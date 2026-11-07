package com.sheepfromheaven.minenow.butcher;

/**
 * What a butcher gets better at with each level: cleaner cuts, up to {@link #extraCuts} more of each drop (like
 * Looting). Better axes, which higher levels can buy, make the work itself quicker ({@link Butchering}).
 */
final class ButcherSkill {
    private static final int[] EXTRA_CUTS = {0, 0, 1, 1, 2};

    private ButcherSkill() {}

    static int extraCuts(int villagerLevel) {
        return EXTRA_CUTS[Math.clamp(villagerLevel, 1, 5) - 1];
    }
}
