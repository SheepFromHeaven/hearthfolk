package com.sheepfromheaven.minenow.villager;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Pair;
import com.sheepfromheaven.minenow.farmer.FarmerRules;
import com.sheepfromheaven.minenow.farmer.FarmerWork;
import com.sheepfromheaven.minenow.farmer.PickUpGiftedHoe;
import com.sheepfromheaven.minenow.farmer.TendFields;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.npc.villager.Villager;
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

    public static void onEntityTick(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof Villager villager) || villager.level().isClientSide() || villager.isBaby()) return;
        Brain<Villager> brain = villager.getBrain();
        if (!AUGMENTED.add(brain)) return;
        if (FarmerRules.isFarmer(villager)) FarmerWork.replaceVanillaWork(brain, villager);
        // TendFields sits in CORE right after vanilla's MoveToTargetSink (same priority, added later), so each tick it
        // runs after the walk target was cleared on arrival and before WORK's walk-back-to-job-site can claim it.
        brain.addActivity(
                Activity.CORE,
                ImmutableList.of(Pair.of(1, new TendFields()), Pair.of(6, new PickUpGiftedHoe())),
                ImmutableSet.of(),
                Set.of());
        brain.addActivity(Activity.IDLE, ImmutableList.of(Pair.of(1, new StrollAlongRoads())), ImmutableSet.of(), Set.of());
    }
}
