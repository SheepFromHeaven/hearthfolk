package com.sheepfromheaven.minenow.mason;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MasonSkillTest {
    @Test
    void aNovicePavesNothingAndTheRangeGrows() {
        assertEquals(0, MasonSkill.roadRange(1));
        assertEquals(32, MasonSkill.roadRange(2));
        assertEquals(96, MasonSkill.roadRange(5));
    }

    @Test
    void stoneRoadsFromJourneymanOn() {
        assertFalse(MasonSkill.laysStone(2));
        assertTrue(MasonSkill.laysStone(3));
    }
}
