package com.sheepfromheaven.minenow.client;

import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Set;

/** Client-side mirror of the path nodes the server has revealed to this player (see {@link PathNodeGizmoRenderer}). */
public class PathNodeTracker {
    private static Set<BlockPos> positions = Set.of();

    public static void set(List<BlockPos> newPositions) {
        positions = Set.copyOf(newPositions);
    }

    public static Set<BlockPos> get() {
        return positions;
    }
}
