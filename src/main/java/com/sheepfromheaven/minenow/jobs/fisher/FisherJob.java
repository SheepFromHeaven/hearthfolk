package com.sheepfromheaven.minenow.jobs.fisher;

import com.sheepfromheaven.minenow.structure.DominionStructure;
import com.sheepfromheaven.minenow.inhabitant.InhabitantEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
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
import java.util.HashSet;
import java.util.Set;
import com.sheepfromheaven.minenow.jobs.ContainerActionPacer;
import com.sheepfromheaven.minenow.jobs.Job;
import com.sheepfromheaven.minenow.jobs.JobActions;

/**
 * Goes to the fishery, equips a fishing rod from a nearby container, fishes at a water block
 * within the structure's range, and deposits the catch back at the hut before heading home at night.
 */
public class FisherJob implements Job {
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
    private final Set<BlockPos> unreachableWater = new HashSet<>();
    private int waitTicks;
    private final ContainerActionPacer actionPacer = new ContainerActionPacer();
    private final JobActions.Nav nav = new JobActions.Nav();
    private final JobActions.ContainerTarget containerTarget = new JobActions.ContainerTarget();
    @Nullable
    private Item missingItem;
    private int bobberEntityId = -1;

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
        inhabitant.setFisherState(0);
        despawnBobber(level);
    }

    @Override
    public void onInterrupted(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        // Whatever pulled control away (going to sleep, eating, ...) may have moved the inhabitant far
        // from wherever this phase left off - re-approach the hut rather than resuming fishing in place.
        phase = Phase.GOTO_HUT;
        containerTarget.pos = null;
        waterPos = null;
        standPos = null;
        unreachableWater.clear();
        inhabitant.setFisherState(0);
        despawnBobber(level);
    }

    @Override
    public boolean tick(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        return switch (phase) {
            case GOTO_HUT -> tickGotoHut(inhabitant, level, workplace);
            case FIND_ROD -> tickFindRod(inhabitant, level, workplace);
            case GOTO_WATER -> tickGotoWater(inhabitant, level, workplace);
            case CASTING -> tickCasting(inhabitant, level);
            case WAITING_BITE -> tickWaitingBite(inhabitant, level);
            case CATCHING -> tickCatching(inhabitant, level);
            case RETURN_TO_HUT -> tickReturnToHut(inhabitant, level, workplace);
            case DEPOSIT -> tickDeposit(inhabitant, level, workplace);
            case MISSING_TOOL -> tickMissingTool(inhabitant, level, workplace);
        };
    }

    private boolean tickGotoHut(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        if (JobActions.goToJobsite(inhabitant, level, workplace, nav)) {
            phase = hasRod(inhabitant) ? Phase.GOTO_WATER : Phase.FIND_ROD;
        }
        return true;
    }

    private boolean tickFindRod(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        if (hasRod(inhabitant)) {
            phase = Phase.GOTO_WATER;
            return true;
        }
        boolean wasSearching = containerTarget.pos == null;
        var rod = JobActions.takeFromContainer(inhabitant, level, workplace, actionPacer, nav, containerTarget,
                stack -> stack.is(Items.FISHING_ROD), true);
        if (!rod.isEmpty()) {
            inhabitant.setItemSlot(EquipmentSlot.MAINHAND, rod);
            missingItem = null;
            phase = Phase.GOTO_WATER;
            return true;
        }
        if (wasSearching && containerTarget.pos == null) {
            missingItem = Items.FISHING_ROD;
            phase = Phase.MISSING_TOOL;
            waitTicks = MISSING_TOOL_RETRY_TICKS;
        }
        return true;
    }

    private boolean tickGotoWater(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        if (waterPos == null) {
            var rangeReqs = workplace.getType().getRangeRequirements();
            if (rangeReqs.isEmpty()) {
                return false;
            }
            waterPos = workplace.findRangeRequirementPosition(level, rangeReqs.getFirst(), unreachableWater).orElse(null);
            if (waterPos == null) {
                return false;
            }
            var shorePos = ShoreScanner.findShorePosition(level, waterPos);
            if (shorePos.isEmpty()) {
                // This water tile has no dry tile beside it (e.g. the interior of a larger pond/lake) -
                // don't walk the inhabitant onto it; blacklist it and try the next candidate next tick.
                unreachableWater.add(waterPos);
                waterPos = null;
                return true;
            }
            standPos = shorePos.get();
        }
        if (JobActions.walkTo(inhabitant, standPos, nav)) {
            if (inhabitant.isInWater()) {
                // The arrive-distance check is generous enough that overshooting onto the water tile
                // itself still counts as "arrived" at the shore. Push back out rather than casting from in there.
                inhabitant.getNavigation().moveTo(standPos.getX() + 0.5, standPos.getY(), standPos.getZ() + 0.5, WALK_SPEED);
                return true;
            }
            inhabitant.setDeltaMovement(Vec3.ZERO);
            phase = Phase.CASTING;
            waitTicks = CAST_TICKS;
            inhabitant.setFisherState(1);
            spawnBobber(level, inhabitant);
            inhabitant.swing(InteractionHand.MAIN_HAND);
            level.playSound(null, waterPos, net.minecraft.sounds.SoundEvents.FISHING_BOBBER_THROW,
                    net.minecraft.sounds.SoundSource.NEUTRAL, 0.5F, 0.4F / (inhabitant.getRandom().nextFloat() * 0.4F + 0.8F));
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

    private boolean tickCasting(InhabitantEntity inhabitant, ServerLevel level) {
        if (isWaterGone(level)) {
            abandonWaterSource(inhabitant, level);
            return true;
        }
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
        if (isWaterGone(level)) {
            abandonWaterSource(inhabitant, level);
            return true;
        }
        keepOnShore(inhabitant);
        if (waterPos != null) {
            inhabitant.getLookControl().setLookAt(waterPos.getX() + 0.5, waterPos.getY(), waterPos.getZ() + 0.5);
        }
        if (!isDaytime(level) && inhabitant.getInventory().isEmpty()) {
            // Nothing caught yet and night has fallen - head back rather than fish forever.
            phase = Phase.RETURN_TO_HUT;
            return true;
        }
        if (--waitTicks <= 0) {
            phase = Phase.CATCHING;
            waitTicks = 10; // hold CATCHING for the reel animation (0.5 s)
            inhabitant.setFisherState(2);
            if (waterPos != null) {
                level.playSound(null, waterPos, net.minecraft.sounds.SoundEvents.FISHING_BOBBER_SPLASH,
                        net.minecraft.sounds.SoundSource.NEUTRAL, 0.25F, 1.0F + (inhabitant.getRandom().nextFloat() - inhabitant.getRandom().nextFloat()) * 0.4F);
                level.playSound(null, waterPos, net.minecraft.sounds.SoundEvents.FISHING_BOBBER_RETRIEVE,
                        net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 1.0F);
            }
            inhabitant.swing(InteractionHand.MAIN_HAND);
        }
        return true;
    }

    private boolean tickCatching(InhabitantEntity inhabitant, ServerLevel level) {
        keepOnShore(inhabitant);
        if (waterPos != null) {
            inhabitant.getLookControl().setLookAt(waterPos.getX() + 0.5, waterPos.getY(), waterPos.getZ() + 0.5);
        }
        if (--waitTicks > 0) {
            return true; // wait for reel animation to finish
        }

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
        if (rodBroken || full || !isDaytime(level)) {
            phase = Phase.RETURN_TO_HUT;
            inhabitant.setFisherState(0);
            despawnBobber(level);
        } else {
            phase = Phase.CASTING;
            waitTicks = CAST_TICKS;
            inhabitant.setFisherState(1);
            spawnBobber(level, inhabitant);
        }
        return true;
    }

    private boolean tickReturnToHut(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        if (JobActions.goToJobsite(inhabitant, level, workplace, nav)) {
            phase = Phase.DEPOSIT;
        }
        return true;
    }

    private boolean tickDeposit(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        if (!JobActions.depositCarriedItems(inhabitant, level, workplace, actionPacer, nav, containerTarget)) {
            return true;
        }
        phase = Phase.GOTO_HUT;
        waterPos = null;
        standPos = null;
        unreachableWater.clear();
        return false;
    }

    private boolean tickMissingTool(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        if (--waitTicks <= 0) {
            phase = Phase.FIND_ROD;
        }
        return true;
    }

    private boolean isWaterGone(ServerLevel level) {
        return waterPos != null && !level.getFluidState(waterPos).is(net.minecraft.tags.FluidTags.WATER);
    }

    private void abandonWaterSource(InhabitantEntity inhabitant, ServerLevel level) {
        inhabitant.setFisherState(0);
        despawnBobber(level);
        unreachableWater.add(waterPos);
        waterPos = null;
        standPos = null;
        phase = Phase.GOTO_WATER;
    }

    private void spawnBobber(ServerLevel level, InhabitantEntity inhabitant) {
        despawnBobber(level);
        if (waterPos == null) return;
        var bobber = new com.sheepfromheaven.minenow.jobs.fisher.NpcFishingBobber(
                com.sheepfromheaven.minenow.inhabitant.ModEntities.NPC_FISHING_BOBBER.get(), level);
        bobber.setPos(waterPos.getX() + 0.5, waterPos.getY() + 0.875, waterPos.getZ() + 0.5);
        bobber.setOwnerEntityId(inhabitant.getId());
        level.addFreshEntity(bobber);
        bobberEntityId = bobber.getId();
    }

    private void despawnBobber(ServerLevel level) {
        if (bobberEntityId == -1) return;
        var entity = level.getEntity(bobberEntityId);
        if (entity != null) entity.discard();
        bobberEntityId = -1;
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
        actionPacer.save(tag, "Action");
        if (waterPos != null) tag.putLong("WaterPos", waterPos.asLong());
        if (standPos != null) tag.putLong("StandPos", standPos.asLong());
        if (containerTarget.pos != null) tag.putLong("ContainerTarget", containerTarget.pos.asLong());
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
        waterPos = tag.contains("WaterPos") ? BlockPos.of(tag.getLongOr("WaterPos", 0)) : null;
        standPos = tag.contains("StandPos") ? BlockPos.of(tag.getLongOr("StandPos", 0)) : null;
        containerTarget.pos = tag.contains("ContainerTarget") ? BlockPos.of(tag.getLongOr("ContainerTarget", 0)) : null;
        missingItem = tag.contains("MissingItem")
                ? BuiltInRegistries.ITEM.getValue(Identifier.parse(tag.getStringOr("MissingItem", "")))
                : null;
    }
}
