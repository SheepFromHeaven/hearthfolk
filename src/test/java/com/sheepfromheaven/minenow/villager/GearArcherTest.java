package com.sheepfromheaven.minenow.villager;

import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GearArcherTest {
    @Test
    void aboutAThirdOfVillagersAreArchers() {
        Random random = new Random(7);
        int archers = 0;
        int villagers = 3000;
        for (int i = 0; i < villagers; i++) {
            if (Gear.prefersBow(new UUID(random.nextLong(), random.nextLong()))) archers++;
        }
        double share = archers / (double) villagers;
        assertTrue(share > 0.28 && share < 0.39, "archer share " + share);
    }
}
