package com.sheepfromheaven.minenow.structure.client;

import net.minecraft.core.BlockPos;

import java.util.HashSet;
import java.util.Set;

public class WorkAreaTracker {
    private static final Set<BlockPos> showingWorkArea = new HashSet<>();

    public static boolean isShowingWorkArea(BlockPos pos) {
        return showingWorkArea.contains(pos);
    }

    public static void toggleWorkArea(BlockPos pos) {
        if (!showingWorkArea.remove(pos)) {
            showingWorkArea.add(pos);
        }
    }

    public static void clear() {
        showingWorkArea.clear();
    }
}
