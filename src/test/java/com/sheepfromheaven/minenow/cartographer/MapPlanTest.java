package com.sheepfromheaven.minenow.cartographer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapPlanTest {
    @Test
    void theMapZoomsOutWithLevel() {
        assertEquals(0, MapPlan.scale(1));
        assertEquals(1, MapPlan.scale(2));
        assertEquals(2, MapPlan.scale(3));
        assertEquals(3, MapPlan.scale(4));
        assertEquals(4, MapPlan.scale(5));
    }

    @Test
    void levelsAddLayers() {
        assertFalse(MapPlan.showsRoads(1));
        assertTrue(MapPlan.showsRoads(2));
        assertFalse(MapPlan.showsBorders(2));
        assertTrue(MapPlan.showsBorders(3));
        assertFalse(MapPlan.showsPlaces(3));
        assertTrue(MapPlan.showsPlaces(4));
        assertTrue(MapPlan.showsPlaces(5));
    }

    @Test
    void aPixelIsAsManyBlocksWideAsTheScaleSays() {
        assertEquals(64, MapPlan.pixel(100, 100, 0));
        assertEquals(65, MapPlan.pixel(101, 100, 0));
        assertEquals(64, MapPlan.pixel(101, 100, 1));
        assertEquals(65, MapPlan.pixel(102, 100, 1));
        assertEquals(63, MapPlan.pixel(99, 100, 1));
        assertEquals(0, MapPlan.pixel(100 - 64 * 8, 100, 3));
        assertEquals(127, MapPlan.pixel(100 + 63 * 8 + 7, 100, 3));
    }

    @Test
    void pixelsOutsideTheMapAreNotOnIt() {
        assertFalse(MapPlan.onMap(MapPlan.pixel(100 - 65, 100, 0)));
        assertFalse(MapPlan.onMap(MapPlan.pixel(100 + 64, 100, 0)));
        assertTrue(MapPlan.onMap(MapPlan.pixel(100 + 63, 100, 0)));
    }

    @Test
    void theMapCoversAsManyBlocksAsItsScaleSays() {
        assertEquals(128, MapPlan.blocksAcross(0));
        assertEquals(2048, MapPlan.blocksAcross(4));
    }
}
