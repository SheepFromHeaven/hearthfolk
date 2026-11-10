package com.sheepfromheaven.minenow.mason;

/** What a mason's level improves: how far around its stonecutter it paves in its free time (not at all as a novice), and with what. */
public final class MasonSkill {
    private static final int[] WORKSHOP_RANGE = {0, 12, 14, 16, 20};

    private MasonSkill() {}

    public static int workshopRange(int level) {
        return WORKSHOP_RANGE[Math.clamp(level, 1, WORKSHOP_RANGE.length) - 1];
    }

    /** From journeyman on it lays stone bricks and slabs, not just cobblestone and gravel. */
    public static boolean laysStone(int level) {
        return level >= 3;
    }
}
