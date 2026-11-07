package com.sheepfromheaven.minenow.fisherman;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A fisherman's water is any open surface water within {@link #RANGE} blocks of its barrel, so a barrel can stand on a
 * dock or in a hut near the water. It fishes from dry spots on that water's shore.
 */
final class FishingWater {
    static final int RANGE = 8;
    private static final int HEIGHT = 3;

    record Spot(BlockPos feet, BlockPos water) {}

    private FishingWater() {}

    static List<BlockPos> water(ServerLevel level, BlockPos barrel) {
        List<BlockPos> water = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(barrel.offset(-RANGE, -HEIGHT, -RANGE), barrel.offset(RANGE, HEIGHT, RANGE))) {
            if (isOpenWater(level, pos)) water.add(pos.immutable());
        }
        return water;
    }

    static List<Spot> shore(ServerLevel level, List<BlockPos> water) {
        Set<BlockPos> waterSet = new HashSet<>(water);
        List<Spot> spots = new ArrayList<>();
        for (BlockPos pond : water) {
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos ground = pond.relative(side);
                if (waterSet.contains(ground)) continue;
                BlockPos feet = ground.above();
                if (isStandable(level, feet)) spots.add(new Spot(feet, pond));
            }
        }
        return spots;
    }

    static boolean isOpenWater(ServerLevel level, BlockPos pos) {
        return level.getFluidState(pos).is(FluidTags.WATER) && level.getBlockState(pos.above()).isAir();
    }

    private static boolean isStandable(ServerLevel level, BlockPos feet) {
        return level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP)
                && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
                && level.getFluidState(feet).isEmpty();
    }
}
