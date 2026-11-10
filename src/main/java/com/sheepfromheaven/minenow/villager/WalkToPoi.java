package com.sheepfromheaven.minenow.villager;

import com.google.common.collect.ImmutableMap;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * Vanilla's walk back to a job site, bed or meeting point ({@code SetWalkTargetFromBlockMemory}), without its two ways
 * of losing it on a walk across the village: vanilla gives the place up once {@code CANT_REACH_WALK_TARGET_SINCE} is
 * older than its limit, but any walk sets that, and a walk to anything beyond a villager's 16-block follow range only
 * ever gets a partial path; and beyond 100 or 150 blocks it gives the place up outright. Here only failing to reach the
 * place itself, from within {@link #NEAR} of it where a path could get there, counts, for as long as vanilla allows: a
 * streak of failed walks lasts while that timestamp stays the same, so whatever clears it (a miner down its mine) ends
 * the streak as it would in vanilla. The job site is {@link #keeping kept} however long it's out of reach, since a
 * village can be far bigger than vanilla's. Any of them is given up once its block is gone, wherever the villager is
 * (vanilla only notices within 16 blocks).
 */
public class WalkToPoi extends Behavior<Villager> {
    static final double NEAR = 16.0;

    private final MemoryModuleType<GlobalPos> memory;
    private final float speed;
    private final int closeEnough;
    private final int giveUpAfter;
    private boolean walking;
    private long walkStarted;
    private long failingSince = -1;
    private long lastSince = Long.MIN_VALUE;

    /** Never gives the place up for being out of reach, only once its block is gone ({@code giveUpAfter} unused). */
    public static WalkToPoi keeping(MemoryModuleType<GlobalPos> memory, float speed, int closeEnough) {
        return new WalkToPoi(memory, speed, closeEnough, -1);
    }

    public WalkToPoi(MemoryModuleType<GlobalPos> memory, float speed, int closeEnough, int giveUpAfter) {
        super(ImmutableMap.of(
                MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, MemoryStatus.REGISTERED,
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT,
                memory, MemoryStatus.VALUE_PRESENT));
        this.memory = memory;
        this.speed = speed;
        this.closeEnough = closeEnough;
        this.giveUpAfter = giveUpAfter;
    }

    @Override
    protected void start(ServerLevel level, Villager body, long timestamp) {
        var brain = body.getBrain();
        GlobalPos target = brain.getMemory(memory).orElseThrow();
        if (target.dimension() != level.dimension() || level.isLoaded(target.pos()) && !level.getPoiManager().exists(target.pos(), type -> true)) {
            giveUp(body, timestamp);
            return;
        }
        boolean near = target.pos().closerToCenterThan(body.position(), NEAR);
        if (walking) {
            var since = brain.getMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
            if (since.isPresent() && near) {
                if (failingSince < 0 || since.get() != lastSince) failingSince = Math.max(since.get(), walkStarted);
                lastSince = since.get();
            } else {
                failingSince = -1;
            }
        }
        walking = false;
        if (giveUpAfter >= 0 && failingSince >= 0 && timestamp - failingSince > giveUpAfter) {
            giveUp(body, timestamp);
            return;
        }
        if (target.pos().distManhattan(body.blockPosition()) > closeEnough) {
            brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(target.pos(), speed, closeEnough));
            walking = true;
            walkStarted = timestamp;
        } else {
            failingSince = -1;
        }
    }

    private void giveUp(Villager body, long timestamp) {
        body.releasePoi(memory);
        body.getBrain().eraseMemory(memory);
        body.getBrain().setMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, timestamp);
        walking = false;
        failingSince = -1;
    }
}
