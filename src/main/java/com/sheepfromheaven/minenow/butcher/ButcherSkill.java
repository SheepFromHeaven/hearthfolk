package com.sheepfromheaven.minenow.butcher;

/**
 * What a butcher gets better at with each level: cleaner cuts, up to {@link #extraCuts} more of each drop (like
 * Looting), and a pen that reaches further from the smoker ({@link #penRange}). Better axes, which higher levels can buy, make the work itself quicker ({@link Butchering}).
 */
final class ButcherSkill {
    private static final int[] EXTRA_CUTS = {0, 1, 2, 3, 4};
    private static final int[] PEN_RANGE = {8, 16, 24, 32, 40};

    private ButcherSkill() {}

    static int penRange(int villagerLevel) {
        return PEN_RANGE[Math.clamp(villagerLevel, 1, 5) - 1];
    }

    static int extraCuts(int villagerLevel) {
        return EXTRA_CUTS[Math.clamp(villagerLevel, 1, 5) - 1];
    }
}
