package com.sheepfromheaven.minenow.villager;

/** What a crafter's level improves: how long one craft takes at its workstation. */
public final class CraftSkill {
    private static final int[] TICKS = {100, 85, 70, 55, 40};

    private CraftSkill() {}

    public static int ticks(int level) {
        return TICKS[Math.clamp(level, 1, TICKS.length) - 1];
    }
}
