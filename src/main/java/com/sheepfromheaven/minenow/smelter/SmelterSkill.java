package com.sheepfromheaven.minenow.smelter;

/** What a smelter's level improves: how many furnaces and blast furnaces beside its own it works at once. */
public final class SmelterSkill {
    private static final int[] EXTRA_FURNACES = {1, 1, 2, 3, 4};

    private SmelterSkill() {}

    public static int extraFurnaces(int level) {
        return EXTRA_FURNACES[Math.clamp(level, 1, EXTRA_FURNACES.length) - 1];
    }
}
