package com.sheepfromheaven.minenow.villager;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WealthFormulaTest {
    private final WealthFormula formula = new WealthFormula(5, 1.0, 6, 40);

    @Test
    void theEmeraldsNeededGrowWithTheVillage() {
        assertEquals(8, formula.required(3));
        assertEquals(15, formula.required(10));
    }

    @Test
    void aFractionPerVillagerRoundsUp() {
        assertEquals(8, new WealthFormula(5, 0.5, 0, 100).required(5));
    }

    @Test
    void theNeedStaysBetweenTheMinimumAndTheMaximum() {
        assertEquals(6, formula.required(0));
        assertEquals(40, formula.required(500));
    }
}
