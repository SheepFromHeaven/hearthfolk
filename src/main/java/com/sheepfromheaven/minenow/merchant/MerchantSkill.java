package com.sheepfromheaven.minenow.merchant;

/** What a merchant's level improves. */
public final class MerchantSkill {
    private MerchantSkill() {}

    /** Pack mules a merchant keeps: one at novice up to four at master. */
    public static int mules(int level) {
        return switch (Math.clamp(level, 1, 5)) {
            case 1 -> 1;
            case 2, 3 -> 2;
            case 4 -> 3;
            default -> 4;
        };
    }

    /** How far from where it stands a merchant looks for villages to visit: 64 blocks at novice to 160 at master. */
    public static int range(int level) {
        return 64 + (Math.clamp(level, 1, 5) - 1) * 24;
    }

    /** Villages on one tour: one at novice and apprentice, two from journeyman, three at master. */
    public static int stops(int level) {
        return switch (Math.clamp(level, 1, 5)) {
            case 1, 2 -> 1;
            case 3, 4 -> 2;
            default -> 3;
        };
    }
}
