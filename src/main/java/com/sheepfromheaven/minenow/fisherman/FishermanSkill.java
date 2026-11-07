package com.sheepfromheaven.minenow.fisherman;

/**
 * What a fisherman gets better at with each level: it reacts to a bite faster (a novice sometimes lets the fish get
 * away, a master never does) and, like every craftsman, handles its rod with more care ({@link com.sheepfromheaven.minenow.villager.ToolCare}).
 * Water is water wherever it is, so its range never grows.
 */
final class FishermanSkill {
    private static final int[] FASTEST_REACTION = {6, 5, 4, 3, 2};
    private static final int[] SLOWEST_REACTION = {36, 28, 22, 16, 10};

    private FishermanSkill() {}

    static int fastestReaction(int villagerLevel) {
        return FASTEST_REACTION[index(villagerLevel)];
    }

    static int slowestReaction(int villagerLevel) {
        return SLOWEST_REACTION[index(villagerLevel)];
    }

    private static int index(int villagerLevel) {
        return Math.clamp(villagerLevel, 1, 5) - 1;
    }
}
