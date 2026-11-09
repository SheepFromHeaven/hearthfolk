package com.sheepfromheaven.minenow.dev;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlaygroundLabelsTest {
    @Test
    void testNamesReadAsSentences() {
        assertEquals("Farmer harvests and replants field beyond vanilla reach",
                PlaygroundLabels.humanize("farmer_harvests_and_replants_field_beyond_vanilla_reach"));
    }

    @Test
    void summaryCountsEachStatus() {
        assertEquals("2 passed · 1 failed · 3 not run · 1 running", PlaygroundLabels.summary(2, 1, 3, 1));
    }
}
