package com.sheepfromheaven.minenow.entity.job;

import com.sheepfromheaven.minenow.dominion.DominionStructure;
import com.sheepfromheaven.minenow.entity.InhabitantEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Goes to the fishery, equips a fishing rod from a nearby container, fishes at a water block
 * within the structure's range, and deposits the catch back at the hut before heading home at night.
 */
public class FisherJob implements Job {
    private static final int ARRIVE_DISTANCE_SQ = 4;
    private static final int REPATH_INTERVAL = 60;
    private static final double WALK_SPEED = 0.6;
    private static final int SHORE_CHECK_INTERVAL = 5;
    private static final int CAST_TICKS = 30;
    private static final int MIN_BITE_TICKS = 100;
    private static final int MAX_BITE_TICKS = 600;
    private static final int MISSING_TOOL_RETRY_TICKS = 100;
    private static final int ROD_DAMAGE_PER_CATCH = 1;

    private enum Phase { GOTO_HUT, FIND_ROD, GOTO_WATER, CASTING, WAITING_BITE, CATCHING, RETURN_TO_HUT, DEPOSIT, MISSING_TOOL }

    private Phase phase = Phase.GOTO_HUT;
    private BlockPos waterPos;
    private BlockPos standPos;
    private int waitTicks;
    private int repathCooldown;
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
    public boolean tick(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        return switch (phase) {
            case GOTO_HUT -> tickGotoHut(inhabitant, workplace);
            case FIND_ROD -> tickFindRod(inhabitant, level, workplace);
            case GOTO_WATER -> tickGotoWater(inhabitant, level, workplace);
            case CASTING -> tickCasting(inhabitant);
            case WAITING_BITE -> tickWaitingBite(inhabitant, level);
            case CATCHING -> tickCatching(inhabitant, level);
            case RETURN_TO_HUT -> tickReturnToHut(inhabitant, workplace);
            case DEPOSIT -> tickDeposit(inhabitant, level, workplace);
            case MISSING_TOOL -> tickMissingTool(inhabitant, level, workplace);
        };
    }

    private boolean tickGotoHut(InhabitantEntity inhabitant, DominionStructure workplace) {
        if (moveTowardsAndCheckArrival(inhabitant, workplace.getMarkerPos())) {
            phase = hasRod(inhabitant) ? Phase.GOTO_WATER : Phase.FIND_ROD;
        }
        return true;
    }

    private boolean tickFindRod(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        if (hasRod(inhabitant)) {
            phase = Phase.GOTO_WATER;
            return true;
        }
        for (var pos : workplace.findContainers(level)) {
            if (level.getBlockEntity(pos) instanceof Container container) {
                var rod = ContainerUtil.extractStack(container, Items.FISHING_ROD);
                if (!rod.isEmpty()) {
                    inhabitant.setItemSlot(EquipmentSlot.MAINHAND, rod);
                    missingItem = null;
                    phase = Phase.GOTO_WATER;
                    return true;
                }
            }
        }
        missingItem = Items.FISHING_ROD;
        phase = Phase.MISSING_TOOL;
        waitTicks = MISSING_TOOL_RETRY_TICKS;
        return true;
    }

    private boolean tickGotoWater(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        if (waterPos == null) {
            var rangeReqs = workplace.getType().getRangeRequirements();
            if (rangeReqs.isEmpty()) {
                return false;
            }
            waterPos = workplace.findRangeRequirementPosition(level, rangeReqs.getFirst()).orElse(null);
            if (waterPos == null) {
                return false;
            }
            standPos = findShorePosition(level, waterPos);
        }
        if (moveTowardsAndCheckArrival(inhabitant, standPos)) {
            if (inhabitant.isInWater()) {
                // The arrive-distance check is generous enough that overshooting onto the water tile
                // itself still counts as "arrived" at the shore. Push back out rather than casting from in there.
                inhabitant.getNavigation().moveTo(standPos.getX() + 0.5, standPos.getY(), standPos.getZ() + 0.5, WALK_SPEED);
                return true;
            }
            inhabitant.setDeltaMovement(Vec3.ZERO);
            phase = Phase.CASTING;
            waitTicks = CAST_TICKS;
            inhabitant.swing(InteractionHand.MAIN_HAND);
        }
        return true;
    }

    /**
     * Momentum from the approach (or fluid push at the water's edge) can carry the inhabitant onto the
     * water tile a tick after "arriving" on shore. Snap back onto dry land rather than leaving them stuck
     * fishing from inside the water.
     */
    private void keepOnShore(InhabitantEntity inhabitant) {
        if (inhabitant.tickCount % SHORE_CHECK_INTERVAL != 0) {
            return;
        }
        if (standPos != null && inhabitant.isInWater()) {
            inhabitant.setDeltaMovement(Vec3.ZERO);
            inhabitant.snapTo(standPos.getX() + 0.5, standPos.getY(), standPos.getZ() + 0.5, inhabitant.getYRot(), inhabitant.getXRot());
        }
    }

    /** Finds a dry tile next to the water block to fish from, rather than standing in the water itself. */
    private BlockPos findShorePosition(ServerLevel level, BlockPos water) {
        for (var dir : Direction.Plane.HORIZONTAL) {
            var candidate = water.relative(dir);
            if (isStandableShore(level, candidate)) {
                return candidate;
            }
        }
        return water;
    }

