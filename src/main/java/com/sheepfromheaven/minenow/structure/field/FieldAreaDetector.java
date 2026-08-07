package com.sheepfromheaven.minenow.structure.field;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

/**
 * Flood-fills contiguous farmland tiles connected to a field marker, the same way
 * {@link com.sheepfromheaven.minenow.structure.RoomDetector} flood-fills an enclosed room - except
 * horizontal-only (fields are flat) and following farmland rather than open air.
 */
public final class FieldAreaDetector {
    private FieldAreaDetector() {}

    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

    public sealed interface Result permits Result.Success, Result.TooLarge, Result.NoFarmland {
        record Success(Set<BlockPos> blocks) implements Result {}
        record TooLarge(Set<BlockPos> blocks) implements Result {}
        record NoFarmland() implements Result {}
    }

    public static Result detect(Level level, BlockPos start, int maxBlocks) {
        if (!level.getBlockState(start).is(Blocks.FARMLAND)) {
            return new Result.NoFarmland();
        }
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            var current = queue.poll();
            if (visited.size() > maxBlocks) {
                return new Result.TooLarge(visited);
            }
            for (var dir : HORIZONTAL) {
                var neighbor = current.relative(dir);
                if (visited.contains(neighbor)) continue;
                if (level.getBlockState(neighbor).is(Blocks.FARMLAND)) {
                    visited.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }
        return new Result.Success(visited);
    }
}
