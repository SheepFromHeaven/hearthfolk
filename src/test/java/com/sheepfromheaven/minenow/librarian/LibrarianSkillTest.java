package com.sheepfromheaven.minenow.librarian;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LibrarianSkillTest {
    @Test
    void powerGrowsWithEveryLevelUpToTheEnchantingTablesBest() {
        for (int level = 2; level <= 5; level++) assertTrue(LibrarianSkill.power(level) > LibrarianSkill.power(level - 1), "level " + level);
        assertEquals(30, LibrarianSkill.power(5));
    }

    @Test
    void levelsOutsideOneToFiveAreClamped() {
        assertEquals(LibrarianSkill.power(1), LibrarianSkill.power(0));
        assertEquals(LibrarianSkill.power(5), LibrarianSkill.power(9));
    }

    @Test
    void onlyAnExpertOrMasterBindsWhatTradersSell() {
        for (int level = 1; level <= 3; level++) assertFalse(LibrarianSkill.bindsTradeableEnchantments(level), "level " + level);
        assertTrue(LibrarianSkill.bindsTradeableEnchantments(4));
        assertTrue(LibrarianSkill.bindsTradeableEnchantments(5));
    }
}
