package com.sheepfromheaven.minenow.villager;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The breeding line of the villager report says what keeps a villager from having a child. */
public final class BreedingReportTests {
    private BreedingReportTests() {}

    private static String breeding(GameTestHelper helper, Villager villager) {
        return String.join("\n", BreedingReport.lines(helper.getLevel(), villager));
    }

    public static void aVillagerWithNoFoodNoBedAndNoPartnerSaysSo(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(6, 1, 6));

        helper.runAfterDelay(10, () -> {
            String report = breeding(helper, villager);
            helper.assertTrue(report.contains("food"), "It says it has too little food, got " + report);
            helper.assertTrue(report.contains("bed"), "It says there is no free bed, got " + report);
            helper.assertTrue(report.contains("willing villager"), "It says there is no partner, got " + report);
            helper.succeed();
        });
    }

    public static void twoWillingVillagersWithAFreeBedAreReady(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        Villager a = GameTestHelpers.spawnWithHome(helper, new BlockPos(6, 1, 8), new BlockPos(4, 1, 4));
        Villager b = GameTestHelpers.spawnWithHome(helper, new BlockPos(8, 1, 8), new BlockPos(4, 1, 12));
        GameTestHelpers.placeBed(helper, new BlockPos(18, 1, 4));
        a.getInventory().addItem(new ItemStack(Items.POTATO, Villager.BREEDING_FOOD_THRESHOLD));
        b.getInventory().addItem(new ItemStack(Items.POTATO, Villager.BREEDING_FOOD_THRESHOLD));

        helper.runAfterDelay(10, () -> {
            String report = breeding(helper, a);
            helper.assertTrue(report.contains("nothing stops it"), "Willing, with a free bed and a willing partner, nothing stops it, got " + report);
            helper.succeed();
        });
    }

    public static void aChildSaysWhenItGrowsUp(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        Villager child = helper.spawn(EntityTypes.VILLAGER, new BlockPos(6, 1, 6));
        child.setAge(-6000);

        helper.runAfterDelay(10, () -> {
            String report = breeding(helper, child);
            helper.assertTrue(report.contains("child") && report.contains("5 min"), "A child says it is a child and when it grows up, got " + report);
            helper.succeed();
        });
    }

    public static void aPartnerAnywhereInTheVillageCounts(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        BlockPos bell = new BlockPos(36, 1, 12);
        Villager a = GameTestHelpers.spawnWithHome(helper, new BlockPos(6, 1, 8), new BlockPos(4, 1, 4));
        Villager b = GameTestHelpers.spawnWithHome(helper, new BlockPos(63, 1, 8), new BlockPos(65, 1, 4));
        GameTestHelpers.placeBed(helper, new BlockPos(36, 1, 4));
        a.getInventory().addItem(new ItemStack(Items.POTATO, Villager.BREEDING_FOOD_THRESHOLD));
        b.getInventory().addItem(new ItemStack(Items.POTATO, Villager.BREEDING_FOOD_THRESHOLD));
        VillageTests.claimBell(helper, a, bell);
        VillageTests.claimBell(helper, b, bell);

        helper.succeedWhen(() -> {
            String report = breeding(helper, a);
            helper.assertTrue(report.contains("nothing stops it"), "A willing villager of the same village counts however far, got " + report);
        });
    }

    public static void aFreeBedAnywhereInTheVillageLetsThemBreed(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        BlockPos bell = new BlockPos(36, 1, 12);
        Villager a = GameTestHelpers.spawnWithHome(helper, new BlockPos(6, 1, 8), new BlockPos(4, 1, 4));
        Villager b = GameTestHelpers.spawnWithHome(helper, new BlockPos(58, 1, 8), new BlockPos(60, 1, 4));
        GameTestHelpers.placeBed(helper, new BlockPos(67, 1, 12));
        GameTestHelpers.placeBed(helper, new BlockPos(52, 1, 12));
        a.getInventory().addItem(new ItemStack(Items.POTATO, Villager.BREEDING_FOOD_THRESHOLD));
        b.getInventory().addItem(new ItemStack(Items.POTATO, Villager.BREEDING_FOOD_THRESHOLD));
        VillageTests.claimBell(helper, a, bell);
        VillageTests.claimBell(helper, b, bell);

        helper.succeedWhen(() -> {
            String report = breeding(helper, a);
            helper.assertTrue(report.contains("nothing stops it"), "A free bed of the village counts however far, got " + report);
        });
    }
}
