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
        butcher.getInventory().addItem(new ItemStack(Items.EMERALD, 3));
        BlockPos composter = new BlockPos(19, 1, 12);
        helper.setBlock(composter, Blocks.COMPOSTER);
        Villager farmer = GameTestHelpers.spawnEmployed(helper, composter.west(), composter, VillagerProfession.FARMER, PoiTypes.FARMER);
        farmer.getInventory().addItem(new ItemStack(Items.WHEAT, 40));
        double[] closest = {Double.MAX_VALUE};
        helper.onEachTick(() -> closest[0] = Math.min(closest[0], butcher.distanceTo(farmer)));

        helper.succeedWhen(() -> {
            helper.assertTrue(count(butcher, Items.WHEAT) >= 20, "The butcher should buy wheat from the farmer, has " + count(butcher, Items.WHEAT));
            helper.assertTrue(count(butcher, Items.EMERALD) == 2, "It pays the farmer's price of one emerald for 20, has " + count(butcher, Items.EMERALD) + " emeralds left");
            helper.assertTrue(count(farmer, Items.EMERALD) == 1, "The farmer gets the emerald");
            helper.assertTrue(count(farmer, Items.WHEAT) == 20, "The farmer hands over 20 wheat, has " + count(farmer, Items.WHEAT));
            helper.assertTrue(closest[0] <= 3.0, "It walks over to the farmer to trade, came within " + closest[0]);
        });
    }

    private static int count(Villager villager, Item item) {
        return villager.getInventory().countItem(item);
    }
}
