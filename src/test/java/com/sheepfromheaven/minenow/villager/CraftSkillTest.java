package com.sheepfromheaven.minenow.villager;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CraftSkillTest {
    @Test
    void betterCraftsmenWorkFaster() {
        assertEquals(100, CraftSkill.ticks(1));
        assertEquals(40, CraftSkill.ticks(5));
    }
}
