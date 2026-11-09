package com.sheepfromheaven.minenow.fisherman;

import org.junit.jupiter.api.Test;

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

}
