package com.sheepfromheaven.minenow.gametest;

import com.sheepfromheaven.minenow.dominion.Dominion;
import com.sheepfromheaven.minenow.dominion.DominionInhabitant;
import com.sheepfromheaven.minenow.dominion.DominionSavedData;
import com.sheepfromheaven.minenow.dominion.DominionStructure;
import com.sheepfromheaven.minenow.dominion.DominionTier;
import com.sheepfromheaven.minenow.dominion.StructureType;
import com.sheepfromheaven.minenow.entity.InhabitantEntity;
import com.sheepfromheaven.minenow.entity.ModEntities;
import com.sheepfromheaven.minenow.entity.job.ImmigrationJob;
import com.sheepfromheaven.minenow.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Set;
import java.util.UUID;

/**
 * Covers the dominion growth mechanics added for issue #5 (tier level-up + immigration). Written as
 * GameTests (rather than plain JUnit, since this codebase has no JUnit test source set set up) but most
 * of these don't rely on world state at all - they use a real ServerLevel only because
 * {@link Dominion#getTotalStoredResources} and {@link Dominion#checkAndApplyLevelUp} require one, and use
 * {@code helper.succeedWhen} for consistency with the rest of the suite even where the assertions are
 * synchronous.
 */
public class DominionGrowthTests {

    public static void addInhabitantRejectsWhenAtCapacity(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);
        var level = helper.getLevel();
        var bannerPos = helper.absolutePos(new BlockPos(1, 1, 1));
        var dominion = DominionSavedData.get(level).createDominion(UUID.randomUUID(), "Test Dominion", bannerPos);

        int max = dominion.getTier().getMaxInhabitants();
        for (int i = 0; i < max; i++) {
            boolean added = dominion.addInhabitant(new DominionInhabitant(UUID.randomUUID(), "Inhabitant " + i));
            helper.assertTrue(added, "Adding inhabitant " + i + " should succeed while under capacity");
        }
        boolean overCapacity = dominion.addInhabitant(new DominionInhabitant(UUID.randomUUID(), "One Too Many"));

