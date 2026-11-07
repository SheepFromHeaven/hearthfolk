package com.sheepfromheaven.minenow.villager;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Pair;
import com.sheepfromheaven.minenow.farmer.PickUpGiftedHoe;
import com.sheepfromheaven.minenow.farmer.TendFields;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
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
        brain.addActivity(
                Activity.WORK,
                ImmutableList.of(Pair.of(1, new TendFields())),
                ImmutableSet.of(Pair.of(MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT)),
                Set.of());
        brain.addActivity(
                Activity.CORE,
                ImmutableList.of(Pair.of(6, new PickUpGiftedHoe())),
                ImmutableSet.of(),
                Set.of());
    }
}
