package com.sheepfromheaven.minenow.jobs.miner;

import com.sheepfromheaven.minenow.dominion.DominionSavedData;
import com.sheepfromheaven.minenow.structure.DominionStructure;
import com.sheepfromheaven.minenow.inhabitant.InhabitantEntity;
import com.sheepfromheaven.minenow.jobs.ContainerActionPacer;
import com.sheepfromheaven.minenow.jobs.Job;
import com.sheepfromheaven.minenow.jobs.JobActions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Walks to a mining claim - the nearest placed {@code MineClaimMarkerBlock} assigned to this Mine,
 * or the structure's own work area (marker + radius) if none have been placed - carves a stairway down
 * to {@link #MAX_DEPTH}, then tunnels forward breaking every block in its path - collecting ore drops
 * (recognized via {@code minecraft:ores}) as well as the plain spoil (stone, dirt, ...) - until the
 * claim's radius is exhausted or the inventory is full, then walks back and deposits everything at
 * the Mine's storage container. Mirrors {@link com.sheepfromheaven.minenow.jobs.lumberjack.LumberjackJob}'s
 * phase/resume/deposit conventions.
 */
public class MinerJob implements Job {
    private static final double WALK_SPEED = 0.6;
    /** How far below the claim marker/work-area's Y the stairway descends before tunneling begins. */
    public static final int MAX_DEPTH = 12;
    /** Ticks spent "mining" a single block, so breaking reads as an animated action rather than instant. */
    private static final int MINE_TICKS = 10;
    private static final int NO_CLAIM_RETRY_TICKS = 100;
    /** Placed every this-many tunnel steps when the support-beam toggle is on. */
    private static final int SUPPORT_SPACING = 4;
    /**
     * Vanilla no longer ships a single generic "ores" tag (each ore has its own e.g. iron_ores) - this
     * mod-defined tag (data/minenow/tags/block/ores.json) unions all of them, matching the issue's
     * mention of checking against "minecraft:ores" one tag lookup rather than a long enumerated list.
     */
    private static final TagKey<Block> ORES_TAG = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("minenow", "ores"));

    private enum Phase { GOTO_CLAIM, FIND_PICKAXE, DIG_STAIRWAY, TUNNEL, RETURN_STAIRWAY, DEPOSIT, NO_CLAIM }

    private Phase phase = Phase.GOTO_CLAIM;
    /** Current floor position the inhabitant is standing/digging from. */
    @Nullable
    private BlockPos cursor;
    private BlockPos claimCenter;
    private int claimRadius;
    private Direction tunnelDirection = Direction.SOUTH;
    private int depthDug;
    private int tunnelSteps;
    private int waitTicks;
    private int noClaimCooldown;
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
        // Whatever pulled control away may have moved the inhabitant far from the dig site - re-approach
        // the claim rather than resuming mid-tunnel from an arbitrary position.
        phase = Phase.GOTO_CLAIM;
        containerTarget.pos = null;
    }

    @Override
    public boolean tick(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        return switch (phase) {
            case GOTO_CLAIM -> tickGotoClaim(inhabitant, level, workplace);
            case FIND_PICKAXE -> tickFindPickaxe(inhabitant, level, workplace);
            case DIG_STAIRWAY -> tickDigStairway(inhabitant, level, workplace);
            case TUNNEL -> tickTunnel(inhabitant, level, workplace);
            case RETURN_STAIRWAY -> tickReturnStairway(inhabitant, level, workplace);
            case DEPOSIT -> tickDeposit(inhabitant, level, workplace);
            case NO_CLAIM -> tickNoClaim(inhabitant);
        };
    }

    private boolean tickGotoClaim(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        if (claimCenter == null) {
            claimCenter = pickClaimCenter(inhabitant, level, workplace);
            claimRadius = workplace.getWorkAreaRadius();
        }
        if (JobActions.walkTo(inhabitant, claimCenter, nav)) {
            cursor = claimCenter;
            depthDug = 0;
            phase = Phase.FIND_PICKAXE;
        }
        return true;
    }

    /**
     * The nearest mine claim marker assigned to this Mine, if any, else the structure's own
     * marker+radius work area - so a mine with no claims placed behaves exactly as before, while
     * placing claim markers elsewhere extends the mineable area to (also) surround each of them.
     */
    private BlockPos pickClaimCenter(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        var dominion = DominionSavedData.get(level).getDominionById(inhabitant.getDominionId());
        var claims = dominion != null ? dominion.getMineClaimsAssignedTo(workplace.getId()) : List.<com.sheepfromheaven.minenow.structure.mine.MineClaim>of();
        if (claims.isEmpty()) {
            return workplace.getWorkAreaCenter();
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
        return best;
    }

    /**
     * Mirrors {@link com.sheepfromheaven.minenow.jobs.lumberjack.LumberjackJob}'s FIND_AXE phase: tries
     * to equip a pickaxe from the Mine's storage before digging. Unlike the lumberjack, the absence of
     * a pickaxe doesn't block work entirely - a miner with bare hands (or waiting on a pickaxe) can
     * still carve through stone/dirt, it just won't be able to break tool-gated ore (see
     * {@link #breakAndCollect}) until one is found, so this phase never parks in a MISSING_TOOL state.
     */
    private boolean tickFindPickaxe(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        if (hasPickaxe(inhabitant)) {
            phase = Phase.DIG_STAIRWAY;
            waitTicks = MINE_TICKS;
            return true;
        }
        boolean wasSearching = containerTarget.pos == null;
        var pickaxe = JobActions.takeFromContainer(inhabitant, level, workplace, actionPacer, nav, containerTarget,
                stack -> stack.is(ItemTags.PICKAXES), true);
        if (!pickaxe.isEmpty()) {
            inhabitant.setItemSlot(EquipmentSlot.MAINHAND, pickaxe);
            phase = Phase.DIG_STAIRWAY;
            waitTicks = MINE_TICKS;
            return true;
        }
        if (wasSearching && containerTarget.pos == null) {
            // No pickaxe anywhere - proceed bare-handed rather than stalling forever; ore that
            // requires a tool simply gets skipped (see breakAndCollect) until one becomes available.
            phase = Phase.DIG_STAIRWAY;
            waitTicks = MINE_TICKS;
        }
        return true;
    }

    private boolean hasPickaxe(InhabitantEntity inhabitant) {
        return inhabitant.getItemBySlot(EquipmentSlot.MAINHAND).is(ItemTags.PICKAXES);
    }

    private boolean tickDigStairway(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        if (depthDug >= MAX_DEPTH) {
            tunnelSteps = 0;
            phase = Phase.TUNNEL;
            return true;
        }
        // Cycles through all four cardinal directions (a full turn every 4 steps nets zero horizontal
        // displacement) so the stairway spirals straight down within a tiny horizontal footprint,
        // instead of marching monotonically off in one direction and burning through the claim's
        // (deliberately small, max 6) radius budget before tunneling even starts.
        var dir = SPIRAL_DIRECTIONS[depthDug % SPIRAL_DIRECTIONS.length];
        var swingProgress = new JobActions.SwingProgress();
        swingProgress.remainingTicks = waitTicks;
        boolean done = JobActions.tickSwing(inhabitant, cursor.relative(dir), swingProgress, MINE_TICKS, MINE_TICKS, () -> {
            // One stair step: carve forward+down (floor and headroom) so the inhabitant can walk onto it.
            var next = cursor.below().relative(dir);
            breakAndCollect(inhabitant, level, next);
            breakAndCollect(inhabitant, level, next.above());
            breakAndCollect(inhabitant, level, cursor.relative(dir));
            cursor = next;
            depthDug++;
            maybePlaceSupport(level, workplace, cursor, dir);
            inhabitant.getNavigation().moveTo(cursor.getX() + 0.5, cursor.getY(), cursor.getZ() + 0.5, WALK_SPEED);
        });
        waitTicks = done ? MINE_TICKS : swingProgress.remainingTicks;
        return true;
    }

    private boolean tickTunnel(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        if (isInventoryFull(inhabitant) || tunnelExhausted(level)) {
            phase = Phase.RETURN_STAIRWAY;
            return true;
        }
        var front = cursor.relative(tunnelDirection);
        var swingProgress = new JobActions.SwingProgress();
        swingProgress.remainingTicks = waitTicks;
        boolean done = JobActions.tickSwing(inhabitant, front, swingProgress, MINE_TICKS, MINE_TICKS, () -> {
            breakAndCollect(inhabitant, level, front);
            breakAndCollect(inhabitant, level, front.above());
            breakOreNeighbors(inhabitant, level, front);
            breakOreNeighbors(inhabitant, level, front.above());
            cursor = front;
            tunnelSteps++;
            maybePlaceSupport(level, workplace, cursor, tunnelDirection);
            inhabitant.getNavigation().moveTo(cursor.getX() + 0.5, cursor.getY(), cursor.getZ() + 0.5, WALK_SPEED);
        });
        waitTicks = done ? MINE_TICKS : swingProgress.remainingTicks;
        return true;
    }

    /** True once the tunnel has run past the claim's radius from its center, in map (XZ) distance. */
    private boolean tunnelExhausted(ServerLevel level) {
        long dx = cursor.getX() - claimCenter.getX();
        long dz = cursor.getZ() - claimCenter.getZ();
        return dx * dx + dz * dz > (long) claimRadius * claimRadius;
    }

    private boolean isInventoryFull(InhabitantEntity inhabitant) {
        var inv = inhabitant.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).isEmpty()) return false;
        }
        return true;
    }

    private static final Direction[] SPIRAL_DIRECTIONS = {
            Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST
    };

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
     * implicit best-in-class tool. Ore blocks ({@link #ORES_TAG}) that vanilla gates behind a tool
     * tier (via {@code minecraft:incorrect_for_*_tool} tags, surfaced on the block state through
     * {@code requiresCorrectToolForDrops}/{@code ItemStack#isCorrectToolForDrops}) are left completely
     * untouched - not even broken - when the held tool isn't sufficient, so the ore stays in place for
     * a future, better-equipped pass instead of vanishing without a drop.
     * <p>
     * Plain stone/dirt are always broken regardless of tool - matching vanilla, where those blocks are
     * gated on {@code requiresCorrectToolForDrops} too, but only for the *drop*, not for whether the
     * block can be destroyed at all (bare-handed stone mining still clears the block, it just yields no
     * cobblestone). Only {@link #ORES_TAG} blocks get the stronger "don't even break it" treatment.
     */
    private void breakAndCollect(InhabitantEntity inhabitant, ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        if (state.isAir()) {
            return;
        }
        var tool = inhabitant.getItemBySlot(EquipmentSlot.MAINHAND);
        if (state.is(ORES_TAG) && state.requiresCorrectToolForDrops() && !tool.isCorrectToolForDrops(state)) {
            // Not equipped to harvest this ore yet - skip it and leave it in place.
            return;
        }
        var soundType = state.getSoundType();
        level.playSound(null, pos, soundType.getBreakSound(), net.minecraft.sounds.SoundSource.BLOCKS,
                soundType.getVolume(), soundType.getPitch());
        for (var drop : Block.getDrops(state, level, pos, level.getBlockEntity(pos), inhabitant, tool)) {
            inhabitant.getInventory().addItem(drop);
        }
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
    }

    /** Places cosmetic wood support beams every {@link #SUPPORT_SPACING} steps, never touching the walkable path itself. */
    private void maybePlaceSupport(ServerLevel level, DominionStructure workplace, BlockPos floorPos, Direction travelDirection) {
        if (!workplace.isPlaceSupportBeams()) return;
        int step = phase == Phase.TUNNEL ? tunnelSteps : depthDug;
        if (step % SUPPORT_SPACING != 0) return;
        var ceiling = floorPos.above(2);
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
        // Cycle finished - claim may still have more to give, so head back and continue tunneling.
        phase = Phase.GOTO_CLAIM;
        claimCenter = null;
        cursor = null;
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
            case DIG_STAIRWAY -> "Digging stairway";
            case TUNNEL -> "Mining";
            case RETURN_STAIRWAY -> "Returning to mine";
            case DEPOSIT -> "Storing ore";
            case NO_CLAIM -> "No claim assigned";
        };
    }

    @Override
    @Nullable
    public Item getMissingItem() {
        return null;
    }

    @Override
    public List<String> getWarnings() {
        return phase == Phase.NO_CLAIM ? List.of("I can't find a mining claim") : List.of();
    }

    @Override
    public List<String> getDebugLines() {
        var lines = new ArrayList<String>();
        lines.add("Phase: " + phase);
        lines.add("Cursor: " + cursor);
        lines.add("Claim center: " + claimCenter + ", radius: " + claimRadius);
        lines.add("Depth dug: " + depthDug + " / " + MAX_DEPTH);
        lines.add("Tunnel steps: " + tunnelSteps);
        return lines;
    }

    @Override
    public void save(CompoundTag tag) {
        tag.putString("Phase", phase.name());
        tag.putInt("WaitTicks", waitTicks);
        tag.putInt("DepthDug", depthDug);
        tag.putInt("TunnelSteps", tunnelSteps);
        tag.putString("TunnelDirection", tunnelDirection.name());
        actionPacer.save(tag, "Action");
        if (cursor != null) tag.putLong("Cursor", cursor.asLong());
        if (claimCenter != null) {
            tag.putLong("ClaimCenter", claimCenter.asLong());
            tag.putInt("ClaimRadius", claimRadius);
        }
        if (containerTarget.pos != null) tag.putLong("ContainerTarget", containerTarget.pos.asLong());
    }

    @Override
    public void load(CompoundTag tag) {
        try {
            phase = Phase.valueOf(tag.getStringOr("Phase", Phase.GOTO_CLAIM.name()));
        } catch (IllegalArgumentException e) {
            phase = Phase.GOTO_CLAIM;
        }
        waitTicks = tag.getIntOr("WaitTicks", 0);
        depthDug = tag.getIntOr("DepthDug", 0);
        tunnelSteps = tag.getIntOr("TunnelSteps", 0);
        try {
            tunnelDirection = Direction.valueOf(tag.getStringOr("TunnelDirection", Direction.SOUTH.name()));
        } catch (IllegalArgumentException e) {
            tunnelDirection = Direction.SOUTH;
        }
        actionPacer.load(tag, "Action");
        cursor = tag.contains("Cursor") ? BlockPos.of(tag.getLongOr("Cursor", 0)) : null;
        claimCenter = tag.contains("ClaimCenter") ? BlockPos.of(tag.getLongOr("ClaimCenter", 0)) : null;
        claimRadius = tag.getIntOr("ClaimRadius", 0);
        containerTarget.pos = tag.contains("ContainerTarget") ? BlockPos.of(tag.getLongOr("ContainerTarget", 0)) : null;
    }
}
