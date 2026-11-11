package com.sheepfromheaven.minenow.librarian;

/** What a librarian's level improves: how strong an enchantment it can bind (an enchanting table's cost level) and which ones. */
public final class LibrarianSkill {
    private static final int[] POWER = {5, 10, 15, 22, 30};

    private LibrarianSkill() {}

    public static int power(int level) {
        return POWER[Math.clamp(level, 1, POWER.length) - 1];
    }

    /** From expert on, the books may hold anything traders sell (mending, frost walker), not just what an enchanting table gives. */
    public static boolean bindsTradeableEnchantments(int level) {
        return level >= 4;
    }
}
