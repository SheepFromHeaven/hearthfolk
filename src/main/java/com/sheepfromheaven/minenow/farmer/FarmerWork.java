package com.sheepfromheaven.minenow.farmer;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.DoNothing;
import net.minecraft.world.entity.ai.behavior.GiveGiftToHero;
import net.minecraft.world.entity.ai.behavior.RunOne;
import net.minecraft.world.entity.ai.behavior.SetEntityLookTarget;
import net.minecraft.world.entity.ai.behavior.SetLookAndInteract;
import net.minecraft.world.entity.ai.behavior.ShowTradesToPlayer;
import net.minecraft.world.entity.ai.behavior.StrollAroundPoi;
import net.minecraft.world.entity.ai.behavior.StrollToPoi;
import net.minecraft.world.entity.ai.behavior.StrollToPoiList;
import net.minecraft.world.entity.ai.behavior.UpdateActivityFromSchedule;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;


/**
 * A farmer's WORK package: vanilla's, minus WorkAtComposter (bakes wheat into bread, composts seeds), HarvestFarmland
 * (tool-free harvesting) and UseBonemeal. TendFields does that work instead. Used by VillagerBrains.
 */
public final class FarmerWork {
    private static final float SPEED = 0.5F;

    private FarmerWork() {}

    public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> workPackage() {
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
                Pair.of(2, com.sheepfromheaven.minenow.villager.VillagerBrains.jobSiteWalk()),
                Pair.of(3, new GiveGiftToHero(100)),
                Pair.of(99, UpdateActivityFromSchedule.create()));
    }
}
