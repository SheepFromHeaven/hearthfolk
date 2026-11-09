package com.sheepfromheaven.minenow.villager;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PriceTest {
    @Test
    void aPriceBelowAnEmeraldBecomesSoManyForOne() {
        assertEquals(new Price(20, 1), Price.of(0.05));
        assertEquals(new Price(22, 1), Price.of(0.045));
        assertEquals(new Price(15, 1), Price.of(0.0667));
    }

    @Test
    void aPriceOfWholeEmeraldsIsOneForThatMany() {
        assertEquals(new Price(1, 1), Price.of(1));
        assertEquals(new Price(1, 3), Price.of(3));
    }

    @Test
    void otherPricesBecomeTheSmallestWholeDeal() {
        assertEquals(new Price(10, 3), Price.of(0.3));
        assertEquals(new Price(2, 3), Price.of(1.5));
    }

    @Test
    void aCraftCostsItsMaterialsAndLaborRoundedUpToWholeEmeralds() {
        assertEquals(1, Price.ofCraft(0.1875, 0.5));
        assertEquals(7, Price.ofCraft(4.0625, 2.0));
        assertEquals(1, Price.ofCraft(0.0, 0.2));
        assertEquals(2, Price.ofCraft(1.0, 1.0));
    }
}
