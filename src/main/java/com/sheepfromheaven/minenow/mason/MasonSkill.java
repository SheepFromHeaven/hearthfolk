package com.sheepfromheaven.minenow.mason;

/** What a mason's level improves: how far from its stonecutter it paves roads (not at all as a novice), and with what. */
public final class MasonSkill {
    private static final int[] ROAD_RANGE = {0, 12, 16, 20, 24};

    private MasonSkill() {}

    public static int roadRange(int level) {
        return ROAD_RANGE[Math.clamp(level, 1, ROAD_RANGE.length) - 1];
    }

    /** From journeyman on it lays stone bricks and slabs, not just cobblestone and gravel. */
    public static boolean laysStone(int level) {
        return level >= 3;
    }
}
