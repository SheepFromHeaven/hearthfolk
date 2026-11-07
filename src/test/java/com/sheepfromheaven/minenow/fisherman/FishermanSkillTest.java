package com.sheepfromheaven.minenow.fisherman;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FishermanSkillTest {
    @Test
    void aNoviceIsSometimesTooSlowForTheShortestBite() {
        assertTrue(FishermanSkill.slowestReaction(1) > VillagerBobber.SHORTEST_BITE);
    }

    @Test
    void aMasterNeverMissesABite() {
        assertTrue(FishermanSkill.slowestReaction(5) < VillagerBobber.SHORTEST_BITE);
    }

    @Test
    void reflexesImproveWithEveryLevel() {
        for (int level = 2; level <= 5; level++) {
            assertTrue(FishermanSkill.slowestReaction(level) < FishermanSkill.slowestReaction(level - 1));
            assertTrue(FishermanSkill.fastestReaction(level) <= FishermanSkill.fastestReaction(level - 1));
            assertTrue(FishermanSkill.fastestReaction(level) > 0);
        }
    }

    @Test
    void betterFishermenWearTheirRodLess() {
        assertEquals(1.0F, FishermanSkill.rodWearChance(1));
        for (int level = 2; level <= 5; level++) {
            assertTrue(FishermanSkill.rodWearChance(level) < FishermanSkill.rodWearChance(level - 1));
        }
        assertTrue(FishermanSkill.rodWearChance(5) > 0.0F);
    }
}
