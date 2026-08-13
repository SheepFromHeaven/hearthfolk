package com.sheepfromheaven.minenow.structure.field;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * Flood-fills the ground patch connected to a field marker like water spreading over terrain: it
 * doesn't need walls to bound it, it just spreads sideways at the marker's level and, wherever the
 * ground drops away, flows downward until it lands on solid footing again - stopping only where a
 * solid block actually blocks it. Every level the fill flows through on the way down is claimed too
 * (a deeper hole claims more tiles, the same way a deeper shaft holds more water), not just the final
 * landing. This no longer needs to be farmland already; tilling it into farmland is the farmer's job.
 */
public final class FieldAreaDetector {
    private FieldAreaDetector() {}

    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
    /** How many blocks the fill will follow a drop-off downward before giving up on that direction. */
    private static final int MAX_FALL = 32;

    public sealed interface Result permits Result.Success, Result.TooLarge, Result.NotEnclosed {
        record Success(Set<BlockPos> blocks) implements Result {}
        record TooLarge(Set<BlockPos> blocks) implements Result {}
        record NotEnclosed() implements Result {}
    }

    /**
     * @param markerPos the field marker's own position, one above {@code start} - its footprint is
     *                  treated as open space regardless of the marker block's actual collision shape,
     *                  since it's standing exactly where a farmer needs to be able to walk.
     */
    public static Result detect(Level level, BlockPos start, BlockPos markerPos, int maxBlocks) {
        if (!isWalkableGround(level, start, markerPos)) {
            return new Result.NotEnclosed();
        }
        Set<BlockPos> claimed = new HashSet<>();
        // Only landing tiles become new BFS frontiers - a farmer can't stand mid-fall to spread
        // sideways from there, so this tracks which landings have already been queued, separately
        // from `claimed` (which also holds the merely-passed-through tiles above each landing).
        Set<BlockPos> frontierSeen = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();
        claimed.add(start);
        frontierSeen.add(start);
        queue.add(start);

        while (!queue.isEmpty()) {
            var current = queue.poll();
            if (claimed.size() > maxBlocks) {
                return new Result.TooLarge(claimed);
            }
            if (!level.getWorldBorder().isWithinBounds(current)) {
                return new Result.NotEnclosed();
            }
            for (var dir : HORIZONTAL) {
                var path = fallPath(level, current.relative(dir), markerPos);
                if (path == null) continue;
                claimed.addAll(path);
                var landing = path.get(path.size() - 1);
                if (frontierSeen.add(landing)) {
                    queue.add(landing);
                }
            }
        }
        return new Result.Success(claimed);
    }

    /**
     * From {@code columnPos} at the current flood-fill level, either lands immediately if it's
     * already walkable ground (the common flat-floor case), or - like water flowing off a ledge -
     * follows the open space downward until it lands on solid, walkable ground. Returns every tile
     * passed through along the way (in fall order, ending with the landing), or null if a solid block
     * occupies {@code columnPos} itself without open space above it (a wall blocking sideways spread
     * at this level) or if it falls more than {@link #MAX_FALL} blocks without finding footing.
     */
    @Nullable
    private static List<BlockPos> fallPath(Level level, BlockPos columnPos, BlockPos markerPos) {
        var path = new ArrayList<BlockPos>();
        for (int fall = 0; fall <= MAX_FALL; fall++) {
            var candidate = columnPos.below(fall);
            if (candidate.getY() <= level.getMinY()) {
                return null;
            }
            if (isWalkableGround(level, candidate, markerPos)) {
                path.add(candidate);
                return path;
            }
            if (!isOpen(level, candidate, markerPos)) {
                return null; // A solid block with no headroom - a wall (fall 0) or an obstruction while falling.
            }
            path.add(candidate);
        }
        return null;
    }

    /** True if {@code pos} has solid footing and open (walkable) space directly above it. */
    private static boolean isWalkableGround(Level level, BlockPos pos, BlockPos markerPos) {
        if (level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
            return false;
        }
        return isOpen(level, pos.above(), markerPos);
    }

    /** True if {@code pos} is passable - either genuinely open, or the marker's own footprint. */
    private static boolean isOpen(Level level, BlockPos pos, BlockPos markerPos) {
        return pos.equals(markerPos) || level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }
}
