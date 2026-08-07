package com.sheepfromheaven.minenow.entity.job;

import com.sheepfromheaven.minenow.dominion.DominionStructure;
import com.sheepfromheaven.minenow.entity.InhabitantEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Goes to the lumberyard, equips an axe from a nearby storage container, finds a naturally-grown
 * tree within the structure's range (see {@link TreeScanner}), chops down its trunk, and deposits
 * the logs back at the hut before heading home at night.
 */
public class LumberjackJob implements Job {
    private static final int ARRIVE_DISTANCE_SQ = 4;
    private static final int REPATH_INTERVAL = 60;
    private static final double WALK_SPEED = 0.6;
    /** Total ticks for one chop cycle — must match animation.inhabitant.chop length (0.9s * 20 TPS = 18). */
    private static final int CHOP_TICKS = 18;
    /** Tick within the cycle at which the axe "impacts" — matches the t=0.4s downstroke keyframe (8 ticks). */
    private static final int CHOP_IMPACT_TICK = 8;
    private static final int MISSING_TOOL_RETRY_TICKS = 100;
    private static final int NO_TREE_RETRY_TICKS = 100;
    private static final int AXE_DAMAGE_PER_LOG = 2;
    /** ~8 leaves/second at 20 TPS - fast, but each leaf popping is still individually visible. */
    private static final int LEAF_HARVEST_TICKS = 3;

    private enum Phase { GOTO_HUT, FIND_AXE, GOTO_TREE, CHOPPING, HARVESTING_LEAVES, RETURN_TO_HUT, DEPOSIT, MISSING_TOOL }

    private Phase phase = Phase.GOTO_HUT;
    @Nullable
    private List<BlockPos> trunk;
    /** The felled tree's canopy, harvested one leaf at a time (see {@link #tickHarvestingLeaves}) once the trunk is gone. */
    private List<BlockPos> canopyLeaves = List.of();
    private BlockPos standPos;
    private int waitTicks;
    private int repathCooldown;
    private int noTreeCooldown;
    private final ContainerActionPacer actionPacer = new ContainerActionPacer();
    @Nullable
    private BlockPos containerTarget;
    /** Trunk base positions found to have no reachable standing spot, so they're skipped in future scans. */
    private final Set<BlockPos> unreachableTreeBases = new HashSet<>();
    @Nullable
    private Item missingItem;

    @Override
    public boolean canStart(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        return isDaytime(level) || phase != Phase.GOTO_HUT;
    }

    @Override
    public void start(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        // Resume wherever the last cycle left off; state persists across interruptions.
    }

    @Override
    public void stop(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        inhabitant.getNavigation().stop();
        inhabitant.setChopping(false);
    }

    @Override
    public void onInterrupted(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        inhabitant.setChopping(false);
        if (phase == Phase.HARVESTING_LEAVES) {
            // Harvesting canopy leaves has no positional requirement (tickHarvestingLeaves never moves
            // the inhabitant) - resuming right where it left off is safe and avoids the alternative of
            // routing back through GOTO_HUT -> GOTO_TREE -> CHOPPING, which finds the (already felled,
            // empty) trunk and bails straight to RETURN_TO_HUT, permanently abandoning canopyLeaves
            // without ever harvesting them or collecting their drops.
            return;
        }
        // Whatever pulled control away (going to sleep, eating, ...) may have moved the inhabitant far
        // from wherever this phase left off - re-approach the hut rather than resuming chopping in place.
        phase = Phase.GOTO_HUT;
        containerTarget = null;
    }

    @Override
    public boolean tick(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        return switch (phase) {
            case GOTO_HUT -> tickGotoHut(inhabitant, level, workplace);
            case FIND_AXE -> tickFindAxe(inhabitant, level, workplace);
            case GOTO_TREE -> tickGotoTree(inhabitant, level, workplace);
            case CHOPPING -> tickChopping(inhabitant, level);
            case HARVESTING_LEAVES -> tickHarvestingLeaves(inhabitant, level);
            case RETURN_TO_HUT -> tickReturnToHut(inhabitant, level, workplace);
            case DEPOSIT -> tickDeposit(inhabitant, level, workplace);
            case MISSING_TOOL -> tickMissingTool();
        };
    }

    private boolean tickGotoHut(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        if (moveTowardsAndCheckArrival(inhabitant, workplace.findApproachPos(level))) {
            phase = hasAxe(inhabitant) ? Phase.GOTO_TREE : Phase.FIND_AXE;
        }
        return true;
    }

