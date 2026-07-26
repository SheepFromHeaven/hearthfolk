package com.sheepfromheaven.minenow.client;

import net.minecraft.core.BlockPos;

import java.util.HashSet;
import java.util.Set;

public class DominionRangeTracker {
    private static final Set<BlockPos> showingRange = new HashSet<>();

    public static boolean isShowingRange(BlockPos bannerPos) {
        return showingRange.contains(bannerPos);
    }

    public static void toggleRange(BlockPos bannerPos) {
        if (!showingRange.remove(bannerPos)) {
            showingRange.add(bannerPos);
        }
    }

    public static void clear() {
        showingRange.clear();
    }
}
