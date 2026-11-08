package com.sheepfromheaven.minenow.forester;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ForesterSkillTest {
    @Test
    void theGroveGrowsWithLevel() {
        assertEquals(8, ForesterSkill.groveRange(1));
        assertEquals(12, ForesterSkill.groveRange(3));
        assertEquals(16, ForesterSkill.groveRange(5));
    }
}
