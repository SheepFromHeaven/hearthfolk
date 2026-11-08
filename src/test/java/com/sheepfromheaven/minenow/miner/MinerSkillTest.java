package com.sheepfromheaven.minenow.miner;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinerSkillTest {
    @Test
    void everyLevelDigsDeeper() {
        for (int level = 2; level <= 5; level++) assertTrue(MinerSkill.depth(level) > MinerSkill.depth(level - 1));
    }

    @Test
    void aNoviceGetsWhatVanillaDropsAndAMasterFindsTheMostOre() {
        assertEquals(0, MinerSkill.extraOre(1));
        for (int level = 2; level <= 5; level++) assertTrue(MinerSkill.extraOre(level) >= MinerSkill.extraOre(level - 1));
        assertTrue(MinerSkill.extraOre(5) > 0);
    }

    @Test
    void tunnelsRunFurtherWithEveryLevel() {
        assertEquals(8, MinerSkill.tunnelLength(1));
        for (int level = 2; level <= 5; level++) assertTrue(MinerSkill.tunnelLength(level) > MinerSkill.tunnelLength(level - 1));
        assertEquals(24, MinerSkill.tunnelLength(5));
    }

    @Test
    void breakingTakesAsLongAsItWouldForAPlayer() {
        assertEquals(23, Mining.ticks(1.5F, 2.0F, true));
        assertEquals(12, Mining.ticks(1.5F, 4.0F, true));
        assertEquals(8, Mining.ticks(1.5F, 6.0F, true));
        assertEquals(1, Mining.ticks(0.0F, 1.0F, false));
    }
}
