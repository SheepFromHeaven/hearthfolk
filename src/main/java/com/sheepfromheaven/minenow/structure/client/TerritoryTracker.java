package com.sheepfromheaven.minenow.structure.client;

import net.minecraft.core.BlockPos;

import java.util.HashSet;
import java.util.Set;

/**
 * Client-side toggle for whether a structure marker draws its placement {@link
 * com.sheepfromheaven.minenow.structure.Territory} box (where work-area markers may be placed). Mirrors
 * {@link com.sheepfromheaven.minenow.pathfinding.client.RangeTracker}.
 */
public class TerritoryTracker {
    private static final Set<BlockPos> showing = new HashSet<>();

    public static boolean isShowing(BlockPos pos) {
        return showing.contains(pos);
    }

    public static void toggle(BlockPos pos) {
        if (!showing.remove(pos)) {
            showing.add(pos);
        }
    }

    public static void clear() {
        showing.clear();
    }
}
