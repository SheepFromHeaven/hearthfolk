package com.sheepfromheaven.minenow.villager;

/** Better craftsmen handle the tool of their trade with more care: each use is less likely to wear it. */
public final class ToolCare {
    private static final float[] WEAR_CHANCE = {1.0F, 0.85F, 0.7F, 0.55F, 0.4F};

    private ToolCare() {}

    public static float wearChance(int villagerLevel) {
        return WEAR_CHANCE[Math.clamp(villagerLevel, 1, 5) - 1];
    }
}
