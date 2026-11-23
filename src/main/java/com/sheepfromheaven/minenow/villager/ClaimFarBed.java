package com.sheepfromheaven.minenow.villager;

import com.google.common.collect.ImmutableMap;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * Vanilla's {@code AcquirePoi} gives an adult without a bed one within 48 blocks of itself that it can plan a path to,
 * which is never one beyond its 16-block follow range. Every 5 s an adult villager of a village without a {@code HOME}
 * takes a free bed from {@link VillageBeds#take}, inside the village's border or up to {@link Villages#SEARCH} blocks
 * outside it, and sets {@code HOME} at once as vanilla does; {@link WalkToPoi} walks it there.
 */
public class ClaimFarBed extends Behavior<Villager> {
    static final int INTERVAL = 100;

    public ClaimFarBed() {
        super(ImmutableMap.of(MemoryModuleType.HOME, MemoryStatus.VALUE_ABSENT));
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager body) {
        return !body.isBaby() && (level.getGameTime() + body.getId()) % INTERVAL == 0 && Villages.of(level, body).isPresent();
    }

    @Override
    protected void start(ServerLevel level, Villager body, long timestamp) {
        VillageBeds.take(level, body).ifPresent(bed -> body.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), bed)));
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager body, long timestamp) {
        return false;
    }
}
