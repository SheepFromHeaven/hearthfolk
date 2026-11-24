package com.sheepfromheaven.minenow.villager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.food.VillagerFood;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Any food counts towards a villager's willingness to breed, worth more the more saturating it is. */
public final class BreedingFoodTests {
    private BreedingFoodTests() {}

    private static int points(ItemStack stack) {
        VillagerFood food = stack.get(DataComponents.VILLAGER_FOOD);
        return food == null ? 0 : food.nutrition();
    }

    public static void anyFoodIsBreedingFood(GameTestHelper helper) {
        helper.assertTrue(points(new ItemStack(Items.COOKED_BEEF)) > 0, "Cooked beef counts as breeding food");
        helper.assertTrue(points(new ItemStack(Items.APPLE)) > 0, "An apple counts as breeding food");
        helper.assertTrue(points(new ItemStack(Items.DRIED_KELP)) > 0, "Dried kelp counts as breeding food");
        helper.assertTrue(points(new ItemStack(Items.STONE)) == 0, "Stone is not food");
        helper.succeed();
    }

    public static void moreSaturatingFoodIsWorthMore(GameTestHelper helper) {
        helper.assertTrue(points(new ItemStack(Items.COOKED_BEEF)) > points(new ItemStack(Items.APPLE)),
                "Cooked beef (saturation 12.8) is worth more than an apple (2.4)");
        helper.assertTrue(points(new ItemStack(Items.APPLE)) >= points(new ItemStack(Items.DRIED_KELP)),
                "An apple is worth at least as much as dried kelp (0.6)");
        helper.succeed();
    }

    public static void vanillaValuesStay(GameTestHelper helper) {
        helper.assertTrue(points(new ItemStack(Items.BREAD)) == 4, "Bread stays 4, got " + points(new ItemStack(Items.BREAD)));
        helper.assertTrue(points(new ItemStack(Items.CARROT)) == 1, "A carrot stays 1, got " + points(new ItemStack(Items.CARROT)));
        helper.succeed();
    }

    public static void rawAndUnsafeFoodIsNotBreedingFood(GameTestHelper helper) {
        for (net.minecraft.world.item.Item item : new net.minecraft.world.item.Item[]{Items.BEEF, Items.PORKCHOP, Items.CHICKEN, Items.COD, Items.SALMON,
                Items.ROTTEN_FLESH, Items.POISONOUS_POTATO, Items.SPIDER_EYE, Items.PUFFERFISH}) {
            helper.assertTrue(points(new ItemStack(item)) == 0, item + " is not breeding food");
        }
        helper.succeed();
    }

    public static void aVillagerHoldsBackOnlyWhatFeedsAndBreeds(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        BlockPos smoker = new BlockPos(6, 1, 6);
        helper.setBlock(smoker, Blocks.SMOKER);
        Villager butcher = GameTestHelpers.spawnEmployed(helper, smoker.east(), smoker, VillagerProfession.BUTCHER, PoiTypes.BUTCHER);
        ItemStack beef = new ItemStack(Items.COOKED_BEEF, 64);
        int each = points(beef);
        int expected = (Villager.BREEDING_FOOD_THRESHOLD + each - 1) / each;
        helper.assertTrue(Unloading.keepLimit(butcher, beef) == expected,
                "It holds back " + expected + " cooked beef (" + each + " points each, enough to breed), not " + Unloading.keepLimit(butcher, beef));
        helper.succeed();
    }

    public static void aWealthyVillagerBuysOnlyTheFoodItNeedsToBreed(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        BlockPos composter = new BlockPos(19, 1, 12);
        helper.setBlock(composter, Blocks.COMPOSTER);
        Villager seller = GameTestHelpers.spawnEmployed(helper, composter.west(), composter, VillagerProfession.FARMER, PoiTypes.FARMER);
        seller.getInventory().addItem(new ItemStack(Items.WOODEN_HOE));
        seller.getInventory().addItem(new ItemStack(Items.CARROT, 64));
        GameTestHelpers.giveHome(helper, seller, new BlockPos(19, 1, 3));
        BlockPos loom = new BlockPos(4, 1, 12);
        helper.setBlock(loom, Blocks.LOOM);
        Villager buyer = GameTestHelpers.spawnEmployed(helper, loom.east(), loom, VillagerProfession.SHEPHERD, PoiTypes.SHEPHERD);
        buyer.getInventory().addItem(new ItemStack(Items.SHEARS));
        buyer.getInventory().addItem(new ItemStack(Items.EMERALD, 40));
        GameTestHelpers.giveHome(helper, buyer, new BlockPos(4, 1, 3));
        GameTestHelpers.placeBed(helper, new BlockPos(10, 1, 3));

        helper.succeedWhen(() -> {
            helper.assertTrue(Wealth.pocketFoodPoints(buyer) > 0, "It buys food to breed");
            helper.assertTrue(Wealth.pocketFoodPoints(buyer) >= Villager.BREEDING_FOOD_THRESHOLD,
                    "...enough to breed, has " + Wealth.pocketFoodPoints(buyer) + " points");
            helper.assertTrue(buyer.getInventory().countItem(Items.EMERALD) == 39,
                    "...for one emerald, whose whole lot of carrots it takes, has " + buyer.getInventory().countItem(Items.EMERALD) + " emeralds");
        });
    }
}
