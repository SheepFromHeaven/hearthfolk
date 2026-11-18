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

    /** Takes a free bed within {@link #NEAR} blocks of the villager, else one anywhere in its village, nearest to its places first. */
    public static Optional<BlockPos> take(ServerLevel level, Villager villager) {
        PoiManager pois = level.getPoiManager();
        Optional<BlockPos> near = pois.take(type -> type.is(PoiTypes.HOME), (type, pos) -> true, villager.blockPosition(), NEAR);
        if (near.isPresent()) return near;
        Optional<Villages.Village> village = Villages.of(level, villager);
        if (village.isEmpty()) return Optional.empty();
        for (BlockPos anchor : village.get().anchors()) {
            Optional<BlockPos> bed = pois.take(type -> type.is(PoiTypes.HOME), (type, pos) -> village.get().contains(pos), anchor, NEAR);
            if (bed.isPresent()) return bed;
        }
        return Optional.empty();
    }

    public static boolean anyFree(ServerLevel level, Villager villager) {
        PoiManager pois = level.getPoiManager();
        if (pois.getCountInRange(type -> type.is(PoiTypes.HOME), villager.blockPosition(), NEAR, PoiManager.Occupancy.HAS_SPACE) > 0) return true;
        return Villages.of(level, villager).filter(village -> village.anchors().stream().anyMatch(anchor ->
                pois.getInRange(type -> type.is(PoiTypes.HOME), anchor, NEAR, PoiManager.Occupancy.HAS_SPACE)
                        .anyMatch(record -> village.contains(record.getPos())))).isPresent();
    }
}
