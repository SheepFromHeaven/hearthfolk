package com.sheepfromheaven.minenow.shepherd;

/**
 * What a shepherd gets better at with each level: shearing a sheep takes time, a master is three times as fast as a
 * novice, a novice tears some of the wool it shears while a master keeps all of it, and a better shepherd keeps a
 * bigger flock ({@link #flockSize}). Like every craftsman it also
 * spares its shears ({@link com.sheepfromheaven.minenow.villager.ToolCare}).
 */
final class ShepherdSkill {
    private static final int[] SHEAR_TICKS = {60, 50, 40, 30, 20};
    private static final float[] WOOL_LOSS_CHANCE = {0.4F, 0.3F, 0.2F, 0.1F, 0.0F};
    private static final int[] FLOCK_SIZE = {4, 6, 8, 10, 12};

    private ShepherdSkill() {}

    static int shearTicks(int villagerLevel) {
        return SHEAR_TICKS[index(villagerLevel)];
    }

    static float woolLossChance(int villagerLevel) {
        return WOOL_LOSS_CHANCE[index(villagerLevel)];
    }

    /** How many sheep, lambs included, a shepherd breeds its flock up to. */
    static int flockSize(int villagerLevel) {
        return FLOCK_SIZE[index(villagerLevel)];
    }

    private static int index(int villagerLevel) {
        return Math.clamp(villagerLevel, 1, 5) - 1;
    }
}
