package com.sheepfromheaven.minenow.villager;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Pair;
import com.sheepfromheaven.minenow.farmer.FarmerRules;
import com.sheepfromheaven.minenow.farmer.FarmerWork;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.DoNothing;
import net.minecraft.world.entity.ai.behavior.GateBehavior;
import net.minecraft.world.entity.ai.behavior.GiveGiftToHero;
import net.minecraft.world.entity.ai.behavior.InteractWith;
import net.minecraft.world.entity.ai.behavior.JumpOnBed;
import net.minecraft.world.entity.ai.behavior.RunOne;
import net.minecraft.world.entity.ai.behavior.SetEntityLookTarget;
import net.minecraft.world.entity.ai.behavior.SetLookAndInteract;
import net.minecraft.world.entity.ai.behavior.SetWalkTargetFromBlockMemory;
import net.minecraft.world.entity.ai.behavior.SetWalkTargetFromLookTarget;
import net.minecraft.world.entity.ai.behavior.ShowTradesToPlayer;
import net.minecraft.world.entity.ai.behavior.SocializeAtBell;
import net.minecraft.world.entity.ai.behavior.StrollAroundPoi;
import net.minecraft.world.entity.ai.behavior.TriggerGate;
import net.minecraft.world.entity.ai.behavior.UpdateActivityFromSchedule;
import net.minecraft.world.entity.ai.behavior.ValidateNearbyPoi;
import net.minecraft.world.entity.ai.behavior.VillageBoundRandomStroll;
import net.minecraft.world.entity.ai.behavior.VillagerGoalPackages;
import net.minecraft.world.entity.ai.behavior.VillagerMakeLove;
import net.minecraft.world.entity.ai.behavior.VillagerPanicTrigger;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;

import java.util.Set;

/**
 * Brains can't drop a single behaviour, so every adult villager's brain is rebuilt from vanilla's own packages (as
 * listed in Villager.BRAIN_PROVIDER), changed in three places: MEET and IDLE leave out TradeWithVillager (villagers
 * handing each other food and goods, which would bypass trading), farmers get {@link FarmerWork}'s WORK package, and
 * CORE has {@link PanicOrFight} instead of the panic trigger (with a FIGHT activity for it).
 * Only safe on a brain that hasn't ticked yet, since running behaviours would never be stopped.
 */
public final class VillagerBrains {
    private static final float SPEED = 0.5F;

    private VillagerBrains() {}

    public static void rebuild(Brain<Villager> brain, Villager villager) {
        Holder<VillagerProfession> profession = villager.getVillagerData().profession();
        var work = FarmerRules.isFarmer(villager) ? FarmerWork.workPackage() : VillagerGoalPackages.getWorkPackage(profession, SPEED);
        brain.removeAllBehaviors();
        brain.addActivity(Activity.CORE, corePackage(profession), ImmutableSet.of(), Set.of());
        brain.addActivity(Activity.FIGHT, ImmutableList.of(Pair.of(99, new DoNothing(20, 40))), ImmutableSet.of(), Set.of());
        brain.addActivity(Activity.WORK, work, ImmutableSet.of(Pair.of(MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT)), Set.of());
        brain.addActivity(Activity.MEET, meetPackage(), ImmutableSet.of(Pair.of(MemoryModuleType.MEETING_POINT, MemoryStatus.VALUE_PRESENT)), Set.of());
        brain.addActivity(Activity.REST, VillagerGoalPackages.getRestPackage(SPEED), ImmutableSet.of(), Set.of());
        brain.addActivity(Activity.IDLE, idlePackage(), ImmutableSet.of(), Set.of());
        brain.addActivity(Activity.PANIC, VillagerGoalPackages.getPanicPackage(SPEED), ImmutableSet.of(), Set.of());
        brain.addActivity(Activity.PRE_RAID, VillagerGoalPackages.getPreRaidPackage(SPEED), ImmutableSet.of(), Set.of());
        brain.addActivity(Activity.RAID, VillagerGoalPackages.getRaidPackage(SPEED), ImmutableSet.of(), Set.of());
        brain.addActivity(Activity.HIDE, VillagerGoalPackages.getHidePackage(SPEED), ImmutableSet.of(), Set.of());
    }

