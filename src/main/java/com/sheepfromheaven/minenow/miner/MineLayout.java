package com.sheepfromheaven.minenow.miner;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The shape of a mine below its marked outline: every {@link #PLATFORM_EVERY}th layer is a platform that keeps a ring
 * {@link #RING} blocks wide (except the ladder hole) and opens the middle, with the shaft continuing below; from each
 * platform a 3x3 tunnel runs out from the middle three columns of each of the four walls.
 */
final class MineLayout {
    static final int PLATFORM_EVERY = 5;
    static final int RING = 2;
    static final int TUNNEL_HEIGHT = 3;
    /** A tunnel needs a wall at least this long to leave room beside it for the ladder. */
    static final int MIN_SIDE = 5;

    private final Set<BlockPos> outline;
    private final int top;
    private final BlockPos ladder;
    private final int minX;
    private final int maxX;
    private final int minZ;
    private final int maxZ;

    MineLayout(Set<BlockPos> outline, int top, BlockPos ladder) {
        this.outline = outline;
        this.top = top;
        this.ladder = ladder.atY(top);
        this.minX = outline.stream().mapToInt(BlockPos::getX).min().orElse(0);
        this.maxX = outline.stream().mapToInt(BlockPos::getX).max().orElse(0);
        this.minZ = outline.stream().mapToInt(BlockPos::getZ).min().orElse(0);
        this.maxZ = outline.stream().mapToInt(BlockPos::getZ).max().orElse(0);
    }

    boolean isPlatform(int y) {
        return y < top && (top - y) % PLATFORM_EVERY == 0;
    }

    /** Whether this shaft cell is dug: all of it, except a platform's ring (but never the ladder hole). */
    boolean digs(BlockPos pos) {
        BlockPos column = pos.atY(top);
        if (pos.getY() >= top || !outline.contains(column)) return false;
        return !isPlatform(pos.getY()) || column.equals(ladder) || !inRing(column);
    }

    List<BlockPos> tunnels(int platform, int length) {
        return tunnelLines(platform, length).stream().flatMap(List::stream).toList();
    }

    /** The tunnels at a platform as lines running outwards from the shaft wall, nearest cell first. */
    List<List<BlockPos>> tunnelLines(int platform, int length) {
        List<List<BlockPos>> lines = new ArrayList<>();
        int midX = (minX + maxX) / 2;
        int midZ = (minZ + maxZ) / 2;
        for (int y = platform + 1; y <= platform + TUNNEL_HEIGHT; y++) {
            for (int across = -1; across <= 1; across++) {
                List<BlockPos> east = new ArrayList<>();
                List<BlockPos> west = new ArrayList<>();
                List<BlockPos> north = new ArrayList<>();
                List<BlockPos> south = new ArrayList<>();
                for (int out = 1; out <= length; out++) {
                    east.add(new BlockPos(maxX + out, y, midZ + across));
                    west.add(new BlockPos(minX - out, y, midZ + across));
                    north.add(new BlockPos(midX + across, y, minZ - out));
                    south.add(new BlockPos(midX + across, y, maxZ + out));
                }
                if (maxZ - minZ + 1 >= MIN_SIDE) {
                    lines.add(east);
                    lines.add(west);
                }
                if (maxX - minX + 1 >= MIN_SIDE) {
                    lines.add(north);
                    lines.add(south);
                }
            }
        }
        return lines;
    }

    /** Whether {@code pos} lies in a tunnel of any platform from the top down to {@code bottom}. */
    boolean inTunnel(BlockPos pos, int bottom, int length) {
        int midX = (minX + maxX) / 2;
        int midZ = (minZ + maxZ) / 2;
        boolean inStrip = (maxZ - minZ + 1 >= MIN_SIDE && Math.abs(pos.getZ() - midZ) <= 1 && ((pos.getX() > maxX && pos.getX() <= maxX + length) || (pos.getX() < minX && pos.getX() >= minX - length)))
                || (maxX - minX + 1 >= MIN_SIDE && Math.abs(pos.getX() - midX) <= 1 && ((pos.getZ() > maxZ && pos.getZ() <= maxZ + length) || (pos.getZ() < minZ && pos.getZ() >= minZ - length)));
        if (!inStrip) return false;
        for (int platform = pos.getY() - TUNNEL_HEIGHT; platform < pos.getY(); platform++) {
            if (platform >= bottom && isPlatform(platform)) return true;
        }
        return false;
    }

    /** Whether the wall on {@code side} of this shaft cell is where a tunnel will be dug, so no ladder may hang on it. */
    boolean tunnelWall(BlockPos cell, Direction side) {
        int midX = (minX + maxX) / 2;
        int midZ = (minZ + maxZ) / 2;
        return side.getAxis() == Direction.Axis.X
                ? maxZ - minZ + 1 >= MIN_SIDE && Math.abs(cell.getZ() - midZ) <= 1
                : maxX - minX + 1 >= MIN_SIDE && Math.abs(cell.getX() - midX) <= 1;
    }

    private boolean inRing(BlockPos column) {
        for (int dx = -RING; dx <= RING; dx++) {
            for (int dz = -RING; dz <= RING; dz++) {
                if (!outline.contains(column.offset(dx, 0, dz))) return true;
            }
        }
        return false;
    }
}
