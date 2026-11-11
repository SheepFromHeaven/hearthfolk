package com.sheepfromheaven.minenow.butcher;

/**
 * How a butcher keeps each kind of livestock in its pen: young are never culled, every adult above
 * {@link #BREEDING_STOCK} may be, but at most {@link #DAILY_KILLS} animals in any day, so the herd breeds faster than
 * it is culled. It breeds whenever it has a pair, until the pen holds {@link #HERD_SIZE} animals of the kind.
 */
final class ButcherHerd {
    static final int HERD_SIZE = 12;
    static final int BREEDING_PAIR = 2;
    static final int BREEDING_STOCK = 4;
    static final int DAILY_KILLS = 4;

    private ButcherHerd() {}

    static int toCull(int adults, int young) {
        return Math.max(0, adults - BREEDING_STOCK);
    }

    static int killsLeft(int killsInTheLastDay) {
        return Math.max(0, DAILY_KILLS - killsInTheLastDay);
    }

    static boolean wantsOffspring(int adults, int young) {
        return adults >= BREEDING_PAIR && adults + young < HERD_SIZE;
    }
}
