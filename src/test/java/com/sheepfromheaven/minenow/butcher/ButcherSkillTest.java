package com.sheepfromheaven.minenow.butcher;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ButcherSkillTest {
    @Test
    void aNoviceGetsWhatVanillaDropsAndAMasterCutsCleanest() {
        assertEquals(0, ButcherSkill.extraCuts(1));
        for (int level = 2; level <= 5; level++) {
            assertTrue(ButcherSkill.extraCuts(level) > ButcherSkill.extraCuts(level - 1), "every level cuts cleaner, level " + level);
        }
        assertTrue(ButcherSkill.extraCuts(5) > ButcherSkill.extraCuts(1));
    }
}
