package com.sheepfromheaven.minenow.villager;

import com.google.common.collect.ImmutableMap;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.pathfinder.Path;

import java.util.List;
import java.util.Optional;

/**
 * Vanilla keeps a claimed bell until it becomes unreachable. Instead, each late afternoon a villager lets go of its
 * bell and claims the nearest one it can walk to from where it is (vanilla's 48-block range, within the bell's 6-block
 * reach), so villagers gather at different bells depending on where their day took them.
 */
public class ChooseMeetingPoint extends Behavior<Villager> {
    private static final int SEARCH_RANGE = 48;
    private static final int CANDIDATES = 5;
    private static final int CHECK_INTERVAL = 20;
    private static final long HALF_A_DAY = 12000L;

    private long lastChosen = Long.MIN_VALUE / 2;
    private long nextCheck;

    public ChooseMeetingPoint() {
        super(ImmutableMap.of());
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager body) {
        long now = level.getGameTime();
        if (now < nextCheck || body.isBaby() || body.isSleeping() || !body.onGround()) return false;
        nextCheck = now + CHECK_INTERVAL;
        return now - lastChosen > HALF_A_DAY
                && level.environmentAttributes().getValue(EnvironmentAttributes.VILLAGER_ACTIVITY, body.position()) == Activity.MEET;
    }

    @Override
    protected void start(ServerLevel level, Villager body, long timestamp) {
        lastChosen = timestamp;
        body.releasePoi(MemoryModuleType.MEETING_POINT);
        body.getBrain().eraseMemory(MemoryModuleType.MEETING_POINT);
        PoiManager pois = level.getPoiManager();
        List<BlockPos> bells = pois.findAllClosestFirstWithType(type -> type.is(PoiTypes.MEETING), pos -> true,
                        body.blockPosition(), SEARCH_RANGE, PoiManager.Occupancy.HAS_SPACE)
                .map(Pair::getSecond).limit(CANDIDATES).toList();
        for (BlockPos bell : bells) {
            Path path = body.getNavigation().createPath(bell, validRange(pois, bell));
            if (path == null || !path.canReach()) continue;
            Optional<BlockPos> taken = pois.take(type -> type.is(PoiTypes.MEETING), (type, pos) -> pos.equals(bell), bell, 1);
            if (taken.isPresent()) {
                body.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(level.dimension(), bell));
                return;
            }
        }
    }

    private static int validRange(PoiManager pois, BlockPos bell) {
        return pois.getType(bell).map(Holder::value).map(PoiType::validRange).orElse(6);
    }
}