    private boolean isStandableShore(ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        if (!state.getFluidState().isEmpty() || !state.getCollisionShape(level, pos).isEmpty()) {
            return false;
        }
        var belowPos = pos.below();
        return !level.getBlockState(belowPos).getCollisionShape(level, belowPos).isEmpty();
    }

    private boolean tickCasting(InhabitantEntity inhabitant) {
        keepOnShore(inhabitant);
        if (waterPos != null) {
            inhabitant.getLookControl().setLookAt(waterPos.getX() + 0.5, waterPos.getY(), waterPos.getZ() + 0.5);
        }
        if (--waitTicks <= 0) {
            phase = Phase.WAITING_BITE;
            waitTicks = MIN_BITE_TICKS + inhabitant.getRandom().nextInt(MAX_BITE_TICKS - MIN_BITE_TICKS);
        }
        return true;
    }

    private boolean tickWaitingBite(InhabitantEntity inhabitant, ServerLevel level) {
        keepOnShore(inhabitant);
        if (!isDaytime(level) && inhabitant.getInventory().isEmpty()) {
            // Nothing caught yet and night has fallen - head back rather than fish forever.
            phase = Phase.RETURN_TO_HUT;
            return true;
        }
        if (--waitTicks <= 0) {
            phase = Phase.CATCHING;
            inhabitant.swing(InteractionHand.MAIN_HAND);
        }
        return true;
    }

    private boolean tickCatching(InhabitantEntity inhabitant, ServerLevel level) {
        keepOnShore(inhabitant);
        var rod = inhabitant.getItemBySlot(EquipmentSlot.MAINHAND);
        if (waterPos != null && !rod.isEmpty()) {
            var lootParams = new LootParams.Builder(level)
                    .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(waterPos))
                    .withParameter(LootContextParams.TOOL, rod)
                    .withLuck(0)
                    .create(LootContextParamSets.FISHING);
            var lootTable = level.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.FISHING);
            for (var caught : lootTable.getRandomItems(lootParams)) {
                inhabitant.getInventory().addItem(caught);
            }
            rod.hurtAndBreak(ROD_DAMAGE_PER_CATCH, inhabitant, EquipmentSlot.MAINHAND);
        }

        boolean rodBroken = inhabitant.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty();
        boolean full = !inhabitant.getInventory().canAddItem(new ItemStack(Items.COD));
        if (rodBroken) {
            phase = Phase.RETURN_TO_HUT;
        } else if (full || !isDaytime(level)) {
            phase = Phase.RETURN_TO_HUT;
        } else {
            phase = Phase.CASTING;
            waitTicks = CAST_TICKS;
        }
        return true;
    }

    private boolean tickReturnToHut(InhabitantEntity inhabitant, DominionStructure workplace) {
        if (moveTowardsAndCheckArrival(inhabitant, workplace.getMarkerPos())) {
            phase = Phase.DEPOSIT;
        }
        return true;
    }

    private boolean tickDeposit(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        var carried = inhabitant.getInventory();
        for (var pos : workplace.findContainers(level)) {
            if (level.getBlockEntity(pos) instanceof Container container) {
                for (int i = 0; i < carried.getContainerSize(); i++) {
                    var stack = carried.getItem(i);
                    if (!stack.isEmpty()) {
                        carried.setItem(i, ContainerUtil.insertItem(container, stack));
                    }
                }
            }
        }
        phase = Phase.GOTO_HUT;
        waterPos = null;
        standPos = null;
        return false;
    }

    private boolean tickMissingTool(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        if (--waitTicks <= 0) {
            phase = Phase.FIND_ROD;
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

    private boolean hasRod(InhabitantEntity inhabitant) {
        return inhabitant.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.FISHING_ROD);
    }

    private boolean isDaytime(ServerLevel level) {
        long dayTime = level.getDayTime() % 24000;
        return dayTime < 12542 || dayTime >= 23460;
    }

    @Override
    public String getStatusDescription() {
        return switch (phase) {
            case GOTO_HUT -> "Heading to work";
            case FIND_ROD -> "Getting equipment";
            case GOTO_WATER -> "Heading to water";
            case CASTING -> "Casting line";
            case WAITING_BITE -> "Fishing";
            case CATCHING -> "Reeling it in";
            case RETURN_TO_HUT -> "Returning to hut";
            case DEPOSIT -> "Storing catch";
            case MISSING_TOOL -> "Needs a Fishing Rod";
        };
    }

    @Override
    @Nullable
    public Item getMissingItem() {
        return missingItem;
    }

    @Override
    public void save(CompoundTag tag) {
        tag.putString("Phase", phase.name());
        tag.putInt("WaitTicks", waitTicks);
        if (waterPos != null) tag.putLong("WaterPos", waterPos.asLong());
        if (standPos != null) tag.putLong("StandPos", standPos.asLong());
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
        waterPos = tag.contains("WaterPos") ? BlockPos.of(tag.getLongOr("WaterPos", 0)) : null;
        standPos = tag.contains("StandPos") ? BlockPos.of(tag.getLongOr("StandPos", 0)) : null;
        missingItem = tag.contains("MissingItem")
                ? BuiltInRegistries.ITEM.getValue(Identifier.parse(tag.getStringOr("MissingItem", "")))
                : null;
    }
}
