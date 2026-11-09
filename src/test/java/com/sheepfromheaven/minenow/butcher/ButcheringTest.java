package com.sheepfromheaven.minenow.butcher;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ButcheringTest {
    @Test
    void takesAsManySwingsAsTheAnimalHasHealthForTheAxe() {
        assertEquals(2, Butchering.swings(10.0F, 7.0));
        assertEquals(1, Butchering.swings(10.0F, 10.0));
        assertEquals(1, Butchering.swings(4.0F, 7.0));
    }

    @Test
    void swingsAsOftenAsTheAxeAttacks() {
        assertEquals(25, Butchering.swingTicks(0.8));
        assertEquals(20, Butchering.swingTicks(1.0));
    }
}
