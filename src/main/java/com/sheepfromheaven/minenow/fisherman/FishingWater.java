package com.sheepfromheaven.minenow.fisherman;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A fisherman's water is the open water touching a side of its barrel (corners don't count) and all surface water
 * connected to it, within a range that grows with its level. It fishes from dry spots on that water's shore.
 */
final class FishingWater {
    private static final int[] RADIUS_BY_LEVEL = {8, 10, 12, 14, 16};
    private static final int HEIGHT = 3;

    record Spot(BlockPos feet, BlockPos water) {}

    private FishingWater() {}

    static int radiusFor(int villagerLevel) {
        return RADIUS_BY_LEVEL[Math.clamp(villagerLevel, 1, RADIUS_BY_LEVEL.length) - 1];
    }

    static List<BlockPos> water(ServerLevel level, BlockPos barrel, int radius) {
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        List<BlockPos> water = new ArrayList<>();
        for (Direction side : Direction.Plane.HORIZONTAL) {
            for (int dy = -1; dy <= 0; dy++) visit(level, barrel, radius, barrel.relative(side).above(dy), visited, queue, water);
        }
        while (!queue.isEmpty() && water.size() < (2 * radius + 1) * (2 * radius + 1)) {
            BlockPos current = queue.poll();
            for (Direction side : Direction.Plane.HORIZONTAL) visit(level, barrel, radius, current.relative(side), visited, queue, water);
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

    private static void visit(ServerLevel level, BlockPos barrel, int radius, BlockPos pos, Set<BlockPos> visited,
                              ArrayDeque<BlockPos> queue, List<BlockPos> water) {
        if (Math.abs(pos.getX() - barrel.getX()) > radius || Math.abs(pos.getZ() - barrel.getZ()) > radius
                || Math.abs(pos.getY() - barrel.getY()) > HEIGHT || !visited.add(pos) || !isOpenWater(level, pos)) {
            return;
        }
        queue.add(pos);
        water.add(pos);
    }
}
