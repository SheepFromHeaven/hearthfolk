package com.sheepfromheaven.minenow.entity.job;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import java.util.Optional;

/**
 * Finds a dry tile to fish from next to a water block, used by {@link FisherJob} - pulled out as its
 * own static utility (mirroring {@link TreeScanner}) so the shore search is directly testable without
 * driving a whole job through navigation.
 */
public final class ShoreScanner {
    private ShoreScanner() {
    }

    /**
     * Checks the ring level with the water first, then the ring one block above it - a small pond (e.g.
     * a well) is often sunk one block below its walkable rim, walled in laterally at the water's own
     * level, so only the rim ring is actually standable; without this second pass every direction at the
     * water's level reports blocked and the fallback below leaves the inhabitant fishing from inside the
     * water instead of beside it. Empty if {@code water} turns out to be an interior tile of a larger
     * body of water with no dry tile adjacent - callers should treat that water block as unusable rather
     * than walking the inhabitant onto it.
     */
    public static Optional<BlockPos> findShorePosition(Level level, BlockPos water) {
        for (var dir : Direction.Plane.HORIZONTAL) {
            var candidate = water.relative(dir);
            if (isStandableShore(level, candidate)) {
                return Optional.of(candidate);
            }
        }
        for (var dir : Direction.Plane.HORIZONTAL) {
            var candidate = water.above().relative(dir);
            if (isStandableShore(level, candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private static boolean isStandableShore(Level level, BlockPos pos) {
        var state = level.getBlockState(pos);
        if (!state.getFluidState().isEmpty() || !state.getCollisionShape(level, pos).isEmpty()) {
            return false;
        }
        var belowPos = pos.below();
        return !level.getBlockState(belowPos).getCollisionShape(level, belowPos).isEmpty();
    }
}
