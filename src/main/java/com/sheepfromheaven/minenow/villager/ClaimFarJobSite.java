package com.sheepfromheaven.minenow.villager;

import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.npc.villager.Villager;

import java.util.Optional;

/**
 * Vanilla's {@code AcquirePoi} looks for a free job block within 48 blocks but only takes one it can plan a path to, and
 * a villager plans no path further than its 16-block follow range, so a villager whose job block was moved never found
 * a free one across the village until it was carried closer. Every 5 s a villager with no job block (and none on its
 * way) claims the nearest free one of its profession beyond that range within {@link Villages#SEARCH} blocks of its own village's border, however far the villager stands from it
 * ({@link Villages.Village#contains}), the same way vanilla does: it takes the point of interest's ticket and sets
 * {@code POTENTIAL_JOB_SITE}, which vanilla's {@code GoToPotentialJobSite} walks to (in partial paths, like {@link WalkToPoi})
 * and {@code AssignProfessionFromJobSite} turns into the job site on arrival. A block it claimed and didn't get to is
 * left alone for {@link #RETRY_AFTER} ticks, so an unreachable one isn't walked to again and again.
 */
public class ClaimFarJobSite extends Behavior<Villager> {
    static final double NEAR = 16.0;
    static final int INTERVAL = 100;
    static final long RETRY_AFTER = 12000;

    private BlockPos candidate;
    private BlockPos lastClaim;
    private long lastClaimAt;

    public ClaimFarJobSite() {
        super(ImmutableMap.of(
                MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.POTENTIAL_JOB_SITE, MemoryStatus.VALUE_ABSENT));
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager body) {
        if (body.isBaby() || (level.getGameTime() + body.getId()) % INTERVAL != 0) return false;
        Optional<Villages.Village> village = Villages.of(level, body);
        if (village.isEmpty()) return false;
        var acquirable = body.getVillagerData().profession().value().acquirableJobSite();
        long now = level.getGameTime();
        candidate = village.get().pois(level, acquirable, PoiManager.Occupancy.HAS_SPACE, body.blockPosition()).stream()
                .filter(pos -> !pos.closerToCenterThan(body.position(), NEAR))
                .filter(pos -> !pos.equals(lastClaim) || now - lastClaimAt > RETRY_AFTER)
                .findFirst().orElse(null);
        return candidate != null;
    }

    @Override
    protected void start(ServerLevel level, Villager body, long timestamp) {
        BlockPos pos = candidate;
        var acquirable = body.getVillagerData().profession().value().acquirableJobSite();
        level.getPoiManager().take(acquirable, (type, at) -> at.equals(pos), pos, 1).ifPresent(taken -> {
            body.getBrain().setMemory(MemoryModuleType.POTENTIAL_JOB_SITE, GlobalPos.of(level.dimension(), pos));
            lastClaim = pos;
            lastClaimAt = timestamp;
        });
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager body, long timestamp) {
        return false;
    }
}