    /** Vanilla's, with {@link PanicOrFight} in place of its panic trigger. */
    private static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> corePackage(Holder<VillagerProfession> profession) {
        return java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(Pair.<Integer, BehaviorControl<? super Villager>>of(0, new Eyes())),
                        VillagerGoalPackages.getCorePackage(profession, SPEED).stream())
                .map(pair -> pair.getSecond() instanceof VillagerPanicTrigger
                        ? Pair.<Integer, BehaviorControl<? super Villager>>of(pair.getFirst(), new PanicOrFight())
                        : Pair.<Integer, BehaviorControl<? super Villager>>of(pair.getFirst(), pair.getSecond()))
                .collect(ImmutableList.toImmutableList());
    }

    private static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> meetPackage() {
        return ImmutableList.of(
                Pair.of(2, TriggerGate.triggerOneShuffled(ImmutableList.of(
                        Pair.of(StrollAroundPoi.create(MemoryModuleType.MEETING_POINT, 0.4F, 40), 2),
                        Pair.of(SocializeAtBell.create(), 2)))),
                Pair.of(10, new ShowTradesToPlayer(400, 1600)),
                Pair.of(10, SetLookAndInteract.create(EntityTypes.PLAYER, 4)),
                Pair.of(2, SetWalkTargetFromBlockMemory.create(MemoryModuleType.MEETING_POINT, SPEED, 6, 100, 200)),
                Pair.of(3, new GiveGiftToHero(100)),
                Pair.of(3, ValidateNearbyPoi.create(type -> type.is(PoiTypes.MEETING), MemoryModuleType.MEETING_POINT)),
                fullLook(),
                Pair.of(99, UpdateActivityFromSchedule.create()));
    }

    private static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> idlePackage() {
        return ImmutableList.of(
                Pair.of(2, new RunOne<Villager>(ImmutableList.of(
                        Pair.of(InteractWith.of(EntityTypes.VILLAGER, 8, MemoryModuleType.INTERACTION_TARGET, SPEED, 2), 2),
                        Pair.of(InteractWith.of(EntityTypes.VILLAGER, 8, AgeableMob::canBreed, AgeableMob::canBreed, MemoryModuleType.BREED_TARGET, SPEED, 2), 1),
                        Pair.of(InteractWith.of(EntityTypes.CAT, 8, MemoryModuleType.INTERACTION_TARGET, SPEED, 2), 1),
                        Pair.of(VillageBoundRandomStroll.create(SPEED), 1),
                        Pair.of(SetWalkTargetFromLookTarget.create(SPEED, 2), 1),
                        Pair.of(new JumpOnBed(SPEED), 1),
                        Pair.of(new DoNothing(30, 60), 1)))),
                Pair.of(3, new GiveGiftToHero(100)),
                Pair.of(3, SetLookAndInteract.create(EntityTypes.PLAYER, 4)),
                Pair.of(3, new ShowTradesToPlayer(400, 1600)),
                Pair.of(3, new GateBehavior<>(
                        java.util.Map.of(),
                        ImmutableSet.of(MemoryModuleType.BREED_TARGET),
                        GateBehavior.OrderPolicy.ORDERED,
                        GateBehavior.RunningPolicy.RUN_ONE,
                        ImmutableList.of(Pair.of(new VillagerMakeLove(), 1)))),
                fullLook(),
                Pair.of(99, UpdateActivityFromSchedule.create()));
    }

    private static Pair<Integer, BehaviorControl<LivingEntity>> fullLook() {
        return Pair.of(5, new RunOne<>(ImmutableList.of(
                Pair.of(SetEntityLookTarget.create(EntityTypes.CAT, 8.0F), 8),
                Pair.of(SetEntityLookTarget.create(EntityTypes.VILLAGER, 8.0F), 2),
                Pair.of(SetEntityLookTarget.create(EntityTypes.PLAYER, 8.0F), 2),
                Pair.of(SetEntityLookTarget.create(MobCategory.CREATURE, 8.0F), 1),
                Pair.of(SetEntityLookTarget.create(MobCategory.WATER_CREATURE, 8.0F), 1),
                Pair.of(SetEntityLookTarget.create(MobCategory.AXOLOTLS, 8.0F), 1),
                Pair.of(SetEntityLookTarget.create(MobCategory.UNDERGROUND_WATER_CREATURE, 8.0F), 1),
                Pair.of(SetEntityLookTarget.create(MobCategory.WATER_AMBIENT, 8.0F), 1),
                Pair.of(SetEntityLookTarget.create(MobCategory.MONSTER, 8.0F), 1),
                Pair.of(new DoNothing(30, 60), 2))));
    }
}
