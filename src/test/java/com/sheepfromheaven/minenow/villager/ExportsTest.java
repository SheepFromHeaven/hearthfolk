package com.sheepfromheaven.minenow.villager;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExportsTest {
    @Test
    void noSurplusNoExtraVisits() {
        assertEquals(0.0, Exports.visitChance(0));
        assertEquals(0.0, Exports.visitChance(Exports.MIN_SURPLUS - 1));
    }

    @Test
    void moreSurplusMeansMoreVisitsUpToACap() {
        assertTrue(Exports.visitChance(64) > Exports.visitChance(16));
        assertEquals(Exports.MAX_CHANCE, Exports.visitChance(100_000));
    }
}
