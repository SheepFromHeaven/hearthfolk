package com.sheepfromheaven.minenow.golemsmith;

/** What a golemsmith's level improves: from journeyman it builds golems, and the village holds more of them the better it gets. */
public final class GolemsmithSkill {
    private static final int BUILDS_FROM = 3;
    private static final int[] MAX_GOLEMS = {0, 0, 2, 3, 4};

    private GolemsmithSkill() {}

    public static boolean buildsGolems(int level) {
        return level >= BUILDS_FROM;
    }

    public static int maxGolems(int level) {
        return MAX_GOLEMS[Math.clamp(level, 1, MAX_GOLEMS.length) - 1];
    }

    /** An iron ingot mends a quarter of a golem's health, so it's spent only when that much is missing. */
    public static boolean worthRepairing(float missing, float maxHealth) {
        return missing * 4 >= maxHealth;
    }
}
