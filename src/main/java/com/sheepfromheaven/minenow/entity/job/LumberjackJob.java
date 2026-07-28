package com.sheepfromheaven.minenow.entity.job;

import com.sheepfromheaven.minenow.dominion.DominionStructure;
import com.sheepfromheaven.minenow.entity.InhabitantEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

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
    private static final int CHOP_TICKS = 30;
    private static final int MISSING_TOOL_RETRY_TICKS = 100;
    private static final int NO_TREE_RETRY_TICKS = 100;
    private static final int AXE_DAMAGE_PER_LOG = 2;
    /** Roughly one storage action per second, so grabbing/depositing items reads as deliberate rather than instant. */
    private static final int ACTION_COOLDOWN_TICKS = 20;

    private enum Phase { GOTO_HUT, FIND_AXE, GOTO_TREE, CHOPPING, RETURN_TO_HUT, DEPOSIT, MISSING_TOOL }

    private Phase phase = Phase.GOTO_HUT;
    @Nullable
    private List<BlockPos> trunk;
    private BlockPos standPos;
    private int waitTicks;
    private int repathCooldown;
    private int noTreeCooldown;
    private int actionCooldown;
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
    }

    @Override
    public void onInterrupted(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
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
        if (actionCooldown > 0) {
            actionCooldown--;
            return true;
        }
        if (level.getBlockEntity(containerTarget) instanceof Container container) {
            var axe = ContainerUtil.extractFirst(container, inhabitant, stack -> stack.is(ItemTags.AXES));
            if (!axe.isEmpty()) {
                inhabitant.setItemSlot(EquipmentSlot.MAINHAND, axe);
                missingItem = null;
                actionCooldown = ACTION_COOLDOWN_TICKS;
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
            var found = workplace.findTreeInRange(level, unreachableTreeBases).orElse(null);
            if (found == null) {
                noTreeCooldown = NO_TREE_RETRY_TICKS;
                // Voice it every retry (every NO_TREE_RETRY_TICKS) rather than every tick, so the
                // player gets an audible/text nudge that something's wrong without being spammed.
                inhabitant.playGoalSwitchSound();
                return true;
            }
            var base = found.getFirst();
            var candidate = findStandableNeighbor(level, base);
            if (candidate == null) {
                // No reachable tile around this trunk - skip it on future scans instead of sending
                // moveTo() at a solid log forever, but retry immediately in case another tree in range
                // is reachable.
                unreachableTreeBases.add(base);
                return true;
            }
            trunk = new ArrayList<>(found);
            standPos = candidate;
        }
        if (moveTowardsAndCheckArrival(inhabitant, standPos)) {
            inhabitant.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            phase = Phase.CHOPPING;
            waitTicks = CHOP_TICKS;
            inhabitant.swing(InteractionHand.MAIN_HAND);
        }
        return true;
    }

    private static final int[][] NEIGHBOR_OFFSETS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };

    /**
     * Finds a standable tile next to the trunk's base to chop from, rather than standing inside the tree.
     * Checks all 8 surrounding tiles (not just the 4 cardinal ones) since canopy leaves often block one
     * or more sides at head height. Returns null if none of them are reachable - the caller must not
     * send the inhabitant to stand inside the (solid) trunk itself.
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
        if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
            return false;
        }
        // Leaves commonly overhang the ground right next to a trunk - a tile can look clear at foot
        // level yet still be unreachable because there's no headroom, silently stranding the pathfinder.
        if (!level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()) {
            return false;
        }
        var below = pos.below();
        return !level.getBlockState(below).getCollisionShape(level, below).isEmpty();
    }

    private boolean tickChopping(InhabitantEntity inhabitant, ServerLevel level) {
        if (trunk == null || trunk.isEmpty()) {
            phase = Phase.RETURN_TO_HUT;
            return true;
        }
        var targetPos = trunk.getFirst();
        inhabitant.getLookControl().setLookAt(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);
        if (--waitTicks > 0) {
            return true;
        }

        var pos = trunk.removeFirst();
        var state = level.getBlockState(pos);
        var soundType = state.getSoundType();
        level.playSound(null, pos, soundType.getBreakSound(), net.minecraft.sounds.SoundSource.BLOCKS,
                soundType.getVolume(), soundType.getPitch());
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        inhabitant.getInventory().addItem(new ItemStack(state.getBlock().asItem()));
        inhabitant.swing(InteractionHand.MAIN_HAND);

        var axe = inhabitant.getItemBySlot(EquipmentSlot.MAINHAND);
        if (!axe.isEmpty()) {
            axe.hurtAndBreak(AXE_DAMAGE_PER_LOG, inhabitant, EquipmentSlot.MAINHAND);
        }

        if (trunk.isEmpty() || inhabitant.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()) {
            phase = Phase.RETURN_TO_HUT;
        } else {
            waitTicks = CHOP_TICKS;
        }
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
            if (actionCooldown > 0) {
                actionCooldown--;
                return true;
            }
            if (level.getBlockEntity(containerTarget) instanceof Container container) {
                carried.setItem(i, ContainerUtil.insertItem(container, inhabitant, stack));
            }
            actionCooldown = ACTION_COOLDOWN_TICKS;
            containerTarget = null;
            return true;
        }
        phase = Phase.GOTO_HUT;
        trunk = null;
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
        lines.add("Trunk: " + (trunk == null ? "none" : trunk.size() + " logs left, base " + trunk.getFirst()));
        lines.add("Stand pos: " + standPos);
        lines.add("Wait ticks: " + waitTicks + ", repath cooldown: " + repathCooldown + ", no-tree cooldown: " + noTreeCooldown);
        lines.add("Unreachable trees skipped: " + unreachableTreeBases.size());
        lines.add("No tree found: " + noTreeFound());
        return lines;
    }

    @Override
    public void save(CompoundTag tag) {
        tag.putString("Phase", phase.name());
        tag.putInt("WaitTicks", waitTicks);
        tag.putInt("ActionCooldown", actionCooldown);
        if (trunk != null) {
            tag.putLongArray("Trunk", trunk.stream().mapToLong(BlockPos::asLong).toArray());
        }
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
        actionCooldown = tag.getIntOr("ActionCooldown", 0);
        trunk = tag.getLongArray("Trunk")
                .map(arr -> Arrays.stream(arr).mapToObj(BlockPos::of).collect(Collectors.toCollection(ArrayList::new)))
                .orElse(null);
        standPos = tag.contains("StandPos") ? BlockPos.of(tag.getLongOr("StandPos", 0)) : null;
        containerTarget = tag.contains("ContainerTarget") ? BlockPos.of(tag.getLongOr("ContainerTarget", 0)) : null;
        missingItem = tag.contains("MissingItem")
                ? BuiltInRegistries.ITEM.getValue(Identifier.parse(tag.getStringOr("MissingItem", "")))
                : null;
    }
}
