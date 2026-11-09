package com.sheepfromheaven.minenow.villager;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SightTest {
    @Test
    void aLitMobIsSeenAtTheSensorsDistanceFromLevelGround() {
        assertEquals(15.0, Sight.reach(15.0, 0.0, 15), 1e-9);
    }

    @Test
    void heightAddsReachUpToALimit() {
        assertEquals(15.0 + 1.5 * 4, Sight.reach(15.0, 4.0, 15), 1e-9);
        assertEquals(Sight.reach(15.0, 8.0, 15), Sight.reach(15.0, 30.0, 15), 1e-9);
        assertEquals(15.0, Sight.reach(15.0, -5.0, 15), 1e-9);
    }

    @Test
    void aMobInTheDarkIsSeenFromLessFar() {
        double lit = Sight.reach(15.0, 4.0, 15);
        double dim = Sight.reach(15.0, 4.0, 7);
        double dark = Sight.reach(15.0, 4.0, 0);
        assertTrue(dark < dim && dim < lit);
        assertTrue(dark < 20.0, "a mob in the dark 20 blocks away is not seen, reach " + dark);
        assertTrue(lit > 20.0, "the same mob in the light is, reach " + lit);
    }
}
