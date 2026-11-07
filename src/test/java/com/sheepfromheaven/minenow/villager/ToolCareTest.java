package com.sheepfromheaven.minenow.villager;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolCareTest {
    @Test
    void betterCraftsmenWearTheirToolLess() {
        assertEquals(1.0F, ToolCare.wearChance(1));
        for (int level = 2; level <= 5; level++) {
            assertTrue(ToolCare.wearChance(level) < ToolCare.wearChance(level - 1));
        }
        assertTrue(ToolCare.wearChance(5) > 0.0F);
    }
}
