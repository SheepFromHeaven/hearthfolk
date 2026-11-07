package com.sheepfromheaven.minenow.fisherman;

/**
 * What a fisherman gets better at with each level: it reacts to a bite faster (a novice sometimes lets the fish get
 * away, a master never does) and handles its rod with more care, so each catch is less likely to wear it.
 * Water is water wherever it is, so its range never grows.
 */
final class FishermanSkill {
    private static final int[] FASTEST_REACTION = {6, 5, 4, 3, 2};
    private static final int[] SLOWEST_REACTION = {36, 28, 22, 16, 10};
    private static final float[] ROD_WEAR_CHANCE = {1.0F, 0.85F, 0.7F, 0.55F, 0.4F};

    private FishermanSkill() {}

    static int fastestReaction(int villagerLevel) {
        return FASTEST_REACTION[index(villagerLevel)];
    }

    static int slowestReaction(int villagerLevel) {
        return SLOWEST_REACTION[index(villagerLevel)];
    }

    static float rodWearChance(int villagerLevel) {
        return ROD_WEAR_CHANCE[index(villagerLevel)];
    }

    private static int index(int villagerLevel) {
        return Math.clamp(villagerLevel, 1, 5) - 1;
    }
}
