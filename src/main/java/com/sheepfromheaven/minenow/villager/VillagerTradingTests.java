package com.sheepfromheaven.minenow.villager;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

public final class VillagerTradingTests {
    private VillagerTradingTests() {}

    public static void butcherBuysWheatFromTheFarmer(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        BlockPos smoker = new BlockPos(4, 1, 12);
        helper.setBlock(smoker, Blocks.SMOKER);
        Villager butcher = GameTestHelpers.spawnEmployed(helper, smoker.east(), smoker, VillagerProfession.BUTCHER, PoiTypes.BUTCHER);
        butcher.getInventory().addItem(new ItemStack(Items.STONE_AXE));
        butcher.getInventory().addItem(new ItemStack(Items.EMERALD, 4));
        BlockPos composter = new BlockPos(19, 1, 12);
        helper.setBlock(composter, Blocks.COMPOSTER);
        Villager farmer = GameTestHelpers.spawnEmployed(helper, composter.west(), composter, VillagerProfession.FARMER, PoiTypes.FARMER);
        farmer.getInventory().addItem(new ItemStack(Items.WHEAT, 40));
        double[] closest = {Double.MAX_VALUE};
        helper.onEachTick(() -> closest[0] = Math.min(closest[0], butcher.distanceTo(farmer)));

        helper.succeedWhen(() -> {
            helper.assertTrue(count(butcher, Items.WHEAT) >= 20, "The butcher should buy wheat from the farmer, has " + count(butcher, Items.WHEAT));
            helper.assertTrue(count(butcher, Items.EMERALD) == 3, "It pays the farmer's price of one emerald for 20, has " + count(butcher, Items.EMERALD) + " emeralds left");
            helper.assertTrue(count(farmer, Items.EMERALD) == 1, "The farmer gets the emerald");
            helper.assertTrue(count(farmer, Items.WHEAT) == 20, "The farmer hands over 20 wheat, has " + count(farmer, Items.WHEAT));
            helper.assertTrue(closest[0] <= 3.0, "It walks over to the farmer to trade, came within " + closest[0]);
        });
    }

    public static void aVillagerKeepsItsReserveWhenBuyingFromVillagers(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        BlockPos smoker = new BlockPos(4, 1, 12);
        helper.setBlock(smoker, Blocks.SMOKER);
        Villager butcher = GameTestHelpers.spawnEmployed(helper, smoker.east(), smoker, VillagerProfession.BUTCHER, PoiTypes.BUTCHER);
        butcher.getInventory().addItem(new ItemStack(Items.STONE_AXE));
        butcher.getInventory().addItem(new ItemStack(Items.EMERALD, Economy.FOOD_MONEY + 1));
        BlockPos composter = new BlockPos(19, 1, 12);
        helper.setBlock(composter, Blocks.COMPOSTER);
        Villager farmer = GameTestHelpers.spawnEmployed(helper, composter.west(), composter, VillagerProfession.FARMER, PoiTypes.FARMER);
        farmer.getInventory().addItem(new ItemStack(Items.WHEAT, 40));

        helper.runAfterDelay(600, () -> {
            helper.assertTrue(count(butcher, Items.EMERALD) == Economy.FOOD_MONEY + 1, "With only its food money and a new axe's price it buys no supplies, has " + count(butcher, Items.EMERALD));
            helper.assertTrue(count(butcher, Items.WHEAT) == 0, "...so no wheat");
            helper.succeed();
        });
    }

    private static int count(Villager villager, Item item) {
        return villager.getInventory().countItem(item);
    }

    public static void aSellerOutOfReachDoesNotCostTheBuyerItsJob(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        BlockPos composter = new BlockPos(5, 1, 12);
        helper.setBlock(composter, Blocks.COMPOSTER);
        Villager buyer = GameTestHelpers.spawnEmployed(helper, composter.east(), composter, VillagerProfession.FARMER, PoiTypes.FARMER);
        buyer.getInventory().addItem(new ItemStack(Items.EMERALD, 5));
        BlockPos sellerSite = new BlockPos(11, 1, 12);
        for (int x = 8; x <= 14; x++) {
            for (int z = 9; z <= 15; z++) {
                for (int y = 1; y <= 3; y++) {
                    if (x == 8 || x == 14 || z == 9 || z == 15 || y == 3) helper.setBlock(x, y, z, Blocks.GLASS);
                }
            }
        }
        helper.setBlock(sellerSite, Blocks.COMPOSTER);
        Villager seller = GameTestHelpers.spawnEmployed(helper, sellerSite.east(), sellerSite, VillagerProfession.FARMER, PoiTypes.FARMER);
        seller.getInventory().addItem(new ItemStack(Items.POTATO, 64));
        seller.setNoAi(true);
        seller.snapTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(9.6, 1, 12.5)));
        helper.onEachTick(() -> com.sheepfromheaven.minenow.villager.Meals.makeHungry(buyer));

        helper.runAfterDelay(2600, () -> {
            helper.assertTrue(buyer.getBrain().hasMemoryValue(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE),
                    "Failing to reach a seller behind glass must not count as failing to reach its own composter, or vanilla takes its job away");
            helper.assertTrue(buyer.getInventory().countItem(Items.EMERALD) == 5, "...and it can't buy through the glass, has " + buyer.getInventory().countItem(Items.EMERALD));
            helper.succeed();
        });
    }
}
