package com.sheepfromheaven.minenow.cleric;

/** What a cleric's level improves besides the recipes it unlocks: how many bottles it loads into its stand for one brew. */
public final class ClericSkill {
    private static final int[] BOTTLES = {1, 2, 3, 3, 3};

    private ClericSkill() {}

    public static int bottles(int level) {
        return BOTTLES[Math.clamp(level, 1, BOTTLES.length) - 1];
    }
}
