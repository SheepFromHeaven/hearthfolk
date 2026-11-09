package com.sheepfromheaven.minenow.dev;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Lays groups of tests out as districts side by side along x, each a square-ish grid of plots, with a gap between. */
final class PlaygroundLayout {
    record Plot(String group, String test, int x, int z) {}
    record District(String group, int minX, int minZ, int maxX, int maxZ) {}
    record Plan(List<District> districts, List<Plot> plots) {}

    private PlaygroundLayout() {}

    static Plan plan(Map<String, List<String>> testsByGroup, int cell, int gap) {
        List<District> districts = new ArrayList<>();
        List<Plot> plots = new ArrayList<>();
        int startX = 0;
        for (var entry : testsByGroup.entrySet()) {
            List<String> tests = entry.getValue();
            if (tests.isEmpty()) continue;
            int perRow = (int) Math.ceil(Math.sqrt(tests.size()));
            int rows = (tests.size() + perRow - 1) / perRow;
            for (int i = 0; i < tests.size(); i++) {
                plots.add(new Plot(entry.getKey(), tests.get(i), startX + (i % perRow) * cell, (i / perRow) * cell));
            }
            districts.add(new District(entry.getKey(), startX, 0, startX + perRow * cell, rows * cell));
            startX += perRow * cell + gap;
        }
        return new Plan(districts, plots);
    }
}
