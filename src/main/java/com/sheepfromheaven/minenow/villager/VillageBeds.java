package com.sheepfromheaven.minenow.villager;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;

import java.util.Optional;

public final class VillageBeds {
    public static final int NEAR = 48;

    private VillageBeds() {}

    /** Takes a free bed inside the villager's village border or up to {@link Villages#SEARCH} blocks outside it, nearest to the villager first; without a village, one within {@link #NEAR} blocks. */
    public static Optional<BlockPos> take(ServerLevel level, Villager villager) {
        PoiManager pois = level.getPoiManager();
        Optional<Villages.Village> village = Villages.of(level, villager);
        if (village.isEmpty()) {
            return pois.take(type -> type.is(PoiTypes.HOME), (type, pos) -> true, villager.blockPosition(), NEAR);
        }
        for (BlockPos pos : village.get().pois(level, type -> type.is(PoiTypes.HOME), PoiManager.Occupancy.HAS_SPACE, villager.blockPosition())) {
            Optional<BlockPos> bed = pois.take(type -> type.is(PoiTypes.HOME), (type, at) -> at.equals(pos), pos, 1);
            if (bed.isPresent()) return bed;
        }
        return Optional.empty();
    }

    public static boolean anyFree(ServerLevel level, Villager villager) {
        PoiManager pois = level.getPoiManager();
        return Villages.of(level, villager)
                .map(village -> !village.pois(level, type -> type.is(PoiTypes.HOME), PoiManager.Occupancy.HAS_SPACE, villager.blockPosition()).isEmpty())
                .orElseGet(() -> pois.getCountInRange(type -> type.is(PoiTypes.HOME), villager.blockPosition(), NEAR, PoiManager.Occupancy.HAS_SPACE) > 0);
    }
}
