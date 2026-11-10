package com.sheepfromheaven.minenow.mason;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MasonSkillTest {
    @Test
    void aNovicePavesNothingAndItsOwnStretchGrows() {
        assertEquals(0, MasonSkill.workshopRange(1));
        assertEquals(12, MasonSkill.workshopRange(2));
        assertEquals(20, MasonSkill.workshopRange(5));
    }

    @Test
    void stoneRoadsFromJourneymanOn() {
        assertFalse(MasonSkill.laysStone(2));
        assertTrue(MasonSkill.laysStone(3));
    }
}
