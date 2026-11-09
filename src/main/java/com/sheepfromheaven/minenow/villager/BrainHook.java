package com.sheepfromheaven.minenow.villager;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Pair;
import com.sheepfromheaven.minenow.farmer.TendFields;
import com.sheepfromheaven.minenow.fisherman.GoFishing;
import com.sheepfromheaven.minenow.shepherd.ShearSheep;
import com.sheepfromheaven.minenow.butcher.TendHerd;
import com.sheepfromheaven.minenow.miner.DigQuarry;
import com.sheepfromheaven.minenow.miner.LeaveQuarry;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public final class BrainHook {
    // Villager#refreshBrain (profession change, growing up, loading from disk) replaces the whole
    // Brain instance, so we tag brains by identity and re-augment whenever a fresh one appears.
    private static final Set<Brain<?>> AUGMENTED = Collections.newSetFromMap(new WeakHashMap<>());

    private BrainHook() {}

    private static boolean employed(Villager villager) {
        var profession = villager.getVillagerData().profession();
        return !profession.is(VillagerProfession.NONE) && !profession.is(VillagerProfession.NITWIT);
    }

    public static void onEntityTick(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof Villager villager) || villager.level().isClientSide() || villager.isBaby()) return;
        Brain<Villager> brain = villager.getBrain();
        if (!AUGMENTED.add(brain)) return;
        VillagerBrains.rebuild(brain, villager);
        if (villager.level() instanceof net.minecraft.server.level.ServerLevel level && employed(villager)) Economy.grantStartingPurseOnce(level, villager);
        // TendFields sits in CORE right after vanilla's MoveToTargetSink (same priority, added later), so each tick it
        // runs after the walk target was cleared on arrival and before WORK's walk-back-to-job-site can claim it.
        brain.addActivity(
                Activity.CORE,
                ImmutableList.of(Pair.of(1, new TendFields()), Pair.of(1, new GoFishing()), Pair.of(1, new ShearSheep()), Pair.of(1, new TendHerd()), Pair.of(1, new DigQuarry()), Pair.of(1, new com.sheepfromheaven.minenow.forester.FellTrees()), Pair.of(3, new com.sheepfromheaven.minenow.fletcher.PracticeArchery()), Pair.of(1, new LeaveQuarry()), Pair.of(1, new CraftAtWorkstation()), Pair.of(2, new EatMeals()), Pair.of(2, new BuyFromVillagers()), Pair.of(1, new OpenFenceGates()), Pair.of(1, new FollowEmeraldHolder()), Pair.of(6, new PickUpGiftedTool()),
                        Pair.of(10, new ChooseMeetingPoint())),
                ImmutableSet.of(),
                Set.of());
        brain.addActivity(Activity.IDLE, ImmutableList.of(Pair.of(1, new StrollAlongRoads())), ImmutableSet.of(), Set.of());
    }
}
