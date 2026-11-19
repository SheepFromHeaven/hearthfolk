package com.sheepfromheaven.minenow.villager;

import com.google.common.collect.ImmutableMap;
import com.sheepfromheaven.minenow.MineNowConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import org.jspecify.annotations.Nullable;

/** Idle villagers now and then pick a nearby road block to stroll to, so they walk the streets rather than the fields. */
public class StrollAlongRoads extends Behavior<Villager> {
    private static final int RADIUS = 12;
    private static final int HEIGHT = 3;
    private static final int MIN_DISTANCE = 4;
    private static final int ATTEMPTS = 32;
    private static final float SPEED = 0.5F;

    private long nextStroll;
    private @Nullable BlockPos target;

    public StrollAlongRoads() {
        super(ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT));
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager body) {
        if (!MineNowConfig.enabled(MineNowConfig.Feature.VILLAGERS_PREFER_ROADS)) return false;
        if (level.getGameTime() < nextStroll) return false;
        RandomSource random = body.getRandom();
        nextStroll = level.getGameTime() + 60 + random.nextInt(120);
        target = findRoad(level, body, random);
        return target != null;
    }

    @Override
    protected void start(ServerLevel level, Villager body, long timestamp) {
        body.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(target, SPEED, 0));
    }

    private static @Nullable BlockPos findRoad(ServerLevel level, Villager body, RandomSource random) {
        BlockPos origin = body.blockPosition();
        for (int i = 0; i < ATTEMPTS; i++) {
            int dx = random.nextIntBetweenInclusive(-RADIUS, RADIUS);
            int dz = random.nextIntBetweenInclusive(-RADIUS, RADIUS);
            if (Math.abs(dx) < MIN_DISTANCE && Math.abs(dz) < MIN_DISTANCE) continue;
            for (int dy = HEIGHT; dy >= -HEIGHT; dy--) {
                BlockPos feet = origin.offset(dx, dy, dz);
                if (Roads.isStandableRoad(level, feet) && !RainShelter.wet(level, feet)) return feet;
            }
        }
        return null;
    }
}
