package com.sheepfromheaven.minenow.mason;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadPatternTest {
    @Test
    void aBlockAlwaysGetsTheSameMaterial() {
        for (long seed = 0; seed < 100; seed++) assertEquals(RoadPattern.material(seed, true), RoadPattern.material(seed, true));
    }

    @Test
    void aRoughRoadMixesCobblestoneAndGravel() {
        Map<RoadPattern.Material, Integer> counts = count(false);
        assertEquals(2, counts.size(), "only cobblestone and gravel: " + counts);
        assertTrue(counts.get(RoadPattern.Material.COBBLESTONE) > counts.get(RoadPattern.Material.GRAVEL), "mostly cobblestone: " + counts);
    }

    @Test
    void aStoneRoadIsMostlyBricksWithSomeRoughBlocksLeft() {
        Map<RoadPattern.Material, Integer> counts = count(true);
        int stone = counts.getOrDefault(RoadPattern.Material.STONE_BRICKS, 0) + counts.getOrDefault(RoadPattern.Material.STONE_BRICK_SLAB, 0);
        assertTrue(stone > 700 && stone < 900, "about four in five stone: " + counts);
        assertTrue(counts.getOrDefault(RoadPattern.Material.STONE_BRICK_SLAB, 0) > 100, "with slabs among the bricks: " + counts);
    }

    private static Map<RoadPattern.Material, Integer> count(boolean stone) {
        Map<RoadPattern.Material, Integer> counts = new EnumMap<>(RoadPattern.Material.class);
        for (long seed = 0; seed < 1000; seed++) counts.merge(RoadPattern.material(seed * 7919L, stone), 1, Integer::sum);
        return counts;
    }
}
