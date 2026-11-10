package com.sheepfromheaven.minenow.butcher;

/**
 * How a butcher keeps each kind of livestock in its pen: young count towards the herd but are never culled, adults
 * above {@link #HERD_SIZE} are culled, and it never culls below {@link #BREEDING_STOCK} adults. It always keeps
 * {@link #YOUNG} growing up, so a full herd still yields surplus to cull instead of standing still at its size.
 */
final class ButcherHerd {
    static final int HERD_SIZE = 6;
    static final int BREEDING_STOCK = 2;
    static final int YOUNG = 2;

    private ButcherHerd() {}

    static int toCull(int adults, int young) {
        return Math.max(0, Math.min(adults - BREEDING_STOCK, adults + young - HERD_SIZE));
    }

    static boolean wantsOffspring(int adults, int young) {
        return adults >= BREEDING_STOCK && young < YOUNG;
    }
}