        helper.succeedWhen(() -> {
            helper.assertFalse(overCapacity, "Adding an inhabitant beyond tier capacity should be rejected");
            helper.assertTrue(dominion.getInhabitants().size() == max,
                    "Dominion should still hold exactly its capacity worth of inhabitants");
        });
    }

    public static void canAttemptImmigrationRequiresAttractionRoomAndCooldown(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);
        var level = helper.getLevel();
        var bannerPos = helper.absolutePos(new BlockPos(1, 1, 1));
        var data = DominionSavedData.get(level);

        // Several dominions are packed close together in this small test structure, so temporarily shrink
        // the (global) dominion radius to keep each dominion's claimed area from bleeding into its
        // neighbors' - restored at the end of the method.
        int originalRadius = com.sheepfromheaven.minenow.Config.defaultDominionRadius;
        com.sheepfromheaven.minenow.Config.defaultDominionRadius = 2;
        try {
            // Baseline: attractive (real bell block placed loose within the dominion's area, NOT inside
            // any structure's footprint), has room, no pending immigrant, off cooldown -> allowed.
            var attractiveDominion = data.createDominion(UUID.randomUUID(), "Attractive", bannerPos);
            var houseRelPos = new BlockPos(5, 1, 5);
            var housePos = helper.absolutePos(houseRelPos);
            attractiveDominion.addStructure(new DominionStructure("Plain House", StructureType.HOUSE, housePos, Set.of(housePos)));
            var bellRelPos1 = new BlockPos(3, 1, 3);
            helper.setBlock(bellRelPos1, Blocks.BELL);
            boolean baselineAllowed = attractiveDominion.canAttemptImmigration(1000L, level);

            // Bell exists but outside the dominion's claimed radius -> not attractive, rejected.
            var outsideBellDominion = data.createDominion(UUID.randomUUID(), "OutsideBell", helper.absolutePos(new BlockPos(1, 1, 30)));
            helper.setBlock(new BlockPos(1, 1, 38), Blocks.BELL);
            boolean outsideBellAllowed = outsideBellDominion.canAttemptImmigration(1000L, level);

            // No attraction structure (no bell block, no tavern) -> rejected.
            var plainRelPos = new BlockPos(1, 1, 6);
            var plainDominion = data.createDominion(UUID.randomUUID(), "Plain", helper.absolutePos(plainRelPos));
            boolean noAttractionAllowed = plainDominion.canAttemptImmigration(1000L, level);

            // Full dominion -> rejected even though attractive (tavern, still a logical attraction tag).
            var fullDominion = data.createDominion(UUID.randomUUID(), "Full", helper.absolutePos(new BlockPos(1, 1, 12)));
            fullDominion.addStructure(new DominionStructure("Tavern", StructureType.TAVERN,
                    helper.absolutePos(new BlockPos(3, 1, 12)), Set.of(helper.absolutePos(new BlockPos(3, 1, 12)))));
            for (int i = 0; i < fullDominion.getTier().getMaxInhabitants(); i++) {
                fullDominion.addInhabitant(new DominionInhabitant(UUID.randomUUID(), "Inhabitant " + i));
            }
            boolean fullAllowed = fullDominion.canAttemptImmigration(1000L, level);

            // Already-pending immigrant -> rejected.
            var pendingDominion = data.createDominion(UUID.randomUUID(), "Pending", helper.absolutePos(new BlockPos(1, 1, 18)));
            var bellRelPos2 = new BlockPos(1, 1, 20);
            helper.setBlock(bellRelPos2, Blocks.BELL);
            pendingDominion.setPendingImmigrantId(UUID.randomUUID());
            boolean pendingAllowed = pendingDominion.canAttemptImmigration(1000L, level);

            // On cooldown -> rejected.
            var cooldownDominion = data.createDominion(UUID.randomUUID(), "Cooldown", helper.absolutePos(new BlockPos(1, 1, 24)));
            var bellRelPos3 = new BlockPos(1, 1, 26);
            helper.setBlock(bellRelPos3, Blocks.BELL);
            cooldownDominion.setNextImmigrationAttemptTick(2000L);
            boolean cooldownAllowed = cooldownDominion.canAttemptImmigration(1000L, level);

            helper.succeedWhen(() -> {
                helper.assertTrue(baselineAllowed, "Attractive dominion with room, off cooldown, no pending immigrant should allow an attempt");
                helper.assertFalse(outsideBellAllowed, "A bell outside the dominion's claimed radius should not count as an attraction");
                helper.assertFalse(noAttractionAllowed, "Dominion without a bell/tavern should not allow an immigration attempt");
                helper.assertFalse(fullAllowed, "Full dominion should not allow an immigration attempt even if attractive");
                helper.assertFalse(pendingAllowed, "Dominion with an already-pending immigrant should not roll again");
                helper.assertFalse(cooldownAllowed, "Dominion still on cooldown should not allow an immigration attempt");
            });
        } finally {
            com.sheepfromheaven.minenow.Config.defaultDominionRadius = originalRadius;
        }
    }

    public static void checkAndApplyLevelUpAdvancesOnlyWhenAllThresholdsMet(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);
        var level = helper.getLevel();

        // Meets all three SETTLEMENT -> VILLAGE thresholds (population 3, structures 3, resources 32).
        var readyDominion = DominionSavedData.get(level).createDominion(UUID.randomUUID(), "Ready",
                helper.absolutePos(new BlockPos(1, 1, 1)));
        for (int i = 0; i < 3; i++) {
            readyDominion.addInhabitant(new DominionInhabitant(UUID.randomUUID(), "Inhabitant " + i));
        }
        for (int i = 0; i < 3; i++) {
            var pos = helper.absolutePos(new BlockPos(3 + i, 1, 1));
            readyDominion.addStructure(new DominionStructure("Structure " + i, StructureType.HOUSE, pos, Set.of(pos)));
        }
        var barrelRelPos = new BlockPos(6, 1, 1);
        helper.setBlock(barrelRelPos, Blocks.BARREL);
        var barrelPos = helper.absolutePos(barrelRelPos);
        readyDominion.addStructure(new DominionStructure("Storehouse", StructureType.STOREHOUSE, barrelPos, Set.of(barrelPos)));
        if (level.getBlockEntity(barrelPos) instanceof net.minecraft.world.Container container) {
            container.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE, 32));
        }

        // Only meets population + structures, not resources - should NOT advance.
        var partialDominion = DominionSavedData.get(level).createDominion(UUID.randomUUID(), "Partial",
                helper.absolutePos(new BlockPos(1, 1, 5)));
        for (int i = 0; i < 3; i++) {
            partialDominion.addInhabitant(new DominionInhabitant(UUID.randomUUID(), "Inhabitant " + i));
        }
        for (int i = 0; i < 3; i++) {
            var pos = helper.absolutePos(new BlockPos(3 + i, 1, 5));
            partialDominion.addStructure(new DominionStructure("Structure " + i, StructureType.HOUSE, pos, Set.of(pos)));
        }

        var readyResult = readyDominion.checkAndApplyLevelUp(level);
        var partialResult = partialDominion.checkAndApplyLevelUp(level);

        helper.succeedWhen(() -> {
            helper.assertTrue(readyResult == DominionTier.VILLAGE,
                    "Dominion meeting population/structures/resources thresholds should advance to VILLAGE");
            helper.assertTrue(readyDominion.getTier() == DominionTier.VILLAGE,
                    "Dominion's stored tier should now be VILLAGE");
            helper.assertTrue(partialResult == null,
                    "Dominion meeting only 2 of 3 thresholds should not advance");
            helper.assertTrue(partialDominion.getTier() == DominionTier.SETTLEMENT,
                    "Dominion's stored tier should remain SETTLEMENT");
        });
    }

    public static void higherTierDominionHasLargerEffectiveRadiusForBellAttraction(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);
        var level = helper.getLevel();
        var data = DominionSavedData.get(level);

        // Shrink the (global) base radius so a small bell offset (distance 4) is meaningfully outside a
        // SETTLEMENT-tier dominion's effective radius (2 * 1.0 = 2) but inside a CITY-tier dominion's
        // effective radius (2 * 3.0 = 6) at the same relative bell distance - restored at the end.
        int originalRadius = com.sheepfromheaven.minenow.Config.defaultDominionRadius;
        com.sheepfromheaven.minenow.Config.defaultDominionRadius = 2;
        try {
            var settlementDominion = data.createDominion(UUID.randomUUID(), "Settlement",
                    helper.absolutePos(new BlockPos(1, 1, 1)));
            helper.setBlock(new BlockPos(5, 1, 1), Blocks.BELL);
            boolean settlementAllowed = settlementDominion.canAttemptImmigration(1000L, level);

            var cityDominion = data.createDominion(UUID.randomUUID(), "City",
                    helper.absolutePos(new BlockPos(1, 1, 6)));
            cityDominion.setTier(DominionTier.CITY);
            helper.setBlock(new BlockPos(5, 1, 6), Blocks.BELL);
            boolean cityAllowed = cityDominion.canAttemptImmigration(1000L, level);

            helper.succeedWhen(() -> {
                helper.assertFalse(settlementAllowed,
                        "A bell 4 blocks out should be outside a SETTLEMENT-tier dominion's smaller effective radius");
                helper.assertTrue(cityAllowed,
                        "The same relative bell distance should be inside a CITY-tier dominion's larger effective radius");
            });
        } finally {
            com.sheepfromheaven.minenow.Config.defaultDominionRadius = originalRadius;
        }
    }

    /** Builds a {@link UseOnContext} as if a player right-clicked the top face of the floor block at the given relative position. */
    private static UseOnContext floorClickContext(GameTestHelper helper, ItemStack stack, BlockPos relFloorPos) {
        var level = helper.getLevel();
        var absFloorPos = helper.absolutePos(relFloorPos);
        var hit = new BlockHitResult(Vec3.atCenterOf(absFloorPos), Direction.UP, absFloorPos, false);
        return new UseOnContext(level, null, InteractionHand.MAIN_HAND, stack, hit);
    }

    public static void spawnEggSpawnsInhabitantInsideDominion(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);
        var level = helper.getLevel();
        // GameTest structures share one overworld DominionSavedData, so a neighboring test's dominion
        // banner can otherwise fall within this dominion's (default 64-block) claimed radius and get
        // matched instead - shrink the (global) radius for the duration of this test to avoid that.
        int originalRadius = com.sheepfromheaven.minenow.Config.defaultDominionRadius;
        com.sheepfromheaven.minenow.Config.defaultDominionRadius = 2;
        try {
            var bannerPos = helper.absolutePos(new BlockPos(1, 1, 1));
            var dominion = DominionSavedData.get(level).createDominion(UUID.randomUUID(), "Test Dominion", bannerPos);

            var stack = new ItemStack(ModItems.INHABITANT_SPAWN_EGG.get());
            var context = floorClickContext(helper, stack, new BlockPos(1, 0, 1));
            var result = ModItems.INHABITANT_SPAWN_EGG.get().useOn(context);

            var spawned = level.getEntitiesOfClass(InhabitantEntity.class,
                    AABB.encapsulatingFullBlocks(helper.absolutePos(new BlockPos(0, 0, 0)), helper.absolutePos(new BlockPos(8, 3, 8))));

            helper.succeedWhen(() -> {
                helper.assertTrue(result == InteractionResult.SUCCESS, "Spawning inside a dominion's area should succeed");
                helper.assertTrue(dominion.getInhabitants().size() == 1, "Dominion should have gained exactly one inhabitant");
                helper.assertTrue(spawned.size() == 1, "Exactly one inhabitant entity should have been spawned");
                var entity = spawned.get(0);
                helper.assertTrue(dominion.getId().equals(entity.getDominionId()), "Spawned inhabitant should be assigned to the dominion");
                helper.assertTrue(!entity.getName().getString().isEmpty(), "Spawned inhabitant should have a name");
            });
        } finally {
            com.sheepfromheaven.minenow.Config.defaultDominionRadius = originalRadius;
        }
    }

    public static void spawnEggFailsOutsideAnyDominion(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);
        var level = helper.getLevel();
        int originalRadius = com.sheepfromheaven.minenow.Config.defaultDominionRadius;
        com.sheepfromheaven.minenow.Config.defaultDominionRadius = 2;
        try {
            // Dominion exists but far from the click position, so the click lands outside any claimed area.
            var bannerPos = helper.absolutePos(new BlockPos(1, 1, 1));
            var dominion = DominionSavedData.get(level).createDominion(UUID.randomUUID(), "Test Dominion", bannerPos);

            var stack = new ItemStack(ModItems.INHABITANT_SPAWN_EGG.get());
            var context = floorClickContext(helper, stack, new BlockPos(20, 0, 20));
            var result = ModItems.INHABITANT_SPAWN_EGG.get().useOn(context);

            var spawned = level.getEntitiesOfClass(InhabitantEntity.class,
                    AABB.encapsulatingFullBlocks(helper.absolutePos(new BlockPos(0, 0, 0)), helper.absolutePos(new BlockPos(30, 3, 30))));

            helper.succeedWhen(() -> {
                helper.assertTrue(result == InteractionResult.FAIL, "Spawning outside any dominion's area should fail");
                helper.assertTrue(dominion.getInhabitants().isEmpty(), "Dominion inhabitant count should be unaffected");
                helper.assertTrue(spawned.isEmpty(), "No inhabitant entity should have been spawned");
            });
        } finally {
            com.sheepfromheaven.minenow.Config.defaultDominionRadius = originalRadius;
        }
    }

    public static void spawnEggFailsWhenDominionAtCapacity(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);
        var level = helper.getLevel();
        int originalRadius = com.sheepfromheaven.minenow.Config.defaultDominionRadius;
        com.sheepfromheaven.minenow.Config.defaultDominionRadius = 2;
        try {
            var bannerPos = helper.absolutePos(new BlockPos(1, 1, 1));
            var dominion = DominionSavedData.get(level).createDominion(UUID.randomUUID(), "Test Dominion", bannerPos);
            int max = dominion.getTier().getMaxInhabitants();
            for (int i = 0; i < max; i++) {
                dominion.addInhabitant(new DominionInhabitant(UUID.randomUUID(), "Inhabitant " + i));
            }

            var stack = new ItemStack(ModItems.INHABITANT_SPAWN_EGG.get());
            var context = floorClickContext(helper, stack, new BlockPos(1, 0, 1));
            var result = ModItems.INHABITANT_SPAWN_EGG.get().useOn(context);

            var spawned = level.getEntitiesOfClass(InhabitantEntity.class,
                    AABB.encapsulatingFullBlocks(helper.absolutePos(new BlockPos(0, 0, 0)), helper.absolutePos(new BlockPos(8, 3, 8))));

            helper.succeedWhen(() -> {
                helper.assertTrue(result == InteractionResult.FAIL, "Spawning into a full dominion should fail");
                helper.assertTrue(dominion.getInhabitants().size() == max, "Dominion inhabitant count should remain at capacity");
                helper.assertTrue(spawned.isEmpty(), "No new inhabitant entity should have been spawned");
            });
        } finally {
            com.sheepfromheaven.minenow.Config.defaultDominionRadius = originalRadius;
        }
    }

    public static void walkToBannerGoalReachesAwaitingDecisionPhase(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);
        var level = helper.getLevel();
        var bannerPos = helper.absolutePos(new BlockPos(4, 1, 4));
        var dominion = DominionSavedData.get(level).createDominion(UUID.randomUUID(), "Test Dominion", bannerPos);

        var candidate = helper.spawn(ModEntities.INHABITANT.get(), new BlockPos(1, 1, 1));
        var job = new ImmigrationJob(dominion.getId());
        candidate.setImmigrationJob(job);

        helper.succeedWhen(() -> {
            helper.assertTrue(job.getPhase() == ImmigrationJob.Phase.AWAITING_DECISION,
                    "Candidate should reach AWAITING_DECISION after walking to the banner");
        });
    }
}
