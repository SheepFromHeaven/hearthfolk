package com.sheepfromheaven.minenow.farmer;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.DoNothing;
import net.minecraft.world.entity.ai.behavior.GiveGiftToHero;
import net.minecraft.world.entity.ai.behavior.RunOne;
import net.minecraft.world.entity.ai.behavior.SetEntityLookTarget;
import net.minecraft.world.entity.ai.behavior.SetLookAndInteract;
import net.minecraft.world.entity.ai.behavior.SetWalkTargetFromBlockMemory;
import net.minecraft.world.entity.ai.behavior.ShowTradesToPlayer;
import net.minecraft.world.entity.ai.behavior.StrollAroundPoi;
import net.minecraft.world.entity.ai.behavior.StrollToPoi;
import net.minecraft.world.entity.ai.behavior.StrollToPoiList;
import net.minecraft.world.entity.ai.behavior.UpdateActivityFromSchedule;
import net.minecraft.world.entity.ai.behavior.VillagerGoalPackages;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;

import java.util.Set;

/**
 * Brains can't drop a single behaviour, so a farmer's brain is rebuilt from vanilla's own packages (as listed in
 * Villager.BRAIN_PROVIDER) with a WORK package that leaves out WorkAtComposter (bakes wheat into bread, composts seeds),
 * HarvestFarmland (tool-free harvesting) and UseBonemeal. TendFields does that work instead.
 * Only safe on a brain that hasn't ticked yet, since running behaviours would never be stopped.
 */
public final class FarmerWork {
    private static final float SPEED = 0.5F;

    private FarmerWork() {}

    public static void replaceVanillaWork(Brain<Villager> brain, Villager villager) {
        Holder<VillagerProfession> profession = villager.getVillagerData().profession();
        brain.removeAllBehaviors();
        brain.addActivity(Activity.CORE, VillagerGoalPackages.getCorePackage(profession, SPEED), ImmutableSet.of(), Set.of());
        brain.addActivity(Activity.WORK, workPackage(), ImmutableSet.of(Pair.of(MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT)), Set.of());
        brain.addActivity(Activity.MEET, VillagerGoalPackages.getMeetPackage(SPEED), ImmutableSet.of(Pair.of(MemoryModuleType.MEETING_POINT, MemoryStatus.VALUE_PRESENT)), Set.of());
        brain.addActivity(Activity.REST, VillagerGoalPackages.getRestPackage(SPEED), ImmutableSet.of(), Set.of());
        brain.addActivity(Activity.IDLE, VillagerGoalPackages.getIdlePackage(SPEED), ImmutableSet.of(), Set.of());
        brain.addActivity(Activity.PANIC, VillagerGoalPackages.getPanicPackage(SPEED), ImmutableSet.of(), Set.of());
        brain.addActivity(Activity.PRE_RAID, VillagerGoalPackages.getPreRaidPackage(SPEED), ImmutableSet.of(), Set.of());
        brain.addActivity(Activity.RAID, VillagerGoalPackages.getRaidPackage(SPEED), ImmutableSet.of(), Set.of());
        brain.addActivity(Activity.HIDE, VillagerGoalPackages.getHidePackage(SPEED), ImmutableSet.of(), Set.of());
    }

    private static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> workPackage() {
        return ImmutableList.of(
                Pair.of(5, new RunOne<>(ImmutableList.of(
                        Pair.of(SetEntityLookTarget.create(EntityTypes.VILLAGER, 8.0F), 2),
                        Pair.of(SetEntityLookTarget.create(EntityTypes.PLAYER, 8.0F), 2),
                        Pair.of(new DoNothing(30, 60), 8)))),
                Pair.of(5, new RunOne<Villager>(ImmutableList.of(
                        Pair.of(StrollAroundPoi.create(MemoryModuleType.JOB_SITE, 0.4F, 4), 2),
                        Pair.of(StrollToPoi.create(MemoryModuleType.JOB_SITE, 0.4F, 1, 10), 5),
                        Pair.of(StrollToPoiList.create(MemoryModuleType.SECONDARY_JOB_SITE, SPEED, 1, 6, MemoryModuleType.JOB_SITE), 5)))),
                Pair.of(10, new ShowTradesToPlayer(400, 1600)),
                Pair.of(10, SetLookAndInteract.create(EntityTypes.PLAYER, 4)),
                Pair.of(2, SetWalkTargetFromBlockMemory.create(MemoryModuleType.JOB_SITE, SPEED, 9, 100, 1200)),
                Pair.of(3, new GiveGiftToHero(100)),
                Pair.of(99, UpdateActivityFromSchedule.create()));
    }
}
