package com.sheepfromheaven.minenow.dominion;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import java.util.*;

public class RoomDetector {

    public sealed interface Result permits Result.Success, Result.TooLarge, Result.NotEnclosed {
        Set<BlockPos> visitedBlocks();
        record Success(Set<BlockPos> blocks) implements Result {
            @Override public Set<BlockPos> visitedBlocks() { return blocks; }
        }
        record TooLarge(Set<BlockPos> visitedBlocks) implements Result {}
        record NotEnclosed(Set<BlockPos> visitedBlocks) implements Result {}
    }

    public static Result detect(Level level, BlockPos start, int maxBlocks) {
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();

            if (visited.size() > maxBlocks) {
                return new Result.TooLarge(visited);
            }

            if (!level.getWorldBorder().isWithinBounds(current)) {
                return new Result.NotEnclosed(visited);
            }

            if (current.getY() >= level.getMaxY() || current.getY() <= level.getMinY()) {
                return new Result.NotEnclosed(visited);
            }

            for (Direction dir : Direction.values()) {
                BlockPos neighbor = current.relative(dir);
                if (visited.contains(neighbor)) continue;

                var state = level.getBlockState(neighbor);
                if (state.getCollisionShape(level, neighbor).isEmpty()) {
                    visited.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }

        return new Result.Success(visited);
    }
}
