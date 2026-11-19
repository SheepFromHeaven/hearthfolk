package com.sheepfromheaven.minenow.villager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Rain and roofs. A spot is wet when rain reaches it (vanilla's {@code isRainingAt}: open to the sky in a biome with rain,
 * so any block overhead keeps it dry); a shelter is a dry cell a villager can stand in. In the rain a villager works
 * outdoors only on a short trip: the work spot has to lie within {@link #QUICK_TRIP} blocks of a shelter, or be dry
 * itself ({@link #allows}), so a roof beside the pen, the field or the water makes the job work in any weather and a bare
 * job block doesn't.
 */
public final class RainShelter {
    /** How far from a roof outdoor work may be in the rain. */
    public static final int QUICK_TRIP = 8;
    private static final int HEIGHT = 3;
    private static final int CACHE_TICKS = 100;

    private record Verdict(boolean allowed, long until) {}

    private static final Map<Long, Verdict> VERDICTS = new HashMap<>();

    private RainShelter() {}

    /** Rain is falling on {@code pos}. */
    public static boolean wet(ServerLevel level, BlockPos pos) {
        return level.isRaining() && level.isRainingAt(pos);
    }

    /**
     * Whether a villager may work at {@code pos} now: always when it isn't raining there, otherwise only within
     * {@link #QUICK_TRIP} blocks of a shelter. Asked per work target, so remembered for a few seconds per 4-block cell.
     */
    public static boolean allows(ServerLevel level, BlockPos pos) {
        if (!wet(level, pos)) return true;
        long now = level.getGameTime();
        long key = BlockPos.asLong(pos.getX() >> 2, pos.getY() >> 2, pos.getZ() >> 2) * 31 + level.dimension().identifier().hashCode();
        Verdict verdict = VERDICTS.get(key);
        if (verdict == null || verdict.until() < now) {
            if (VERDICTS.size() > 4096) VERDICTS.clear();
            verdict = new Verdict(nearest(level, pos, QUICK_TRIP).isPresent(), now + CACHE_TICKS);
            VERDICTS.put(key, verdict);
        }
        return verdict.allowed();
    }

    /** The shelter nearest to {@code around} within {@code radius} blocks: a dry cell with room to stand and a floor under it. */
    public static Optional<BlockPos> nearest(ServerLevel level, BlockPos around, int radius) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        BlockPos.MutableBlockPos cell = new BlockPos.MutableBlockPos();
        for (int x = around.getX() - radius; x <= around.getX() + radius; x++) {
            for (int z = around.getZ() - radius; z <= around.getZ() + radius; z++) {
                if (!level.hasChunk(x >> 4, z >> 4)) continue;
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                for (int y = around.getY() - HEIGHT; y <= around.getY() + HEIGHT; y++) {
                    if (y >= top) break;
                    cell.set(x, y, z);
                    if (!standable(level, cell) || wet(level, cell)) continue;
                    double distance = cell.distSqr(around);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = cell.immutable();
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private static boolean standable(ServerLevel level, BlockPos feet) {
        return level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir()
                && level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP);
    }
}
