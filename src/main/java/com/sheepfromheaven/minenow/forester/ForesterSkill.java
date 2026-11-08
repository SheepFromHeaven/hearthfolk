package com.sheepfromheaven.minenow.forester;

/** What a forester's level improves: how far from its nursery its grove reaches. */
public final class ForesterSkill {
    private static final int[] GROVE_RANGE = {8, 10, 12, 14, 16};

    private ForesterSkill() {}

    public static int groveRange(int level) {
        return GROVE_RANGE[Math.clamp(level, 1, GROVE_RANGE.length) - 1];
    }
}
