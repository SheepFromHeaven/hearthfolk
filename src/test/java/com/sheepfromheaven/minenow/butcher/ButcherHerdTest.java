package com.sheepfromheaven.minenow.butcher;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ButcherHerdTest {
    @Test
    void cullsAdultsAboveTheHerdSize() {
        assertEquals(3, ButcherHerd.toCull(9, 0));
        assertEquals(0, ButcherHerd.toCull(6, 0));
        assertEquals(0, ButcherHerd.toCull(4, 0));
    }

    @Test
    void youngCountTowardsTheHerdButAreNeverCulled() {
        assertEquals(2, ButcherHerd.toCull(6, 2));
    }

    @Test
    void neverCullsBelowTheBreedingStock() {
        assertEquals(0, ButcherHerd.toCull(2, 10));
        assertEquals(1, ButcherHerd.toCull(3, 10));
    }

    @Test
    void breedsAsLongAsThereIsABreedingPair() {
        assertTrue(ButcherHerd.wantsOffspring(2, 0));
        assertTrue(ButcherHerd.wantsOffspring(6, 0));
        assertTrue(ButcherHerd.wantsOffspring(4, 2));
        assertTrue(ButcherHerd.wantsOffspring(2, 10));
        assertFalse(ButcherHerd.wantsOffspring(1, 0));
    }
}
