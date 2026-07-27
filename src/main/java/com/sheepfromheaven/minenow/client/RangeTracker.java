package com.sheepfromheaven.minenow.client;

import net.minecraft.core.BlockPos;

import java.util.HashSet;
import java.util.Set;

public class RangeTracker {
    private static final Set<BlockPos> showingRange = new HashSet<>();

    public static boolean isShowingRange(BlockPos pos) {
        return showingRange.contains(pos);
    }

    public static void toggleRange(BlockPos pos) {
        if (!showingRange.remove(pos)) {
            showingRange.add(pos);
        }
    }

    public static void clear() {
        showingRange.clear();
    }
}
