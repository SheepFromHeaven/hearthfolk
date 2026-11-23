package com.sheepfromheaven.minenow.villager;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The outline of a village on the ground: a column is in it when it is within {@code reach} blocks across of a place
 * (the same circle {@link Villages.Village#contains} uses, whatever the height within its limit). Pure, so the client
 * can work the outline out from the places the server sends.
 */
public final class VillageBorder {
    /** A straight stretch of border along block corners, from (x1, z1) to (x2, z2); one of the two axes does not change. */
    public record Edge(int x1, int z1, int x2, int z2) {}

    private VillageBorder() {}

    public static boolean includes(Collection<BlockPos> anchors, int reach, int x, int z) {
        for (BlockPos anchor : anchors) {
            int dx = anchor.getX() - x;
            int dz = anchor.getZ() - z;
            if (dx * dx + dz * dz <= reach * reach) return true;
        }
        return false;
    }

    public static List<Edge> edges(Collection<BlockPos> anchors, int reach) {
        return edges(anchors, reach, (x, z) -> true);
    }

    /** The border of the areas around {@code extra} that lies outside the area around {@code village}: how far the village would grow. */
    public static List<Edge> growth(Collection<BlockPos> extra, Collection<BlockPos> village, int reach) {
        return edges(extra, reach, (x, z) -> !includes(village, reach, x, z));
    }

    private static List<Edge> edges(Collection<BlockPos> anchors, int reach, java.util.function.BiPredicate<Integer, Integer> keepInside) {
        if (anchors.isEmpty()) return List.of();
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos anchor : anchors) {
            minX = Math.min(minX, anchor.getX() - reach);
            maxX = Math.max(maxX, anchor.getX() + reach);
            minZ = Math.min(minZ, anchor.getZ() - reach);
            maxZ = Math.max(maxZ, anchor.getZ() + reach);
        }
        int originX = minX - 1;
        int originZ = minZ - 1;
        int width = maxX - minX + 3;
        int depth = maxZ - minZ + 3;
        boolean[][] in = new boolean[width][depth];
        for (BlockPos anchor : anchors) {
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    if (dx * dx + dz * dz > reach * reach) continue;
                    in[anchor.getX() + dx - minX + 1][anchor.getZ() + dz - minZ + 1] = true;
                }
            }
        }
        List<Edge> edges = new ArrayList<>();
        for (int x = 1; x < width; x++) {
            int runStart = -1;
            for (int z = 1; z <= depth; z++) {
                boolean border = z < depth && in[x][z] != in[x - 1][z]
                        && keepInside.test(originX + (in[x][z] ? x : x - 1), originZ + z);
                if (border && runStart < 0) runStart = z;
                if (!border && runStart >= 0) {
                    edges.add(new Edge(originX + x, originZ + runStart, originX + x, originZ + z));
                    runStart = -1;
                }
            }
        }
        for (int z = 1; z < depth; z++) {
            int runStart = -1;
            for (int x = 1; x <= width; x++) {
                boolean border = x < width && in[x][z] != in[x][z - 1]
                        && keepInside.test(originX + x, originZ + (in[x][z] ? z : z - 1));
                if (border && runStart < 0) runStart = x;
                if (!border && runStart >= 0) {
                    edges.add(new Edge(originX + runStart, originZ + z, originX + x, originZ + z));
                    runStart = -1;
                }
            }
        }
        return edges;
    }
}
