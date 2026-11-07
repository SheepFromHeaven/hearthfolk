package com.sheepfromheaven.minenow.farmer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A farmer's field is the ground connected to its composter: farmland, plus tillable ground (vanilla's
 * {@code turns_into_farmland} tag) within vanilla's 4-block watering range, joined through the water itself, up to
 * {@link #radiusFor} blocks from the composter.
 * Water is the boundary the player builds with, so a composter on a meadow doesn't claim the whole meadow.
 */
final class Field {
    private static final int[] RADIUS_BY_LEVEL = {10, 13, 16, 20, 24};
    static final int HEIGHT = 3;
    private static final int WATER_RANGE = 4;

    /** How far from the composter a field may reach, by villager level (novice 10 ... master 24). */
    static int radiusFor(int villagerLevel) {
        return RADIUS_BY_LEVEL[Math.clamp(villagerLevel, 1, RADIUS_BY_LEVEL.length) - 1];
    }

    private Field() {}

    /** Ground blocks of the field; the farmer works the block above each. */
    static List<BlockPos> ground(ServerLevel level, BlockPos composter, int radius) {
        int maxGround = (2 * radius + 1) * (2 * radius + 1);
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        List<BlockPos> ground = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -1; dy <= 0; dy++) {
                    if (dx != 0 || dz != 0) visit(level, composter, radius, composter.offset(dx, dy, dz), visited, queue, ground);
                }
            }
        }
        while (!queue.isEmpty() && ground.size() < maxGround) {
            BlockPos current = queue.poll();
            for (Direction side : Direction.Plane.HORIZONTAL) {
                for (int dy = -1; dy <= 1; dy++) {
                    visit(level, composter, radius, current.relative(side).above(dy), visited, queue, ground);
                }
            }
        }
        return ground;
    }

    private static void visit(ServerLevel level, BlockPos composter, int radius, BlockPos pos, Set<BlockPos> visited,
                              ArrayDeque<BlockPos> queue, List<BlockPos> ground) {
        if (Math.abs(pos.getX() - composter.getX()) > radius || Math.abs(pos.getZ() - composter.getZ()) > radius
                || Math.abs(pos.getY() - composter.getY()) > HEIGHT || !visited.add(pos) || !isOpenAbove(level, pos)) {
            return;
        }
        if (isWater(level, pos)) {
            queue.add(pos);
        } else if (isFarmland(level.getBlockState(pos)) || isTillable(level, pos)) {
            queue.add(pos);
            ground.add(pos);
        }
    }

    static boolean isFarmland(BlockState state) {
        return state.getBlock() instanceof FarmlandBlock || state.is(Tags.Blocks.VILLAGER_FARMLANDS);
    }

    static boolean isTillable(ServerLevel level, BlockPos ground) {
        return level.getBlockState(ground).is(BlockTags.TURNS_INTO_FARMLAND) && isNearWater(level, ground);
    }

    /** Short grass, flowers and the like are cleared when tilling; anything else on top blocks the ground. */
    static boolean isClearable(BlockState above) {
        return above.isAir() || (above.canBeReplaced() && above.getFluidState().isEmpty() && !(above.getBlock() instanceof CropBlock));
    }

    private static boolean isOpenAbove(ServerLevel level, BlockPos pos) {
        BlockState above = level.getBlockState(pos.above());
        return isClearable(above) || above.getBlock() instanceof CropBlock;
    }

    private static boolean isWater(ServerLevel level, BlockPos pos) {
        return level.getFluidState(pos).is(FluidTags.WATER);
    }

    private static boolean isNearWater(ServerLevel level, BlockPos ground) {
        for (BlockPos pos : BlockPos.betweenClosed(ground.offset(-WATER_RANGE, 0, -WATER_RANGE), ground.offset(WATER_RANGE, 1, WATER_RANGE))) {
            if (isWater(level, pos)) return true;
        }
        return false;
    }
}
