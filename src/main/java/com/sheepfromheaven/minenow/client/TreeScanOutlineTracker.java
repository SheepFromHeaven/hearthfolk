package com.sheepfromheaven.minenow.client;

import net.minecraft.core.BlockPos;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/** Client-side mirror of the currently highlighted blocks from a Tree Scanner Wand scan (see TreeScannerDebugger). */
public class TreeScanOutlineTracker {
    public static final int CATEGORY_LOG = 0;
    public static final int CATEGORY_LEAF = 1;
    public static final int CATEGORY_BLOCKED = 2;

    private static final Set<BlockPos> logs = new HashSet<>();
    private static final Set<BlockPos> leaves = new HashSet<>();
    private static final Set<BlockPos> blocked = new HashSet<>();

    public static void clear() {
        logs.clear();
        leaves.clear();
        blocked.clear();
    }

    public static void add(int category, Collection<BlockPos> positions) {
        switch (category) {
            case CATEGORY_LOG -> logs.addAll(positions);
            case CATEGORY_LEAF -> leaves.addAll(positions);
            case CATEGORY_BLOCKED -> blocked.addAll(positions);
            default -> {}
        }
    }

    public static Set<BlockPos> getLogs() {
        return logs;
    }

    public static Set<BlockPos> getLeaves() {
        return leaves;
    }

    public static Set<BlockPos> getBlocked() {
        return blocked;
    }

    public static boolean isEmpty() {
        return logs.isEmpty() && leaves.isEmpty() && blocked.isEmpty();
    }
}
