package com.sheepfromheaven.minenow.villager;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

public final class MealsTests {
    private MealsTests() {}

    public static void hungryVillagerEatsFromItsInventory(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(12, 1, 12));
        villager.getInventory().addItem(new ItemStack(Items.BREAD, 3));
        Meals.makeHungry(villager);

        helper.succeedWhen(() -> {
            helper.assertFalse(Meals.isHungry(villager), "A hungry villager with food should eat");
            helper.assertTrue(villager.getInventory().countItem(Items.BREAD) == 1, "Two loaves (10 nutrition) make a meal, has "
                    + villager.getInventory().countItem(Items.BREAD) + " left");
        });
    }

    public static void fedVillagerLeavesItsFoodAlone(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(12, 1, 12));
        villager.getInventory().addItem(new ItemStack(Items.BREAD, 3));

        helper.runAfterDelay(200, () -> {
            helper.assertTrue(villager.getInventory().countItem(Items.BREAD) == 3, "A villager that has eaten recently doesn't eat again");
            helper.succeed();
        });
    }

    public static void hungryVillagerWithoutFoodBuysAMeal(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        BlockPos smoker = new BlockPos(4, 1, 12);
        helper.setBlock(smoker, Blocks.SMOKER);
        Villager butcher = GameTestHelpers.spawnEmployed(helper, smoker.east(), smoker, VillagerProfession.BUTCHER, PoiTypes.BUTCHER);
        butcher.getInventory().addItem(new ItemStack(Items.STONE_AXE));
        butcher.getInventory().addItem(new ItemStack(Items.EMERALD, 2));
        Meals.makeHungry(butcher);
        BlockPos composter = new BlockPos(19, 1, 12);
        helper.setBlock(composter, Blocks.COMPOSTER);
        Villager farmer = GameTestHelpers.spawnEmployed(helper, composter.west(), composter, VillagerProfession.FARMER, PoiTypes.FARMER);
        farmer.getInventory().addItem(new ItemStack(Items.CARROT, 40));

        helper.succeedWhen(() -> {
            helper.assertFalse(Meals.isHungry(butcher), "The hungry butcher should buy food from the farmer and eat; it has "
                    + butcher.getInventory().countItem(Items.EMERALD) + " emeralds, " + butcher.getInventory().countItem(Items.CARROT) + " carrots and is "
                    + Math.round(butcher.distanceTo(farmer)) + " blocks from the farmer");
            helper.assertTrue(butcher.getInventory().countItem(Items.EMERALD) == 1, "It pays one emerald for 22 carrots, has "
                    + butcher.getInventory().countItem(Items.EMERALD) + " left");
            helper.assertTrue(butcher.getInventory().countItem(Items.CARROT) == 20, "Two carrots make a meal, the rest is kept, has "
                    + butcher.getInventory().countItem(Items.CARROT));
            helper.assertTrue(farmer.getInventory().countItem(Items.EMERALD) == 1, "The farmer gets the emerald");
        });
    }

    public static void wealthyVillagerBuysFoodToStartAFamily(GameTestHelper helper) {
        Villager villager = villagerNextToAFarmerWithPotatoes(helper);
        villager.getInventory().addItem(new ItemStack(Items.EMERALD, Wealth.required(helper.getLevel(), villager)));

        helper.succeedWhen(() -> {
            helper.assertTrue(villager.getInventory().countItem(Items.POTATO) >= Villager.BREEDING_FOOD_THRESHOLD,
                    "A villager with emeralds to spare should buy enough food to be willing to breed, has " + villager.getInventory().countItem(Items.POTATO) + " potatoes");
            helper.assertTrue(villager.getInventory().countItem(Items.EMERALD) == Wealth.required(helper.getLevel(), villager) - 1, "It spends one emerald");
        });
    }

    public static void poorVillagerKeepsItsEmeralds(GameTestHelper helper) {
        Villager villager = villagerNextToAFarmerWithPotatoes(helper);
        villager.getInventory().addItem(new ItemStack(Items.EMERALD, Wealth.required(helper.getLevel(), villager) - 1));

        helper.runAfterDelay(900, () -> {
            helper.assertTrue(villager.getInventory().countItem(Items.EMERALD) == Wealth.required(helper.getLevel(), villager) - 1, "A villager without emeralds to spare doesn't buy food for a family");
            helper.succeed();
        });
    }

    public static void wealthyVillagerWithFoodAtHomeBuysNoMore(GameTestHelper helper) {
        Villager villager = villagerNextToAFarmerWithPotatoes(helper);
        BlockPos chest = new BlockPos(4, 1, 6);
        helper.setBlock(chest, Blocks.CHEST);
        GameTestHelpers.giveHome(helper, villager, new BlockPos(4, 1, 8));
        GameTestHelpers.fillOnceClaimed(helper, () -> HomeStorage.storageOf(villager).isPresent(), chest, new ItemStack(Items.POTATO, 20));
        helper.runAfterDelay(60, () -> villager.getInventory().addItem(new ItemStack(Items.EMERALD, Wealth.required(helper.getLevel(), villager))));

        helper.runAfterDelay(900, () -> {
            helper.assertTrue(Economy.emeralds(helper.getLevel(), villager) == Wealth.required(helper.getLevel(), villager),
                    "A villager whose home chest already holds food for a family buys no more, has " + Economy.emeralds(helper.getLevel(), villager) + " emeralds and "
                            + Economy.count(helper.getLevel(), villager, Items.POTATO) + " potatoes");
            helper.succeed();
        });
    }

    public static void aHungryVillagerBuysTheMostFillingFoodForItsMoney(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        BlockPos composter = new BlockPos(19, 1, 12);
        helper.setBlock(composter, Blocks.COMPOSTER);
        Villager farmer = GameTestHelpers.spawnEmployed(helper, composter.west(), composter, VillagerProfession.FARMER, PoiTypes.FARMER);
        farmer.getInventory().addItem(new ItemStack(Items.BREAD, 40));
        farmer.getInventory().addItem(new ItemStack(Items.CARROT, 64));
        Villager hungry = helper.spawn(EntityTypes.VILLAGER, new BlockPos(6, 1, 12));
        hungry.getInventory().addItem(new ItemStack(Items.EMERALD, 1));
        Meals.makeHungry(hungry);

        helper.succeedWhen(() -> {
            helper.assertTrue(hungry.getInventory().countItem(Items.EMERALD) == 0, "It spends its emerald on food");
            helper.assertTrue(Economy.count(helper.getLevel(), hungry, Items.CARROT) > 0 && hungry.getInventory().countItem(Items.BREAD) == 0,
                    "An emerald buys more meals of carrots than of bread, so it buys carrots; has " + hungry.getInventory().countItem(Items.CARROT) + " carrots, "
                            + hungry.getInventory().countItem(Items.BREAD) + " bread");
        });
    }

    public static void aVillagerAlternatesBreadAndMeat(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(6, 1, 12));
        villager.getInventory().addItem(new ItemStack(Items.BREAD, 10));
        villager.getInventory().addItem(new ItemStack(Items.COOKED_BEEF, 10));
        Meals.eat(helper.getLevel(), villager);
        int beefAfterFirst = villager.getInventory().countItem(Items.COOKED_BEEF);
        int breadAfterFirst = villager.getInventory().countItem(Items.BREAD);
        Meals.eat(helper.getLevel(), villager);

        helper.assertTrue(beefAfterFirst == 10 - 1 || breadAfterFirst < 10, "It eats one kind of food at a meal");
        helper.assertTrue(villager.getInventory().countItem(Items.COOKED_BEEF) < 10 && villager.getInventory().countItem(Items.BREAD) < 10,
                "A villager with both eats bread at one meal and meat at the next, has " + villager.getInventory().countItem(Items.BREAD) + " bread, "
                        + villager.getInventory().countItem(Items.COOKED_BEEF) + " beef");
        helper.succeed();
    }

    public static void aVillagerWithOnlyBreadBuysSomeMeatForAChange(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        BlockPos smoker = new BlockPos(19, 1, 12);
        helper.setBlock(smoker, Blocks.SMOKER);
        Villager butcher = GameTestHelpers.spawnEmployed(helper, smoker.west(), smoker, VillagerProfession.BUTCHER, PoiTypes.BUTCHER);
        butcher.getInventory().addItem(new ItemStack(Items.STONE_AXE));
        butcher.getInventory().addItem(new ItemStack(Items.COOKED_PORKCHOP, 40));
        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(6, 1, 12));
        villager.getInventory().addItem(new ItemStack(Items.BREAD, 20));
        villager.getInventory().addItem(new ItemStack(Items.EMERALD, 6));
        Meals.eat(helper.getLevel(), villager);
        Meals.makeHungry(villager);

        helper.succeedWhen(() -> helper.assertTrue(villager.getInventory().countItem(Items.COOKED_PORKCHOP) > 0 || villager.getInventory().countItem(Items.EMERALD) < 6,
                "A villager that has only bread buys some meat for its next meal, has " + villager.getInventory().countItem(Items.EMERALD) + " emeralds"));
    }

    public static void aFarmerWithFoodToSpareFeedsAHungryNeighbourWithNoMoney(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        BlockPos composter = new BlockPos(19, 1, 12);
        helper.setBlock(composter, Blocks.COMPOSTER);
        Villager farmer = GameTestHelpers.spawnEmployed(helper, composter.west(), composter, VillagerProfession.FARMER, PoiTypes.FARMER);
        for (int i = 0; i < 3; i++) farmer.getInventory().addItem(new ItemStack(Items.CARROT, 64));
        Villager hungry = helper.spawn(EntityTypes.VILLAGER, new BlockPos(6, 1, 12));
        Meals.makeHungry(hungry);

        helper.succeedWhen(() -> helper.assertTrue(!Meals.isHungry(hungry),
                "A farmer with more carrots than it could sell gives a hungry neighbour with no money a meal"));
    }

    public static void withoutMealsAHungryVillagerDoesNotEat(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(12, 1, 12));
        villager.getInventory().addItem(new ItemStack(Items.BREAD, 3));
        Meals.makeHungry(villager);
        long lastMeal = villager.getData(ModVillagers.LAST_MEAL);

        helper.runAfterDelay(200, () -> {
            helper.assertTrue(villager.getInventory().countItem(Items.BREAD) == 3, "With meals off a villager eats nothing, has "
                    + villager.getInventory().countItem(Items.BREAD) + " loaves");
            helper.assertTrue(villager.getData(ModVillagers.LAST_MEAL) == lastMeal, "...and its last meal stays as it was");
            helper.succeed();
        });
    }

    public static void withoutMealsAHungryVillagerWithEmeraldsDoesNotBuyFood(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        BlockPos composter = new BlockPos(19, 1, 12);
        helper.setBlock(composter, Blocks.COMPOSTER);
        Villager farmer = GameTestHelpers.spawnEmployed(helper, composter.west(), composter, VillagerProfession.FARMER, PoiTypes.FARMER);
        farmer.getInventory().addItem(new ItemStack(Items.CARROT, 40));
        Villager hungry = helper.spawn(EntityTypes.VILLAGER, new BlockPos(6, 1, 12));
        hungry.getInventory().addItem(new ItemStack(Items.EMERALD, 2));
        Meals.makeHungry(hungry);

        helper.runAfterDelay(500, () -> {
            helper.assertTrue(hungry.getInventory().countItem(Items.CARROT) == 0 && hungry.getInventory().countItem(Items.EMERALD) == 2,
                    "With meals off a hungry villager buys no food, has " + hungry.getInventory().countItem(Items.CARROT) + " carrots and "
                    + hungry.getInventory().countItem(Items.EMERALD) + " emeralds");
            helper.assertTrue(farmer.getInventory().countItem(Items.CARROT) == 40, "...and the farmer sold none");
            helper.succeed();
        });
    }

    private static Villager villagerNextToAFarmerWithPotatoes(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        BlockPos composter = new BlockPos(19, 1, 12);
        helper.setBlock(composter, Blocks.COMPOSTER);
        Villager farmer = GameTestHelpers.spawnEmployed(helper, composter.west(), composter, VillagerProfession.FARMER, PoiTypes.FARMER);
        farmer.getInventory().addItem(new ItemStack(Items.POTATO, 64));
        GameTestHelpers.placeBed(helper, new BlockPos(10, 1, 3));
        return helper.spawn(EntityTypes.VILLAGER, new BlockPos(6, 1, 12));
    }
}
