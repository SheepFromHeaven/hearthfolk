package com.sheepfromheaven.minenow.shepherd;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShepherdSkillTest {
    @Test
    void shearingTakesANoviceSeconds() {
        assertTrue(ShepherdSkill.shearTicks(1) >= 40);
    }

    @Test
    void everyLevelShearsFaster() {
        for (int level = 2; level <= 5; level++) {
            assertTrue(ShepherdSkill.shearTicks(level) < ShepherdSkill.shearTicks(level - 1));
        }
        assertTrue(ShepherdSkill.shearTicks(5) > 0);
    }

    @Test
    void aNoviceLosesSomeWoolAndAMasterNone() {
        assertTrue(ShepherdSkill.woolLossChance(1) > 0.0F);
        for (int level = 2; level <= 5; level++) {
            assertTrue(ShepherdSkill.woolLossChance(level) < ShepherdSkill.woolLossChance(level - 1));
        }
        assertEquals(0.0F, ShepherdSkill.woolLossChance(5));
    }
}
