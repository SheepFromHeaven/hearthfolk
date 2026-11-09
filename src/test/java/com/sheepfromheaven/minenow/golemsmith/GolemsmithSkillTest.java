package com.sheepfromheaven.minenow.golemsmith;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GolemsmithSkillTest {
    @Test
    void buildingStartsAtJourneyman() {
        assertFalse(GolemsmithSkill.buildsGolems(2));
        assertTrue(GolemsmithSkill.buildsGolems(3));
    }

    @Test
    void theVillageHoldsMoreGolemsWithLevel() {
        assertEquals(0, GolemsmithSkill.maxGolems(2));
        assertEquals(2, GolemsmithSkill.maxGolems(3));
        assertEquals(3, GolemsmithSkill.maxGolems(4));
        assertEquals(4, GolemsmithSkill.maxGolems(5));
    }

    @Test
    void anIngotRepairsAQuarterOfAGolem() {
        assertTrue(GolemsmithSkill.worthRepairing(25, 100));
        assertFalse(GolemsmithSkill.worthRepairing(24, 100));
    }
}