    private boolean tickFindAxe(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        if (hasAxe(inhabitant)) {
            missingItem = null;
            phase = Phase.GOTO_TREE;
            return true;
        }
        if (containerTarget == null) {
            containerTarget = ContainerUtil.findContainerWith(level, workplace.findStorageContainers(level), stack -> stack.is(ItemTags.AXES));
            if (containerTarget == null) {
                missingItem = Items.WOODEN_AXE;
                phase = Phase.MISSING_TOOL;
                waitTicks = MISSING_TOOL_RETRY_TICKS;
                return true;
            }
        }
        if (!moveTowardsAndCheckArrival(inhabitant, containerTarget)) {
            return true;
        }
        if (!actionPacer.isReady(containerTarget)) {
            return true;
        }
        lookAtBlock(inhabitant, containerTarget);
        if (level.getBlockEntity(containerTarget) instanceof Container container) {
            var axe = ContainerUtil.extractFirst(container, inhabitant, stack -> stack.is(ItemTags.AXES));
            if (!axe.isEmpty()) {
                inhabitant.setItemSlot(EquipmentSlot.MAINHAND, axe);
                missingItem = null;
                actionPacer.recordAction(containerTarget);
                containerTarget = null;
                phase = Phase.GOTO_TREE;
                return true;
            }
        }
        // The axe was gone by the time we arrived (race with another job/player) - rescan next tick.
        containerTarget = null;
        return true;
    }

    private boolean tickGotoTree(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        if (trunk == null) {
            // Returning false here would make WorkGoal stop/restart every tick (replaying the goal-switch
            // sound in a loop) while no tree is in range - park and retry on a cooldown instead.
            if (--noTreeCooldown > 0) {
                return true;
            }
            var found = workplace.findTreeShapeInRange(level, unreachableTreeBases).orElse(null);
            if (found == null) {
                // Nothing reachable right now - drop the exclusion set rather than let it grow stale
                // forever (a sapling can regrow into a tree at a base that was marked unreachable long
                // ago, e.g. because the old tree there had no standable neighbor at the time). Every
                // future rescan will just re-add any base that's still genuinely unreachable.
                unreachableTreeBases.clear();
                noTreeCooldown = NO_TREE_RETRY_TICKS;
                // Voice it every retry (every NO_TREE_RETRY_TICKS) rather than every tick, so the
                // player gets an audible/text nudge that something's wrong without being spammed.
                inhabitant.playGoalSwitchSound();
                return true;
            }
            var base = found.trunk().getFirst();
            var candidate = findStandableNeighbor(level, base);
            if (candidate == null) {
                // No reachable tile around this trunk - skip it on future scans instead of sending
                // moveTo() at a solid log forever, but retry immediately in case another tree in range
                // is reachable.
                unreachableTreeBases.add(base);
                return true;
            }
            trunk = new ArrayList<>(found.trunk());
            canopyLeaves = new ArrayList<>(found.leaves());
            standPos = candidate;
            // The chosen tile (and its headroom) may only have qualified as standable because it's
            // occupied by decayable canopy leaves rather than being genuinely clear - harvest those now
            // so the inhabitant can actually walk there, and drop them from the canopy list so they
            // aren't harvested a second time once the trunk is down.
            harvestLeafIfDecayable(inhabitant, level, candidate);
            harvestLeafIfDecayable(inhabitant, level, candidate.above());
            canopyLeaves.remove(candidate);
            canopyLeaves.remove(candidate.above());
        }
        if (moveTowardsAndCheckArrival(inhabitant, standPos)) {
            inhabitant.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            phase = Phase.CHOPPING;
            waitTicks = CHOP_TICKS;
            inhabitant.setChopping(true);
        }
        return true;
    }

