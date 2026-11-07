package com.sheepfromheaven.minenow.jobs.miner;

import com.sheepfromheaven.minenow.dominion.DominionSavedData;
import com.sheepfromheaven.minenow.structure.DominionStructure;
import com.sheepfromheaven.minenow.inhabitant.InhabitantEntity;
import com.sheepfromheaven.minenow.jobs.ContainerActionPacer;
import com.sheepfromheaven.minenow.jobs.Job;
import com.sheepfromheaven.minenow.jobs.JobActions;
import com.sheepfromheaven.minenow.structure.workarea.CylinderArea;
import com.sheepfromheaven.minenow.structure.workarea.WorkArea;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Walks to a mining claim - the nearest placed {@code MineClaimMarkerBlock} assigned to this Mine,
 * or the structure's own work area (marker + radius) if none have been placed - carves a spiral
 * stairway down to {@link #MAX_DEPTH} to reach a "hub", digs a short straight junction corridor
 * {@link #JUNCTION_LENGTH} blocks further out from there (see {@link #tickDigJunction}), then from the
 * far end of that tunnels outward in three directions - forward, left, and right, deliberately never
 * back toward the stairway (see {@link #BRANCH_DIRECTIONS}) - strip-mining short perpendicular ribs off
 * each of those branches at regular intervals (see {@link #tickBranchTunnel}). Collects ore drops
 * (recognized via {@code minenow:ores}) as well as the plain spoil (stone, dirt, ...) until the claim's
 * radius is exhausted or the inventory is full, then walks back and deposits everything at the Mine's
 * storage container. A branch/rib that opens into a cave or ravine gets bridged with carried spoil
 * rather than sending the inhabitant into open air (see {@link #ensureFloor}), and torches (fetched
 * from storage into the off-hand slot, alongside the pickaxe in the main hand) get placed at intervals
 * to keep hostile mobs from spawning in the dark. Mirrors {@link
 * com.sheepfromheaven.minenow.jobs.lumberjack.LumberjackJob}'s phase/resume/deposit conventions.
 */
public class MinerJob implements Job {
    private static final double WALK_SPEED = 0.6;
    /** How far below the claim marker/work-area's Y the stairway descends before branching out from the hub. */
    public static final int MAX_DEPTH = 5;
    /** Ticks spent "mining" a single block, so breaking reads as an animated action rather than instant. */
    private static final int NO_CLAIM_RETRY_TICKS = 100;
    /** How long to wait between attempts to fetch a suitable pickaxe once digging is stuck on a block. */
    private static final int BLOCKED_TOOL_RETRY_TICKS = 100;
    /**
     * How far a single mine claim reaches from its marker, in XZ blocks (the tunnel's exhaustion
     * radius). Needs enough room for the junction corridor plus at least one strip-mining rib past it -
     * too tight a radius means a branch runs out before {@link #STRIP_INTERVAL} spine steps ever
     * accumulate, and the ribs (the whole point of strip-mining) never trigger at all.
     */
    public static final int CLAIM_RADIUS = 10;
    /** Placed every this-many tunnel steps when the support-beam toggle is on. */
    private static final int SUPPORT_SPACING = 4;
    /** Spine steps between each pair of perpendicular strip-mining ribs off a branch tunnel. */
    private static final int STRIP_INTERVAL = 4;
    /** How far each strip-mining rib extends to either side of a branch's spine. */
    private static final int RIB_LENGTH = 3;
    /** Spine steps between torches placed to light the tunnel and keep mobs from spawning in it. */
    private static final int TORCH_INTERVAL = 6;
    /**
     * Vanilla no longer ships a single generic "ores" tag (each ore has its own e.g. iron_ores) - this
     * mod-defined tag (data/minenow/tags/block/ores.json) unions all of them, matching the issue's
     * mention of checking against "minecraft:ores" one tag lookup rather than a long enumerated list.
     */
    private static final TagKey<Block> ORES_TAG = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("minenow", "ores"));

    private enum Phase { GOTO_CLAIM, FIND_PICKAXE, FIND_TORCHES, DIG_STAIRWAY, DIG_JUNCTION, BRANCH_TUNNEL, RESUME_DIGGING, RETURN_STAIRWAY, DEPOSIT, NO_CLAIM, BLOCKED_TOOL }

    /** Where a {@link Phase#BRANCH_TUNNEL} step is relative to its branch's spine; see {@link #currentStepDirection}. */
    private enum RibState { NONE, RIGHT_OUT, RIGHT_BACK, LEFT_OUT, LEFT_BACK }

    private static final Direction[] SPIRAL_DIRECTIONS = {
            Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST
    };

    /**
     * The direction of the stairway's very last step - i.e. the direction the inhabitant is already
     * facing/moving in once it reaches the hub, continuing straight on rather than turning back toward
     * the entrance. Used as the junction corridor's direction and the "forward" reference for the three
     * branch directions below.
     */
    private static final Direction STAIRWAY_APPROACH_DIRECTION = SPIRAL_DIRECTIONS[(MAX_DEPTH - 1) % SPIRAL_DIRECTIONS.length];

    /**
     * The three branch tunnels dug outward from the far end of the junction corridor - forward
     * (continuing straight on from the stairway), left, and right. Deliberately excludes the direction
     * back toward the stairway/entrance: a branch (and its perpendicular strip-mining ribs) dug that way
     * would otherwise tunnel back through the immediate vicinity of the only way out.
     */
    private static final Direction[] BRANCH_DIRECTIONS = {
            STAIRWAY_APPROACH_DIRECTION,
            STAIRWAY_APPROACH_DIRECTION.getCounterClockWise(),
            STAIRWAY_APPROACH_DIRECTION.getClockWise()
    };
    /** How far the junction corridor extends straight out from the hub before branching starts. */
    private static final int JUNCTION_LENGTH = 3;

    private Phase phase = Phase.GOTO_CLAIM;
    /** Phase to return to once {@link #BLOCKED_TOOL} is resolved - {@code DIG_STAIRWAY}, {@code DIG_JUNCTION}, or {@code BRANCH_TUNNEL}. */
    private Phase resumePhase = Phase.DIG_STAIRWAY;
    /** Current floor position the inhabitant is standing/digging from. */
    @Nullable
    private BlockPos cursor;
    /** The area currently being worked - a claim cylinder, or the structure's own work area as fallback. */
    @Nullable
    private WorkArea workArea;
    /** Where the stairway bottoms out and the junction corridor starts; set once, when the hub is reached. */
    @Nullable
    private BlockPos hubPos;
    /** Progress through the junction corridor dug from the hub before branching starts; see {@link #tickDigJunction}. */
    private int junctionStep;
    /** Which of {@link #BRANCH_DIRECTIONS} is currently being dug. */
    private int branchIndex;
    /**
     * Progress through {@link #tickResumeDigging}'s replay: -1 = still walking to the claim center,
     * 0..{@link #MAX_DEPTH}-1 = which spiral stairway step was just reached, {@link #MAX_DEPTH} = done
     * with the stairway, now replaying the junction corridor, then {@link #branchPath}, to reach
     * {@link #cursor}.
     */
    private int resumeStairwayStep = -1;
    /** How far into the junction corridor {@link #tickResumeDigging} has replayed so far. */
    private int resumeJunctionStep;
    /** How far into {@link #branchPath} {@link #tickResumeDigging} has replayed so far. */
    private int resumeBranchStep;
    /** Ticks left on the current replay hop; see {@link #walkHop}. */
    private int resumeHopTicks;
    /**
     * Every position stepped onto since the current branch's last successful resume (or since the hub,
     * if it hasn't needed one yet) - recorded so {@link #tickResumeDigging} can retrace the route back
     * one short hop at a time. Deliberately trimmed to empty every time a resume completes (not just
     * when a new branch starts) - replaying the *entire* branch on every single deposit trip would make
     * each trip's retrace cost grow without bound as the branch gets longer; only ever replaying what's
     * been dug since the last trip keeps it bounded to a handful of steps.
     */
    private final List<BlockPos> branchPath = new ArrayList<>();
    /** Spine steps taken along the current branch (excluding rib excursions); resets every new branch. */
    private int stepInBranch;
    private RibState ribState = RibState.NONE;
    private int ribStepsRemaining;
    private int depthDug;
    private int tunnelSteps;
    private int stepsSinceTorch;
    /** Set once storage has been searched for torches and come up empty; cleared once some are found. */
    private boolean outOfTorches;
    private int waitTicks;
    private int noClaimCooldown;
    /** The block currently gating progress while {@link #phase} is {@link Phase#BLOCKED_TOOL}. */
    @Nullable
    private BlockPos blockedPos;
    /** Set while {@link #phase} is {@link Phase#BLOCKED_TOOL} and a better tool would actually help; null for an unbreakable block (bedrock). */
    @Nullable
    private Item missingItem;
    private final ContainerActionPacer actionPacer = new ContainerActionPacer();
    private final JobActions.Nav nav = new JobActions.Nav();
    private final JobActions.ContainerTarget containerTarget = new JobActions.ContainerTarget();

    @Override
    public boolean canStart(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        return true;
    }

    @Override
    public void start(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        // Resume wherever the last cycle left off; state persists across interruptions.
    }

    @Override
    public void stop(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        inhabitant.getNavigation().stop();
    }

    @Override
    public void onInterrupted(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        // If a hub/branch is already established, head straight back to the exact spot digging left off
        // (see tickResumeDigging) rather than re-approaching the claim from scratch and re-processing
        // every already-open cell of the tunnel as if digging it all over again. Only genuinely restart
        // (GOTO_CLAIM, which recomputes the claim center and re-digs the stairway) when there's no
        // established dig site yet, this claim's branches are already all exhausted, or the interruption
        // landed mid-stairway/mid-junction: tickResumeDigging's replay always walks the *full* stairway
        // and junction (they're short and deterministic either way), so resuming mid-way through either
        // would walk the inhabitant past blocks that were never actually mined yet.
        var effectivePhase = phase == Phase.BLOCKED_TOOL ? resumePhase : phase;
        boolean midMandatorySection = effectivePhase == Phase.DIG_STAIRWAY || effectivePhase == Phase.DIG_JUNCTION;
        phase = !midMandatorySection && canResumeDigging() ? Phase.RESUME_DIGGING : Phase.GOTO_CLAIM;
        containerTarget.pos = null;
    }

    /** Whether there's an established hub/branch to head back to, rather than needing a fresh start. */
    private boolean canResumeDigging() {
        return hubPos != null && cursor != null && branchIndex < BRANCH_DIRECTIONS.length;
    }

    @Override
    public boolean tick(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        return switch (phase) {
            case GOTO_CLAIM -> tickGotoClaim(inhabitant, level, workplace);
            case FIND_PICKAXE -> tickFindPickaxe(inhabitant, level, workplace);
            case FIND_TORCHES -> tickFindTorches(inhabitant, level, workplace);
            case DIG_STAIRWAY -> tickDigStairway(inhabitant, level, workplace);
            case DIG_JUNCTION -> tickDigJunction(inhabitant, level, workplace);
            case BRANCH_TUNNEL -> tickBranchTunnel(inhabitant, level, workplace);
            case RESUME_DIGGING -> tickResumeDigging(inhabitant, level);
            case RETURN_STAIRWAY -> tickReturnStairway(inhabitant, level, workplace);
            case DEPOSIT -> tickDeposit(inhabitant, level, workplace);
            case NO_CLAIM -> tickNoClaim(inhabitant);
            case BLOCKED_TOOL -> tickBlockedTool(inhabitant, level, workplace);
        };
    }

    /**
     * Walks straight back to the exact spot {@link #tickBranchTunnel} left off at, via ordinary
     * pathfinding through the tunnel already carved open, instead of restarting the whole GOTO_CLAIM ->
     * stairway -> hub sequence and re-processing every already-dug cell one at a time along the way.
     */
    /** How many ticks {@link #walkHop} spends on each replay hop in {@link #tickResumeDigging}. */
    private static final int WALK_HOP_TICKS = 6;

    /**
     * Retraces the exact route back to {@link #cursor}: the stairway's spiral descent (deterministic -
     * recomputed via {@link #stairwayPositionAtStep}), then {@link #branchPath} (recorded while actually
     * digging the branch, trimmed to only what's happened since the last resume). Each leg advances one
     * short hop at a time on a fixed tick timer (see {@link #walkHop}) rather than via ordinary
     * long-distance pathfinding, which can't reliably get the inhabitant there at all: for the stairway,
     * {@code Navigation#createPath} verifiably returns null for a target several steps down it, since
     * the pathfinder refuses to plan while airborne, which the inhabitant always is for a moment on
     * every one of its drops; more generally, this job never gates real progress on navigation actually
     * reporting arrival anywhere else either (mining timing is driven by tick counters, not distance
     * checks - see {@link #tickBranchTunnel}), so this doesn't either.
     */
    private boolean tickResumeDigging(InhabitantEntity inhabitant, ServerLevel level) {
        if (cursor == null || workArea == null) {
            // Shouldn't happen (only entered when canResumeDigging() held), but don't get stuck with
            // nowhere to walk to - fall back to a full restart instead.
            phase = Phase.GOTO_CLAIM;
            return true;
        }
        if (resumeStairwayStep < 0) {
            if (JobActions.walkTo(inhabitant, workArea.center(), nav)) {
                resumeStairwayStep = 0;
            }
            return true;
        }
        if (resumeStairwayStep < MAX_DEPTH) {
            var waypoint = stairwayPositionAtStep(workArea.center(), resumeStairwayStep + 1);
            if (walkHop(inhabitant, waypoint)) {
                resumeStairwayStep++;
            }
            return true;
        }
        if (resumeJunctionStep < JUNCTION_LENGTH) {
            // Deterministic and always fully dug by the time BRANCH_TUNNEL (and so a resume) is
            // possible - see onInterrupted - so this can always replay the junction's full length,
            // exactly like the stairway above.
            var waypoint = hubPos.relative(STAIRWAY_APPROACH_DIRECTION, resumeJunctionStep + 1);
            if (walkHop(inhabitant, waypoint)) {
                resumeJunctionStep++;
            }
            return true;
        }
        if (resumeBranchStep < branchPath.size()) {
            if (walkHop(inhabitant, branchPath.get(resumeBranchStep))) {
                resumeBranchStep++;
            }
            return true;
        }
        // Fully retraced - resume real digging exactly where it left off. Trim the replayed history
        // down to empty rather than just resetting the index: only what's dug from here on needs
        // replaying on the *next* trip, not this same stretch all over again.
        phase = Phase.BRANCH_TUNNEL;
        resumeStairwayStep = -1;
        resumeJunctionStep = 0;
        resumeBranchStep = 0;
        branchPath.clear();
        return true;
    }

    /**
     * Steers toward {@code target} for a fixed {@link #WALK_HOP_TICKS}, then reports done - a tick-based
     * timer rather than a distance check, both because it sidesteps the airborne/pathfinding problems
     * {@link #tickResumeDigging} hits, and because it keeps replay progress deterministic (driven purely
     * by {@code tick()} calls) rather than depending on real per-tick physics actually moving the
     * inhabitant, matching how every other phase in this job already paces itself by tick count rather
     * than by confirming arrival.
     */
    private boolean walkHop(InhabitantEntity inhabitant, BlockPos target) {
        if (resumeHopTicks <= 0) {
            resumeHopTicks = WALK_HOP_TICKS;
        }
        inhabitant.getMoveControl().setWantedPosition(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, WALK_SPEED);
        inhabitant.getLookControl().setLookAt(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        return --resumeHopTicks <= 0;
    }

    /** Where the spiral stairway (see {@link #tickDigStairway}) is after {@code stepsCompleted} steps from {@code start}. */
    private static BlockPos stairwayPositionAtStep(BlockPos start, int stepsCompleted) {
        var pos = start;
        for (int i = 0; i < stepsCompleted; i++) {
            pos = pos.below().relative(SPIRAL_DIRECTIONS[i % SPIRAL_DIRECTIONS.length]);
        }
        return pos;
    }

    private boolean tickGotoClaim(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        if (workArea == null) {
            workArea = pickClaimArea(inhabitant, level, workplace);
            if (workArea == null) {
                // No mine claim placed in this Mine's territory - nothing to work (the claim marker is a
                // level-1 work condition), so park and retry rather than mining around the structure.
                phase = Phase.NO_CLAIM;
                noClaimCooldown = NO_CLAIM_RETRY_TICKS;
                return true;
            }
        }
        if (JobActions.walkTo(inhabitant, workArea.center(), nav)) {
            cursor = workArea.center();
            depthDug = 0;
            phase = Phase.FIND_PICKAXE;
        }
        return true;
    }

    /** The nearest mine claim assigned to this Mine as a {@link #CLAIM_RADIUS} cylinder, or null if none placed. */
    @Nullable
    private WorkArea pickClaimArea(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        var dominion = DominionSavedData.get(level).getDominionById(inhabitant.getDominionId());
        var claims = dominion != null ? dominion.getMineClaimsAssignedTo(workplace.getId()) : List.<com.sheepfromheaven.minenow.structure.mine.MineClaim>of();
        if (claims.isEmpty()) {
            return null;
        }
        var from = inhabitant.blockPosition();
        var best = claims.get(0).getMarkerPos();
        double bestDistSq = best.distSqr(from);
        for (var claim : claims) {
            double distSq = claim.getMarkerPos().distSqr(from);
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = claim.getMarkerPos();
            }
        }
        return new CylinderArea(best, CLAIM_RADIUS, CLAIM_RADIUS);
    }

    /**
     * Mirrors {@link com.sheepfromheaven.minenow.jobs.lumberjack.LumberjackJob}'s FIND_AXE phase: tries
     * to equip a pickaxe from the Mine's storage before digging. Unlike the lumberjack this never parks
     * waiting here - a miner with no pickaxe available yet still walks down to start digging, since
     * dirt/gravel/sand ahead of the stone don't need one. Once it actually reaches a tool-gated block
     * (stone, ore, ...) {@link #tickDigStairway}/{@link #tickBranchTunnel} catch that via
     * {@link #findEssentialBlocker} and park in {@link Phase#BLOCKED_TOOL} instead.
     */
    private boolean tickFindPickaxe(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        if (hasPickaxe(inhabitant)) {
            phase = Phase.FIND_TORCHES;
            return true;
        }
        boolean wasSearching = containerTarget.pos == null;
        var pickaxe = JobActions.takeFromContainer(inhabitant, level, workplace, actionPacer, nav, containerTarget,
                stack -> stack.is(ItemTags.PICKAXES), true);
        if (!pickaxe.isEmpty()) {
            inhabitant.setItemSlot(EquipmentSlot.MAINHAND, pickaxe);
            phase = Phase.FIND_TORCHES;
            return true;
        }
        if (wasSearching && containerTarget.pos == null) {
            // No pickaxe anywhere - proceed bare-handed rather than stalling forever; ore that
            // requires a tool simply gets skipped (see breakAndCollect) until one becomes available.
            phase = Phase.FIND_TORCHES;
        }
        return true;
    }

    private boolean hasPickaxe(InhabitantEntity inhabitant) {
        return inhabitant.getItemBySlot(EquipmentSlot.MAINHAND).is(ItemTags.PICKAXES);
    }

    /**
     * Tries once to stock up on torches from the Mine's storage into the off-hand slot before heading
     * down - mirrors {@link #tickFindPickaxe} in never blocking on it: a miner with no torches available
     * still goes to work, it just won't light its own tunnel (see {@link #maybePlaceTorch}) until some
     * show up in storage on a future trip.
     */
    private boolean tickFindTorches(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        if (hasTorches(inhabitant)) {
            outOfTorches = false;
            phase = Phase.DIG_STAIRWAY;
            waitTicks = 0;
            return true;
        }
        boolean wasSearching = containerTarget.pos == null;
        var torches = JobActions.takeFromContainer(inhabitant, level, workplace, actionPacer, nav, containerTarget,
                stack -> stack.is(Items.TORCH), true);
        if (!torches.isEmpty()) {
            inhabitant.setItemSlot(EquipmentSlot.OFFHAND, torches);
            outOfTorches = false;
            phase = Phase.DIG_STAIRWAY;
            waitTicks = 0;
            return true;
        }
        if (wasSearching && containerTarget.pos == null) {
            // No torches anywhere in storage - proceed without lighting the tunnel this trip.
            outOfTorches = true;
            phase = Phase.DIG_STAIRWAY;
            waitTicks = 0;
        }
        return true;
    }

    private boolean hasTorches(InhabitantEntity inhabitant) {
        return inhabitant.getItemBySlot(EquipmentSlot.OFFHAND).is(Items.TORCH);
    }

    private boolean tickDigStairway(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        if (depthDug >= MAX_DEPTH) {
            hubPos = cursor;
            junctionStep = 0;
            branchIndex = 0;
            stepInBranch = 0;
            ribState = RibState.NONE;
            ribStepsRemaining = 0;
            branchPath.clear();
            tunnelSteps = 0;
            // stepsSinceTorch deliberately survives across trips (unlike the other branch-progress
            // fields, which restart each time the hub is re-reached after a deposit) - the carry
            // inventory is only 2 slots, so a single trip often fills up and heads back before a full
            // TORCH_INTERVAL of spine steps ever accumulates; resetting this here would mean torches
            // never get placed at all under realistic conditions.
            phase = Phase.DIG_JUNCTION;
            return true;
        }
        // Cycles through all four cardinal directions (a full turn every 4 steps nets zero horizontal
        // displacement) so the stairway spirals straight down within a tiny horizontal footprint,
        // instead of marching monotonically off in one direction and burning through the claim's
        // radius budget before tunneling even starts.
        var dir = SPIRAL_DIRECTIONS[depthDug % SPIRAL_DIRECTIONS.length];
        // A step down needs three distinct cells cleared, not two: the new foot cell, the new head cell
        // directly above it (which is also the "doorway" at the old floor level, since dropping one Y
        // means new-head == old-cursor-forward), AND the cell above THAT - the old head level, forward.
        // Without that third cell the passage is only a single block tall at the point where it steps
        // down (the old ceiling is still hanging over the doorway), so a two-block-tall mob can walk up
        // to the edge but can't actually descend through it.
        var next = cursor.below().relative(dir);
        var newHead = next.above();
        var doorwayTop = newHead.above();
        var blocker = findEssentialBlocker(level, inhabitant, next, newHead, doorwayTop);
        if (blocker != null) {
            return enterBlockedTool(Phase.DIG_STAIRWAY, blocker, level);
        }
        // newHead (the riser, straight ahead at the old floor level) is the primary target - it's what
        // the inhabitant is already looking at and it's the one visibly cracking as it's mined; next and
        // doorwayTop are cleared alongside it once it comes down (see breakAndCollect and
        // JobActions#mineBlockRealistically for why an already-open primary still fires this callback).
        var tool = inhabitant.getItemBySlot(EquipmentSlot.MAINHAND);
        if (waitTicks <= 0) {
            waitTicks = JobActions.breakTicksFor(level, newHead, level.getBlockState(newHead), tool);
        }
        var swingProgress = new JobActions.SwingProgress();
        swingProgress.remainingTicks = waitTicks;
        boolean done = JobActions.mineBlockRealistically(inhabitant, level, newHead, tool, swingProgress, () -> {
            breakAndCollect(inhabitant, level, next);
            breakAndCollect(inhabitant, level, doorwayTop);
            cursor = next;
            depthDug++;
            maybePlaceSupport(level, workplace, cursor, dir);
            inhabitant.getNavigation().moveTo(cursor.getX() + 0.5, cursor.getY(), cursor.getZ() + 0.5, WALK_SPEED);
        });
        waitTicks = done ? 0 : swingProgress.remainingTicks;
        return true;
    }

    /**
     * Digs a short straight corridor {@link #JUNCTION_LENGTH} blocks out from the hub, in the same
     * direction as the stairway's last step, before any branching starts - so the branches (and their
     * perpendicular strip-mining ribs) begin far enough from the stairway's foot that none of them ever
     * needs to consider tunneling back toward it in the first place (see {@link #BRANCH_DIRECTIONS},
     * which excludes that direction entirely).
     */
    private boolean tickDigJunction(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        if (junctionStep >= JUNCTION_LENGTH) {
            phase = Phase.BRANCH_TUNNEL;
            return true;
        }
        var front = cursor.relative(STAIRWAY_APPROACH_DIRECTION);
        var blocker = findEssentialBlocker(level, inhabitant, front, front.above());
        if (blocker != null) {
            return enterBlockedTool(Phase.DIG_JUNCTION, blocker, level);
        }
        if (!ensureFloor(inhabitant, level, front)) {
            // No spoil to bridge a ravine with yet - unlike a branch/rib there's nothing to abandon this
            // stretch to (branching depends on finishing it), so just wait and retry; the stairway just
            // dug produces spoil almost immediately in practice.
            return true;
        }
        var tool = inhabitant.getItemBySlot(EquipmentSlot.MAINHAND);
        if (waitTicks <= 0) {
            waitTicks = JobActions.breakTicksFor(level, front, level.getBlockState(front), tool);
        }
        var swingProgress = new JobActions.SwingProgress();
        swingProgress.remainingTicks = waitTicks;
        boolean done = JobActions.mineBlockRealistically(inhabitant, level, front, tool, swingProgress, () -> {
            breakAndCollect(inhabitant, level, front.above());
            breakOreNeighbors(inhabitant, level, front);
            breakOreNeighbors(inhabitant, level, front.above());
            cursor = front;
            junctionStep++;
            maybePlaceSupport(level, workplace, cursor, STAIRWAY_APPROACH_DIRECTION);
            inhabitant.getNavigation().moveTo(cursor.getX() + 0.5, cursor.getY(), cursor.getZ() + 0.5, WALK_SPEED);
        });
        waitTicks = done ? 0 : swingProgress.remainingTicks;
        return true;
    }

    /**
     * Digs outward from the far end of the junction corridor along {@link #BRANCH_DIRECTIONS}, one
     * branch at a time. Each branch is a spine tunnel with perpendicular strip-mining ribs dug off it
     * every {@link #STRIP_INTERVAL} steps (right rib out-and-back, then left rib out-and-back - see
     * {@link #currentStepDirection}/{@link #advanceRibOrSpine}), so a single generic "advance one step
     * in whatever direction that is" loop drives both the spine and its ribs. A branch ends (moving on
     * to the next direction, or to {@link Phase#RETURN_STAIRWAY} once all three are done) when its
     * spine walks outside the claim's radius, same as the old single-direction tunnel did.
     */
    private boolean tickBranchTunnel(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        if (isInventoryFull(inhabitant)) {
            phase = Phase.RETURN_STAIRWAY;
            return true;
        }
        if (ribState == RibState.NONE && tunnelExhausted()) {
            if (!advanceToNextBranch()) {
                phase = Phase.RETURN_STAIRWAY;
            }
            return true;
        }
        var dir = currentStepDirection();
        var front = cursor.relative(dir);
        // Both the floor-level and head-level blocks ahead must actually come down before advancing -
        // otherwise the tunnel just stalls in a solid wall while the game logic thinks it moved forward.
        var blocker = findEssentialBlocker(level, inhabitant, front, front.above());
        if (blocker != null) {
            if (level.getBlockState(blocker).getDestroySpeed(level, blocker) < 0) {
                // Truly unbreakable (bedrock, ...) - no tool, however good, would ever clear this, so
                // parking in BLOCKED_TOOL would wait forever. Abandon this excursion instead, exactly
                // like the ravine case below.
                abandonExcursion();
                return true;
            }
            return enterBlockedTool(Phase.BRANCH_TUNNEL, blocker, level);
        }
        if (!ensureFloor(inhabitant, level, front)) {
            // The way ahead opens into a cave/ravine with nothing to stand on, and there's no spoil left
            // to bridge it with yet - stop this excursion here rather than sending the inhabitant off
            // the edge into open space; a future trip (carrying spoil from earlier steps) can pick up
            // wherever the claim still has rock left once inventory space allows it.
            abandonExcursion();
            return true;
        }
        var tool = inhabitant.getItemBySlot(EquipmentSlot.MAINHAND);
        if (waitTicks <= 0) {
            waitTicks = JobActions.breakTicksFor(level, front, level.getBlockState(front), tool);
        }
        var swingProgress = new JobActions.SwingProgress();
        swingProgress.remainingTicks = waitTicks;
        boolean done = JobActions.mineBlockRealistically(inhabitant, level, front, tool, swingProgress, () -> {
            breakAndCollect(inhabitant, level, front.above());
            breakOreNeighbors(inhabitant, level, front);
            breakOreNeighbors(inhabitant, level, front.above());
            cursor = front;
            branchPath.add(front);
            tunnelSteps++;
            advanceRibOrSpine();
            maybePlaceTorch(inhabitant, level);
            maybePlaceSupport(level, workplace, cursor, dir);
            inhabitant.getNavigation().moveTo(cursor.getX() + 0.5, cursor.getY(), cursor.getZ() + 0.5, WALK_SPEED);
        });
        waitTicks = done ? 0 : swingProgress.remainingTicks;
        return true;
    }

    /** The direction the very next {@link #tickBranchTunnel} step should move in, given the current rib state. */
    private Direction currentStepDirection() {
        var mainDir = BRANCH_DIRECTIONS[branchIndex];
        return switch (ribState) {
            case NONE -> mainDir;
            case RIGHT_OUT -> mainDir.getClockWise();
            case RIGHT_BACK -> mainDir.getCounterClockWise();
            case LEFT_OUT -> mainDir.getCounterClockWise();
            case LEFT_BACK -> mainDir.getClockWise();
        };
    }

    /**
     * Called after a step in {@link #currentStepDirection}'s direction actually lands, to decide what
     * the next one should be: continuing the spine, starting a rib, walking further out along one, or
     * retracing back to the spine after one. A rib pair (right out-and-back, then left out-and-back) is
     * kicked off every {@link #STRIP_INTERVAL} spine steps.
     */
    private void advanceRibOrSpine() {
        switch (ribState) {
            case NONE -> {
                stepInBranch++;
                stepsSinceTorch++;
                if (stepInBranch % STRIP_INTERVAL == 0) {
                    ribState = RibState.RIGHT_OUT;
                    ribStepsRemaining = RIB_LENGTH;
                }
            }
            case RIGHT_OUT -> {
                if (--ribStepsRemaining <= 0) {
                    ribState = RibState.RIGHT_BACK;
                    ribStepsRemaining = RIB_LENGTH;
                }
            }
            case RIGHT_BACK -> {
                if (--ribStepsRemaining <= 0) {
                    ribState = RibState.LEFT_OUT;
                    ribStepsRemaining = RIB_LENGTH;
                }
            }
            case LEFT_OUT -> {
                if (--ribStepsRemaining <= 0) {
                    ribState = RibState.LEFT_BACK;
                    ribStepsRemaining = RIB_LENGTH;
                }
            }
            case LEFT_BACK -> {
                if (--ribStepsRemaining <= 0) {
                    ribState = RibState.NONE;
                }
            }
        }
    }

    /**
     * Bails out of whatever {@link #tickBranchTunnel} can't push through right now (an unbreakable
     * block, or a ravine with no spoil left to bridge it) - ends the current rib early and returns to
     * the spine, or if not currently on a rib, ends the whole branch and moves on to the next cardinal
     * direction (or heads back to deposit if all four are already done).
     */
    private void abandonExcursion() {
        if (ribState != RibState.NONE) {
            ribState = RibState.NONE;
            ribStepsRemaining = 0;
        } else if (!advanceToNextBranch()) {
            phase = Phase.RETURN_STAIRWAY;
        }
    }

    /** Moves on to the next cardinal branch from the hub, or reports there isn't one (all four are done). */
    private boolean advanceToNextBranch() {
        branchIndex++;
        if (branchIndex >= BRANCH_DIRECTIONS.length) {
            return false;
        }
        stepInBranch = 0;
        ribState = RibState.NONE;
        ribStepsRemaining = 0;
        branchPath.clear();
        cursor = junctionEndPos();
        return true;
    }

    /** Where the branches actually radiate from - the far end of the junction corridor dug from the hub. */
    private BlockPos junctionEndPos() {
        return hubPos.relative(STAIRWAY_APPROACH_DIRECTION, JUNCTION_LENGTH);
    }

    /**
     * Guards against a branch/rib opening into a cave or ravine with nothing underneath - rather than
     * send the inhabitant walking off the edge into open space, bridges the gap with one block of spoil
     * (stone, cobblestone, dirt, ...) from its own carried inventory. Returns false if there's nothing
     * to bridge with yet, so the caller can end this excursion instead.
     */
    private boolean ensureFloor(InhabitantEntity inhabitant, ServerLevel level, BlockPos front) {
        var below = front.below();
        if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
            return true;
        }
        var inv = inhabitant.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem blockItem) {
                level.setBlockAndUpdate(below, blockItem.getBlock().defaultBlockState());
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }

    /** Places a torch on the floor every {@link #TORCH_INTERVAL} spine steps, if the off-hand is carrying any. */
    private void maybePlaceTorch(InhabitantEntity inhabitant, ServerLevel level) {
        if (stepsSinceTorch < TORCH_INTERVAL) {
            return;
        }
        var offhand = inhabitant.getItemBySlot(EquipmentSlot.OFFHAND);
        if (!offhand.is(Items.TORCH) || !level.getBlockState(cursor).isAir()) {
            return;
        }
        level.setBlockAndUpdate(cursor, Blocks.TORCH.defaultBlockState());
        offhand.shrink(1);
        stepsSinceTorch = 0;
        if (offhand.isEmpty()) {
            outOfTorches = true;
        }
    }

    /**
     * The first of {@code positions} the miner can't actually clear right now - either permanently
     * unbreakable (bedrock, {@code getDestroySpeed < 0}) or gated behind a tool it isn't holding - or
     * null if all of them are already passable. Mirrors the checks {@link #breakAndCollect} applies
     * per-block, but run upfront so the caller can decide whether to advance at all instead of only
     * discovering after the fact that nothing actually broke.
     */
    @Nullable
    private BlockPos findEssentialBlocker(ServerLevel level, InhabitantEntity inhabitant, BlockPos... positions) {
        var tool = inhabitant.getItemBySlot(EquipmentSlot.MAINHAND);
        for (var pos : positions) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            if (state.getDestroySpeed(level, pos) < 0) {
                return pos;
            }
            if (state.requiresCorrectToolForDrops() && !tool.isCorrectToolForDrops(state)) {
                return pos;
            }
        }
        return null;
    }

    /** Parks in {@link Phase#BLOCKED_TOOL} instead of advancing past {@code blocker}; see {@link #tickBlockedTool}. */
    private boolean enterBlockedTool(Phase from, BlockPos blocker, ServerLevel level) {
        resumePhase = from;
        blockedPos = blocker;
        var state = level.getBlockState(blocker);
        // Bedrock etc: no pickaxe, however good, will ever make this breakable, so there's nothing to fetch.
        missingItem = state.getDestroySpeed(level, blocker) < 0 ? null : Items.IRON_PICKAXE;
        phase = Phase.BLOCKED_TOOL;
        waitTicks = BLOCKED_TOOL_RETRY_TICKS;
        return true;
    }

    /**
     * Waits on a block {@link #tickDigStairway}/{@link #tickDigJunction}/{@link #tickBranchTunnel} couldn't clear, periodically trying
     * to fetch a pickaxe from the Mine's storage that's actually correct for that specific block (rather
     * than just any pickaxe, as {@link #tickFindPickaxe} does) before resuming digging.
     */
    private boolean tickBlockedTool(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        if (blockedPos == null || level.getBlockState(blockedPos).isAir()) {
            // Already cleared, e.g. by a player - resume right away.
            resumeFromBlockedTool();
            return true;
        }
        var blockedState = level.getBlockState(blockedPos);
        if (inhabitant.getItemBySlot(EquipmentSlot.MAINHAND).isCorrectToolForDrops(blockedState)) {
            // A player may have handed the inhabitant a suitable pickaxe directly (not via storage) -
            // check the held item every tick rather than only after fetching from a container, so this
            // isn't missed until the next storage-search cooldown.
            resumeFromBlockedTool();
            return true;
        }
        if (missingItem == null) {
            // Unbreakable regardless of tool - nothing to do but keep waiting and re-check periodically.
            if (--waitTicks <= 0) {
                waitTicks = BLOCKED_TOOL_RETRY_TICKS;
            }
            return true;
        }
        if (--waitTicks > 0) {
            return true;
        }
        boolean wasSearching = containerTarget.pos == null;
        var pickaxe = JobActions.takeFromContainer(inhabitant, level, workplace, actionPacer, nav, containerTarget,
                stack -> stack.is(ItemTags.PICKAXES) && stack.isCorrectToolForDrops(blockedState), true);
        if (!pickaxe.isEmpty()) {
            inhabitant.setItemSlot(EquipmentSlot.MAINHAND, pickaxe);
            resumeFromBlockedTool();
            return true;
        }
        if (wasSearching && containerTarget.pos == null) {
            // No suitable pickaxe in storage right now - wait and try again later.
            waitTicks = BLOCKED_TOOL_RETRY_TICKS;
        }
        return true;
    }

    private void resumeFromBlockedTool() {
        phase = resumePhase;
        blockedPos = null;
        missingItem = null;
        // Forces a fresh breakTicksFor computation for whatever's actually being mined next, rather than
        // resuming a swing timed against the leftover BLOCKED_TOOL retry cooldown.
        waitTicks = 0;
    }

    /** True once the tunnel cursor has run outside the worked area (XZ), see {@link CylinderArea#contains}. */
    private boolean tunnelExhausted() {
        return workArea == null || !workArea.contains(cursor);
    }

    private boolean isInventoryFull(InhabitantEntity inhabitant) {
        var inv = inhabitant.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).isEmpty()) return false;
        }
        return true;
    }

    private static final Direction[] SIDE_OFFSETS = {
            Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN
    };

    /** Breaks and collects any {@code minecraft:ores}-tagged block adjacent to {@code pos}, alongside the plain tunnel dig. */
    private void breakOreNeighbors(InhabitantEntity inhabitant, ServerLevel level, BlockPos pos) {
        for (var dir : SIDE_OFFSETS) {
            var neighbor = pos.relative(dir);
            var state = level.getBlockState(neighbor);
            if (state.is(ORES_TAG)) {
                breakAndCollect(inhabitant, level, neighbor);
            }
        }
    }

    /**
     * Breaks {@code pos} using whatever pickaxe the miner actually holds (fetched in
     * {@link #tickFindPickaxe}, mirroring how the lumberjack wields a fetched axe), rather than an
     * implicit best-in-class tool. Any block that vanilla gates behind a tool tier (via
     * {@code minecraft:incorrect_for_*_tool} tags, surfaced on the block state through
     * {@code requiresCorrectToolForDrops}/{@code ItemStack#isCorrectToolForDrops}) - stone, deepslate,
     * ores, obsidian, ... - is left completely untouched, not even broken, when the held tool isn't
     * sufficient, so it stays in place for a future, better-equipped pass instead of an inhabitant
     * carving through solid rock bare-handed. Blocks with negative destroy speed (bedrock) are never
     * breakable at all, regardless of tool.
     */
    private void breakAndCollect(InhabitantEntity inhabitant, ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        if (state.isAir()) {
            return;
        }
        if (state.getDestroySpeed(level, pos) < 0) {
            // Unbreakable in survival (bedrock, etc.) - no tool makes this minable.
            return;
        }
        var tool = inhabitant.getItemBySlot(EquipmentSlot.MAINHAND);
        if (state.requiresCorrectToolForDrops() && !tool.isCorrectToolForDrops(state)) {
            // Not equipped to break this block yet - skip it and leave it in place.
            return;
        }
        var soundType = state.getSoundType();
        level.playSound(null, pos, soundType.getBreakSound(), net.minecraft.sounds.SoundSource.BLOCKS,
                soundType.getVolume(), soundType.getPitch());
        for (var drop : Block.getDrops(state, level, pos, level.getBlockEntity(pos), inhabitant, tool)) {
            inhabitant.getInventory().addItem(drop);
        }
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        if (!tool.isEmpty()) {
            // Same 1-durability-per-block cost a player pays, same as the primary block JobActions#
            // mineBlockRealistically times/animates - this one's just cleared alongside it for free
            // (headroom, ore peeks, ...) rather than getting its own separate swing.
            tool.hurtAndBreak(1, inhabitant, EquipmentSlot.MAINHAND);
        }
    }

    /** Places cosmetic wood support beams every {@link #SUPPORT_SPACING} steps, never touching the walkable path itself. */
    private void maybePlaceSupport(ServerLevel level, DominionStructure workplace, BlockPos floorPos, Direction travelDirection) {
        if (!workplace.isPlaceSupportBeams()) return;
        int step = switch (phase) {
            case BRANCH_TUNNEL -> tunnelSteps;
            case DIG_JUNCTION -> junctionStep;
            default -> depthDug;
        };
        if (step % SUPPORT_SPACING != 0) return;
        // The stairway carves an extra "doorway" cell above head height at each step (see
        // tickDigStairway) so the still-solid rock ceiling sits one block further up there than in the
        // flat tunnel, where only floor+headroom (2 cells) are ever cleared.
        var ceiling = floorPos.above(phase == Phase.DIG_STAIRWAY ? 3 : 2);
        if (!level.getBlockState(ceiling).isAir()) {
            level.setBlockAndUpdate(ceiling, Blocks.OAK_PLANKS.defaultBlockState());
        }
        for (var side : new Direction[]{travelDirection.getClockWise(), travelDirection.getCounterClockWise()}) {
            var wallPos = floorPos.relative(side);
            if (!level.getBlockState(wallPos).isAir()) {
                level.setBlockAndUpdate(wallPos, Blocks.OAK_FENCE.defaultBlockState());
            }
        }
    }

    private boolean tickReturnStairway(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        // The stairway and tunnel are already carved open, so ordinary pathfinding can retrace them.
        if (JobActions.goToJobsite(inhabitant, level, workplace, nav)) {
            phase = Phase.DEPOSIT;
        }
        return true;
    }

    private boolean tickDeposit(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        if (!JobActions.depositCarriedItems(inhabitant, level, workplace, actionPacer, nav, containerTarget)) {
            return true;
        }
        if (canResumeDigging()) {
            // This branch still has more to give (the trip back was only triggered by a full carry
            // inventory, not exhaustion) - head straight back to it instead of restarting the whole
            // GOTO_CLAIM -> stairway -> hub sequence.
            phase = Phase.RESUME_DIGGING;
            return false;
        }
        // All four branches off this hub are exhausted - nothing left to give here, pick a fresh claim
        // area (and dig a fresh hub) next cycle.
        phase = Phase.GOTO_CLAIM;
        workArea = null;
        cursor = null;
        hubPos = null;
        branchIndex = 0;
        return false;
    }

    private boolean tickNoClaim(InhabitantEntity inhabitant) {
        if (--noClaimCooldown <= 0) {
            phase = Phase.GOTO_CLAIM;
        }
        return true;
    }

    @Override
    public String getStatusDescription() {
        return switch (phase) {
            case GOTO_CLAIM -> "Heading to claim";
            case FIND_PICKAXE -> "Getting equipment";
            case FIND_TORCHES -> "Getting torches";
            case DIG_STAIRWAY -> "Digging stairway";
            case DIG_JUNCTION -> "Digging junction";
            case BRANCH_TUNNEL -> "Mining";
            case RESUME_DIGGING -> "Heading back to the dig site";
            case RETURN_STAIRWAY -> "Returning to mine";
            case DEPOSIT -> "Storing ore";
            case NO_CLAIM -> "No claim assigned";
            case BLOCKED_TOOL -> missingItem != null ? "Needs a better pickaxe" : "Tunnel blocked";
        };
    }

    @Override
    @Nullable
    public Item getMissingItem() {
        return missingItem;
    }

    @Override
    @Nullable
    public String getMissingItemLabel() {
        // The exact tier needed varies by block (any pickaxe for stone, iron+ for diamond ore, ...) -
        // pointing at the representative Items.IRON_PICKAXE icon directly would overstate what's needed.
        return missingItem != null ? "a pickaxe strong enough to mine that block" : null;
    }

    @Override
    public List<String> getWarnings() {
        var warnings = new ArrayList<String>();
        if (phase == Phase.NO_CLAIM) {
            warnings.add("I can't find a mining claim");
        }
        if (phase == Phase.BLOCKED_TOOL && missingItem == null) {
            warnings.add("My tunnel is blocked by something unbreakable");
        }
        if (outOfTorches) {
            warnings.add("I don't have any torches to light the tunnel");
        }
        return warnings;
    }

    @Override
    public List<String> getDebugLines() {
        var lines = new ArrayList<String>();
        lines.add("Phase: " + phase);
        lines.add("Cursor: " + cursor);
        lines.add("Work area: " + (workArea != null ? workArea.center() : null)
                + (workArea instanceof CylinderArea c ? ", radius: " + c.radius() : ""));
        lines.add("Depth dug: " + depthDug + " / " + MAX_DEPTH);
        lines.add("Hub: " + hubPos);
        lines.add("Junction step: " + junctionStep + " / " + JUNCTION_LENGTH);
        lines.add("Branch: " + branchIndex + " (" + (branchIndex < BRANCH_DIRECTIONS.length ? BRANCH_DIRECTIONS[branchIndex] : "done") + "), step " + stepInBranch
                + ", rib " + ribState + " (" + ribStepsRemaining + " left)");
        lines.add("Tunnel steps: " + tunnelSteps);
        lines.add("Blocked pos: " + blockedPos + (missingItem != null ? " (needs pickaxe)" : ""));
        lines.add("Out of torches: " + outOfTorches);
        return lines;
    }

    @Override
    public void save(CompoundTag tag) {
        tag.putString("Phase", phase.name());
        tag.putString("ResumePhase", resumePhase.name());
        tag.putInt("WaitTicks", waitTicks);
        tag.putInt("DepthDug", depthDug);
        tag.putInt("TunnelSteps", tunnelSteps);
        tag.putInt("JunctionStep", junctionStep);
        tag.putInt("BranchIndex", branchIndex);
        tag.putInt("ResumeStairwayStep", resumeStairwayStep);
        tag.putInt("ResumeJunctionStep", resumeJunctionStep);
        tag.putInt("ResumeBranchStep", resumeBranchStep);
        tag.putInt("ResumeHopTicks", resumeHopTicks);
        tag.putLongArray("BranchPath", branchPath.stream().mapToLong(BlockPos::asLong).toArray());
        tag.putInt("StepInBranch", stepInBranch);
        tag.putString("RibState", ribState.name());
        tag.putInt("RibStepsRemaining", ribStepsRemaining);
        tag.putInt("StepsSinceTorch", stepsSinceTorch);
        tag.putBoolean("OutOfTorches", outOfTorches);
        actionPacer.save(tag, "Action");
        if (cursor != null) tag.putLong("Cursor", cursor.asLong());
        if (hubPos != null) tag.putLong("HubPos", hubPos.asLong());
        if (workArea instanceof CylinderArea c) {
            tag.putLong("ClaimCenter", c.center().asLong());
            tag.putInt("ClaimRadius", c.radius());
        }
        if (containerTarget.pos != null) tag.putLong("ContainerTarget", containerTarget.pos.asLong());
        if (blockedPos != null) tag.putLong("BlockedPos", blockedPos.asLong());
        if (missingItem != null) tag.putString("MissingItem", BuiltInRegistries.ITEM.getKey(missingItem).toString());
    }

    @Override
    public void load(CompoundTag tag) {
        try {
            phase = Phase.valueOf(tag.getStringOr("Phase", Phase.GOTO_CLAIM.name()));
        } catch (IllegalArgumentException e) {
            phase = Phase.GOTO_CLAIM;
        }
        try {
            resumePhase = Phase.valueOf(tag.getStringOr("ResumePhase", Phase.DIG_STAIRWAY.name()));
        } catch (IllegalArgumentException e) {
            resumePhase = Phase.DIG_STAIRWAY;
        }
        waitTicks = tag.getIntOr("WaitTicks", 0);
        depthDug = tag.getIntOr("DepthDug", 0);
        tunnelSteps = tag.getIntOr("TunnelSteps", 0);
        junctionStep = tag.getIntOr("JunctionStep", 0);
        branchIndex = tag.getIntOr("BranchIndex", 0);
        resumeStairwayStep = tag.getIntOr("ResumeStairwayStep", -1);
        resumeJunctionStep = tag.getIntOr("ResumeJunctionStep", 0);
        resumeBranchStep = tag.getIntOr("ResumeBranchStep", 0);
        resumeHopTicks = tag.getIntOr("ResumeHopTicks", 0);
        branchPath.clear();
        tag.getLongArray("BranchPath").ifPresent(arr -> {
            for (var packed : arr) {
                branchPath.add(BlockPos.of(packed));
            }
        });
        stepInBranch = tag.getIntOr("StepInBranch", 0);
        try {
            ribState = RibState.valueOf(tag.getStringOr("RibState", RibState.NONE.name()));
        } catch (IllegalArgumentException e) {
            ribState = RibState.NONE;
        }
        ribStepsRemaining = tag.getIntOr("RibStepsRemaining", 0);
        stepsSinceTorch = tag.getIntOr("StepsSinceTorch", 0);
        outOfTorches = tag.getBoolean("OutOfTorches").orElse(false);
        actionPacer.load(tag, "Action");
        cursor = tag.contains("Cursor") ? BlockPos.of(tag.getLongOr("Cursor", 0)) : null;
        hubPos = tag.contains("HubPos") ? BlockPos.of(tag.getLongOr("HubPos", 0)) : null;
        if (tag.contains("ClaimCenter")) {
            var center = BlockPos.of(tag.getLongOr("ClaimCenter", 0));
            int radius = tag.getIntOr("ClaimRadius", 0);
            workArea = new CylinderArea(center, radius, radius);
        } else {
            workArea = null;
        }
        containerTarget.pos = tag.contains("ContainerTarget") ? BlockPos.of(tag.getLongOr("ContainerTarget", 0)) : null;
        blockedPos = tag.contains("BlockedPos") ? BlockPos.of(tag.getLongOr("BlockedPos", 0)) : null;
        missingItem = tag.contains("MissingItem")
                ? BuiltInRegistries.ITEM.getValue(Identifier.parse(tag.getStringOr("MissingItem", "")))
                : null;
    }
}
