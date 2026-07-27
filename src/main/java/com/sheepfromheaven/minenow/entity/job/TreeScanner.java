package com.sheepfromheaven.minenow.entity.job;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Distinguishes a naturally-grown tree trunk from a player-built log structure so the lumberjack
 * never tears down a house. A trunk only counts as a tree once a non-persistent (naturally decaying)
 * leaf is found beside it; any other adjacent block (planks, stone, a persistent decorative leaf, a
 * differently-oriented log) aborts the scan entirely rather than risking a false positive.
 */
public final class TreeScanner {
    private static final int MAX_TRUNK_HEIGHT = 32;

    private TreeScanner() {
    }

    public static Optional<List<BlockPos>> findTree(Level level, BlockPos rootPos) {
        var rootState = level.getBlockState(rootPos);
        if (!rootState.is(BlockTags.LOGS) || !isSoil(level.getBlockState(rootPos.below()))) {
            return Optional.empty();
        }
        var axis = rootState.getValue(BlockStateProperties.AXIS);

        var trunk = new ArrayList<BlockPos>();
        var confirmedTree = false;
        var cur = rootPos;

        for (int i = 0; i < MAX_TRUNK_HEIGHT; i++) {
            var state = level.getBlockState(cur);
            if (!state.is(BlockTags.LOGS)) {
                break;
            }
            trunk.add(cur);

            for (var dir : Direction.Plane.HORIZONTAL) {
                var neighborState = level.getBlockState(cur.relative(dir));
                if (neighborState.isAir()) {
                    continue;
                }
                if (neighborState.is(BlockTags.LOGS) && neighborState.getValue(BlockStateProperties.AXIS) == axis) {
                    continue;
                }
                if (neighborState.is(Blocks.BEEHIVE) || neighborState.is(Blocks.BEE_NEST)) {
                    continue;
                }
                if (neighborState.is(BlockTags.LEAVES) && !neighborState.getValue(LeavesBlock.PERSISTENT)) {
                    confirmedTree = true;
                    continue;
                }
                return Optional.empty();
            }

            cur = cur.above();
        }

        return confirmedTree ? Optional.of(trunk) : Optional.empty();
    }

    private static boolean isSoil(BlockState state) {
        return state.is(BlockTags.DIRT) || state.is(Blocks.GRASS_BLOCK);
    }
}
