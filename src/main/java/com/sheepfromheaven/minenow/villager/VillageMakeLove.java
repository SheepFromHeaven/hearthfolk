package com.sheepfromheaven.minenow.villager;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.VillagerMakeLove;
import net.minecraft.world.entity.npc.villager.Villager;

import java.util.Optional;

/** Vanilla's mating, with the baby's bed taken from anywhere in the village (see {@link VillageBeds}). */
public final class VillageMakeLove extends VillagerMakeLove {
    @Override
    public Optional<BlockPos> takeVacantBed(ServerLevel level, Villager villager) {
        return VillageBeds.take(level, villager);
    }
}
