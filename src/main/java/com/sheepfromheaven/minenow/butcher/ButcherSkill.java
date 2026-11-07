package com.sheepfromheaven.minenow.butcher;

/**
 * What a butcher gets better at with each level: cleaner cuts, up to {@link #extraCuts} more of each drop (like
 * Looting). Like every craftsman it also spares its axe ({@link com.sheepfromheaven.minenow.villager.ToolCare}).
 */
final class ButcherSkill {
    private static final int[] EXTRA_CUTS = {0, 0, 1, 1, 2};

    private ButcherSkill() {}

    static int extraCuts(int villagerLevel) {
        return EXTRA_CUTS[Math.clamp(villagerLevel, 1, 5) - 1];
    }
}
