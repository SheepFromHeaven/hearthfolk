package com.sheepfromheaven.minenow.dev;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaygroundLayoutTest {
    private static final int CELL = 32;

    private static PlaygroundLayout.Plan plan() {
        Map<String, List<String>> groups = new LinkedHashMap<>();
        groups.put("villagers", List.of("a", "b", "c", "d", "e"));
        groups.put("farmer", List.of("f", "g", "h"));
        groups.put("roads", List.of("i"));
        return PlaygroundLayout.plan(groups, CELL, 16);
    }

    @Test
    void everyTestGetsAPlot() {
        assertEquals(9, plan().plots().size());
    }

    @Test
    void districtsDoNotOverlap() {
        List<PlaygroundLayout.District> districts = plan().districts();
        for (int i = 0; i < districts.size(); i++) {
            for (int j = i + 1; j < districts.size(); j++) {
                PlaygroundLayout.District a = districts.get(i), b = districts.get(j);
                boolean overlap = a.minX() < b.maxX() && b.minX() < a.maxX() && a.minZ() < b.maxZ() && b.minZ() < a.maxZ();
                assertFalse(overlap, a.group() + " overlaps " + b.group());
            }
        }
    }

    @Test
    void eachPlotLiesInsideItsGroupsDistrict() {
        PlaygroundLayout.Plan plan = plan();
        for (PlaygroundLayout.Plot plot : plan.plots()) {
            PlaygroundLayout.District district = plan.districts().stream().filter(d -> d.group().equals(plot.group())).findFirst().orElseThrow();
            assertTrue(plot.x() >= district.minX() && plot.x() + CELL <= district.maxX()
                    && plot.z() >= district.minZ() && plot.z() + CELL <= district.maxZ(), plot + " outside " + district);
        }
    }
}