    private static final int[][] NEIGHBOR_OFFSETS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };

    /**
     * Finds a standable tile next to the trunk's base to chop from, rather than standing inside the tree.
     * Checks all 8 surrounding tiles (not just the 4 cardinal ones) since canopy leaves often block one
     * or more sides at head height. A tile blocked only by decayable (non-persistent) leaves still
     * counts as standable - the caller clears those leaves before walking the inhabitant there. Returns
     * null if none of them are reachable even accounting for that - the caller must not send the
     * inhabitant to stand inside the (solid) trunk itself.
     */
    @Nullable
    private BlockPos findStandableNeighbor(ServerLevel level, BlockPos base) {
        for (var offset : NEIGHBOR_OFFSETS) {
            var candidate = base.offset(offset[0], 0, offset[1]);
            if (isStandable(level, candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean isStandable(ServerLevel level, BlockPos pos) {
        if (isBlocking(level, pos)) {
            return false;
        }
        // Leaves commonly overhang the ground right next to a trunk - a tile can look clear at foot
        // level yet still be unreachable because there's no headroom, silently stranding the pathfinder.
        if (isBlocking(level, pos.above())) {
            return false;
        }
        var below = pos.below();
        return !level.getBlockState(below).getCollisionShape(level, below).isEmpty();
    }

    /** A collision-bearing block that isn't decayable canopy leaves - those get cleared, not avoided. */
    private boolean isBlocking(ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        if (state.getCollisionShape(level, pos).isEmpty()) {
            return false;
        }
        return !isDecayableLeaves(state);
    }

    private boolean isDecayableLeaves(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(BlockTags.LEAVES) && state.hasProperty(LeavesBlock.PERSISTENT) && !state.getValue(LeavesBlock.PERSISTENT);
    }

    /**
     * Breaks a decayable leaf block blocking access to the trunk and collects its drops, mirroring
     * {@link #tickHarvestingLeaves} - leaves the inhabitant would otherwise be unable to path through
     * (or a player deliberately left standing, i.e. persistent) are left untouched.
     */
    private void harvestLeafIfDecayable(InhabitantEntity inhabitant, ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        if (!isDecayableLeaves(state)) {
            return;
        }
        var soundType = state.getSoundType();
        level.playSound(null, pos, soundType.getBreakSound(), net.minecraft.sounds.SoundSource.BLOCKS,
                soundType.getVolume(), soundType.getPitch());
        var axe = inhabitant.getItemBySlot(EquipmentSlot.MAINHAND);
        for (var drop : Block.getDrops(state, level, pos, level.getBlockEntity(pos), inhabitant, axe)) {
            inhabitant.getInventory().addItem(drop);
        }
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
    }

    private boolean tickChopping(InhabitantEntity inhabitant, ServerLevel level) {
        if (trunk == null) {
            phase = Phase.RETURN_TO_HUT;
            inhabitant.setChopping(false);
            return true;
        }
        if (trunk.isEmpty()) {
            // The last log's impact (below) empties the trunk mid-animation-cycle, well before the
            // end-of-cycle check further down runs - without this being canopy-aware too, every felled
            // tree would land here on the very next tick and jump straight to RETURN_TO_HUT, silently
            // abandoning canopyLeaves without ever harvesting them.
            phase = canopyLeaves.isEmpty() ? Phase.RETURN_TO_HUT : Phase.HARVESTING_LEAVES;
            waitTicks = LEAF_HARVEST_TICKS;
            inhabitant.setChopping(false);
            return true;
        }
        // Re-assert every tick so the flag survives world reload (isChopping is not persisted).
        inhabitant.setChopping(true);
        var targetPos = trunk.getFirst();

        // Turn the full body toward the target, not just the head.
        double dx = targetPos.getX() + 0.5 - inhabitant.getX();
        double dz = targetPos.getZ() + 0.5 - inhabitant.getZ();
        float targetYaw = (float) (net.minecraft.util.Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
        inhabitant.setYRot(net.minecraft.util.Mth.approachDegrees(inhabitant.getYRot(), targetYaw, 10.0f));
        inhabitant.yBodyRot = inhabitant.getYRot();
        inhabitant.getLookControl().setLookAt(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);

        --waitTicks;

        // At the impact frame (t=0.4s into the animation) break the block and play the sound.
        if (waitTicks == CHOP_TICKS - CHOP_IMPACT_TICK) {
            var pos = trunk.getFirst();
            var state = level.getBlockState(pos);
            if (state.isAir()) {
                // Block already gone (broken by player etc.) — skip it.
                trunk.removeFirst();
            } else {
                var soundType = state.getSoundType();
                level.playSound(null, pos, soundType.getBreakSound(), net.minecraft.sounds.SoundSource.BLOCKS,
                        soundType.getVolume(), soundType.getPitch());
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                trunk.removeFirst();
                inhabitant.getInventory().addItem(new ItemStack(state.getBlock().asItem()));
                inhabitant.swing(InteractionHand.MAIN_HAND);
                var axe = inhabitant.getItemBySlot(EquipmentSlot.MAINHAND);
                if (!axe.isEmpty()) {
                    axe.hurtAndBreak(AXE_DAMAGE_PER_LOG, inhabitant, EquipmentSlot.MAINHAND);
                }
            }
        }

        // End of the full animation cycle — decide what to do next.
        if (waitTicks <= 0) {
            if (trunk.isEmpty()) {
                phase = canopyLeaves.isEmpty() ? Phase.RETURN_TO_HUT : Phase.HARVESTING_LEAVES;
                waitTicks = LEAF_HARVEST_TICKS;
                inhabitant.setChopping(false);
            } else if (inhabitant.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()) {
                phase = Phase.RETURN_TO_HUT;
                inhabitant.setChopping(false);
            } else {
                waitTicks = CHOP_TICKS;
            }
        }
        return true;
    }

    /**
     * Once the trunk is down, harvests the canopy's leaf drops (saplings, apples, ...) one leaf at a
     * time via each leaf's loot table, instead of breaking every block and waiting for vanilla decay -
     * this scopes pickup to exactly the tree just felled instead of turning the lumberjack into a
     * general item magnet that would also vacuum up anything a player happens to drop on the ground
     * nearby. Paced by {@link #LEAF_HARVEST_TICKS} rather than done all at once so the decay reads as a
     * fast but visible sweep through the canopy instead of the whole tree vanishing instantly.
     */
    private boolean tickHarvestingLeaves(InhabitantEntity inhabitant, ServerLevel level) {
        if (canopyLeaves.isEmpty()) {
            phase = Phase.RETURN_TO_HUT;
            return true;
        }
        lookAtBlock(inhabitant, canopyLeaves.getFirst());
        if (--waitTicks > 0) {
            return true;
        }

        var pos = canopyLeaves.removeFirst();
        var state = level.getBlockState(pos);
        if (state.is(BlockTags.LEAVES)) {
            // Still there (not already decayed naturally, or removed by a player) since the scan.
            var soundType = state.getSoundType();
            level.playSound(null, pos, soundType.getBreakSound(), net.minecraft.sounds.SoundSource.BLOCKS,
                    soundType.getVolume(), soundType.getPitch());
            var axe = inhabitant.getItemBySlot(EquipmentSlot.MAINHAND);
            for (var drop : Block.getDrops(state, level, pos, level.getBlockEntity(pos), inhabitant, axe)) {
                inhabitant.getInventory().addItem(drop);
            }
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }

        phase = canopyLeaves.isEmpty() ? Phase.RETURN_TO_HUT : Phase.HARVESTING_LEAVES;
        waitTicks = LEAF_HARVEST_TICKS;
        return true;
    }

    private boolean tickReturnToHut(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        if (moveTowardsAndCheckArrival(inhabitant, workplace.findApproachPos(level))) {
            phase = Phase.DEPOSIT;
        }
        return true;
    }

    private boolean tickDeposit(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        var carried = inhabitant.getInventory();
        for (int i = 0; i < carried.getContainerSize(); i++) {
            var stack = carried.getItem(i);
            if (stack.isEmpty()) continue;
            if (containerTarget == null) {
                containerTarget = ContainerUtil.findContainerAccepting(level, workplace.findStorageContainers(level), stack);
                if (containerTarget == null) {
                    // No room anywhere right now - stay put and retry next tick rather than getting stuck.
                    return true;
                }
            }
            if (!moveTowardsAndCheckArrival(inhabitant, containerTarget)) {
                return true;
            }
            if (!actionPacer.isReady(containerTarget)) {
                return true;
            }
            lookAtBlock(inhabitant, containerTarget);
            if (level.getBlockEntity(containerTarget) instanceof Container container) {
                carried.setItem(i, ContainerUtil.insertItem(container, inhabitant, stack));
            }
            actionPacer.recordAction(containerTarget);
            containerTarget = null;
            return true;
        }
        phase = Phase.GOTO_HUT;
        trunk = null;
        canopyLeaves = List.of();
        standPos = null;
        unreachableTreeBases.clear();
        return false;
    }

    private boolean tickMissingTool() {
        if (--waitTicks <= 0) {
            phase = Phase.FIND_AXE;
        }
        return true;
    }

    private boolean moveTowardsAndCheckArrival(InhabitantEntity inhabitant, BlockPos target) {
        boolean arrived = inhabitant.distanceToSqr(target.getX() + 0.5, target.getY(), target.getZ() + 0.5) <= ARRIVE_DISTANCE_SQ;
        if (arrived) {
            inhabitant.getNavigation().stop();
            return true;
        }
        // The navigator can report itself as "done" while still short of the target (e.g. stopped
        // just outside a doorway), so force a fresh path periodically rather than only when idle.
        if (inhabitant.getNavigation().isDone() || --repathCooldown <= 0) {
            inhabitant.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, WALK_SPEED);
            repathCooldown = REPATH_INTERVAL;
        }
        return false;
    }

    private boolean hasAxe(InhabitantEntity inhabitant) {
        return inhabitant.getItemBySlot(EquipmentSlot.MAINHAND).is(ItemTags.AXES);
    }

    private boolean isDaytime(ServerLevel level) {
        long dayTime = level.getDayTime() % 24000;
        return dayTime < 12542 || dayTime >= 23460;
    }

    @Override
    public String getStatusDescription() {
        return switch (phase) {
            case GOTO_HUT -> "Heading to work";
            case FIND_AXE -> "Getting equipment";
            case GOTO_TREE -> "Heading to tree";
            case CHOPPING -> "Chopping wood";
            case HARVESTING_LEAVES -> "Harvesting leaves";
            case RETURN_TO_HUT -> "Returning to hut";
            case DEPOSIT -> "Storing logs";
            case MISSING_TOOL -> "Needs an Axe";
        };
    }

    @Override
    @Nullable
    public Item getMissingItem() {
        return missingItem;
    }

    /** True while parked in {@link Phase#GOTO_TREE} with nothing found yet - see {@link #tickGotoTree}. */
    private boolean noTreeFound() {
        return trunk == null && noTreeCooldown > 0;
    }

    @Override
    public List<String> getWarnings() {
        return noTreeFound() ? List.of("I can't find any tree") : List.of();
    }

    @Override
    public List<String> getDebugLines() {
        var lines = new ArrayList<String>();
        lines.add("Phase: " + phase);
        lines.add("Trunk: " + (trunk == null ? "none" : trunk.isEmpty() ? "empty" : trunk.size() + " logs left, next " + trunk.getFirst()));
        lines.add("Canopy leaves: " + canopyLeaves.size());
        lines.add("Stand pos: " + standPos);
        lines.add("Wait ticks: " + waitTicks + " / " + CHOP_TICKS + " (impact at " + CHOP_IMPACT_TICK + ")");
        lines.add("Repath: " + repathCooldown + ", no-tree: " + noTreeCooldown);
        lines.add("Unreachable trees skipped: " + unreachableTreeBases.size());
        return lines;
    }

    @Override
    public void save(CompoundTag tag) {
        tag.putString("Phase", phase.name());
        tag.putInt("WaitTicks", waitTicks);
        actionPacer.save(tag, "Action");
        if (trunk != null) {
            tag.putLongArray("Trunk", trunk.stream().mapToLong(BlockPos::asLong).toArray());
        }
        tag.putLongArray("CanopyLeaves", canopyLeaves.stream().mapToLong(BlockPos::asLong).toArray());
        if (standPos != null) tag.putLong("StandPos", standPos.asLong());
        if (containerTarget != null) tag.putLong("ContainerTarget", containerTarget.asLong());
        if (missingItem != null) tag.putString("MissingItem", BuiltInRegistries.ITEM.getKey(missingItem).toString());
    }

    @Override
    public void load(CompoundTag tag) {
        try {
            phase = Phase.valueOf(tag.getStringOr("Phase", Phase.GOTO_HUT.name()));
        } catch (IllegalArgumentException e) {
            phase = Phase.GOTO_HUT;
        }
        waitTicks = tag.getIntOr("WaitTicks", 0);
        actionPacer.load(tag, "Action");
        trunk = tag.getLongArray("Trunk")
                .map(arr -> Arrays.stream(arr).mapToObj(BlockPos::of).collect(Collectors.toCollection(ArrayList::new)))
                .orElse(null);
        canopyLeaves = tag.getLongArray("CanopyLeaves")
                .<List<BlockPos>>map(arr -> Arrays.stream(arr).mapToObj(BlockPos::of).collect(Collectors.toCollection(ArrayList::new)))
                .orElse(List.of());
        standPos = tag.contains("StandPos") ? BlockPos.of(tag.getLongOr("StandPos", 0)) : null;
        containerTarget = tag.contains("ContainerTarget") ? BlockPos.of(tag.getLongOr("ContainerTarget", 0)) : null;
        missingItem = tag.contains("MissingItem")
                ? BuiltInRegistries.ITEM.getValue(Identifier.parse(tag.getStringOr("MissingItem", "")))
                : null;
    }
}
