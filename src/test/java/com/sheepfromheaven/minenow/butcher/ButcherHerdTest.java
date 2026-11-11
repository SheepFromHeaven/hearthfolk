package com.sheepfromheaven.minenow.butcher;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ButcherHerdTest {
    @Test
    void cullsEveryAdultAboveTheBreedingStock() {
        assertEquals(5, ButcherHerd.toCull(ButcherHerd.BREEDING_STOCK + 5, 0));
        assertEquals(1, ButcherHerd.toCull(ButcherHerd.BREEDING_STOCK + 1, 0));
        assertEquals(0, ButcherHerd.toCull(ButcherHerd.BREEDING_STOCK, 0));
    }

    @Test
    void youngAreNeverCulledAndNeverMakeItCullMore() {
        assertEquals(2, ButcherHerd.toCull(ButcherHerd.BREEDING_STOCK + 2, 2));
    }

    @Test
    void neverCullsBelowTheBreedingStock() {
        assertEquals(0, ButcherHerd.toCull(ButcherHerd.BREEDING_STOCK, 10));
        assertEquals(1, ButcherHerd.toCull(ButcherHerd.BREEDING_STOCK + 1, 10));
        assertEquals(0, ButcherHerd.toCull(1, 0));
        assertTrue(ButcherHerd.BREEDING_STOCK > ButcherHerd.BREEDING_PAIR);
    }

    @Test
    void breedsWhileThereIsAPairAndRoomInThePen() {
        assertTrue(ButcherHerd.wantsOffspring(2, 0));
        assertTrue(ButcherHerd.wantsOffspring(2, 8));
        assertTrue(ButcherHerd.wantsOffspring(6, ButcherHerd.HERD_SIZE - 7));
        assertFalse(ButcherHerd.wantsOffspring(6, ButcherHerd.HERD_SIZE - 6));
        assertFalse(ButcherHerd.wantsOffspring(1, 0));
    }

    @Test
    void killsAreLimitedPerDaySoTheHerdBreedsFasterThanItIsCulled() {
        assertEquals(ButcherHerd.DAILY_KILLS, ButcherHerd.killsLeft(0));
        assertEquals(1, ButcherHerd.killsLeft(ButcherHerd.DAILY_KILLS - 1));
        assertEquals(0, ButcherHerd.killsLeft(ButcherHerd.DAILY_KILLS));
        assertEquals(0, ButcherHerd.killsLeft(ButcherHerd.DAILY_KILLS + 3));
    }
}
