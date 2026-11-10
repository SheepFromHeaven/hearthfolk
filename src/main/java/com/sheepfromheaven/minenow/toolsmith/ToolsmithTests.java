package com.sheepfromheaven.minenow.toolsmith;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Blocks;

import java.util.Optional;

public final class ToolsmithTests {
    static final BlockPos TABLE = new BlockPos(6, 1, 12);

    private ToolsmithTests() {}

    public static void toolsmithCraftsAToolAtItsSmithingTable(GameTestHelper helper) {
        Villager toolsmith = spawnToolsmith(helper);
        toolsmith.getInventory().addItem(new ItemStack(Items.COBBLESTONE, 2));
        toolsmith.getInventory().addItem(new ItemStack(Items.STICK, 2));
        long[] craftedAt = {-1};
        helper.onEachTick(() -> {
            if (craftedAt[0] < 0 && toolsmith.getInventory().countItem(Items.STONE_HOE) > 0) craftedAt[0] = helper.getTick();
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(craftedAt[0] >= 0, "The toolsmith should craft a stone hoe from 2 cobblestone and 2 sticks");
            helper.assertTrue(craftedAt[0] >= com.sheepfromheaven.minenow.villager.CraftSkill.ticks(1), "Crafting takes time, done on tick " + craftedAt[0]);
            helper.assertTrue(toolsmith.getInventory().countItem(Items.COBBLESTONE) == 0 && toolsmith.getInventory().countItem(Items.STICK) == 0,
                    "The materials are used up");
        });
    }

    public static void toolsmithGainsExperienceFromCrafting(GameTestHelper helper) {
        Villager toolsmith = spawnToolsmith(helper);
        toolsmith.getInventory().addItem(new ItemStack(Items.COBBLESTONE, 2));
        toolsmith.getInventory().addItem(new ItemStack(Items.STICK, 2));
        com.sheepfromheaven.minenow.gametest.GameTestHelpers.allowWorkXp(toolsmith);

        helper.succeedWhen(() -> helper.assertTrue(toolsmith.getVillagerXp() > 0,
                "A crafter should earn experience from crafting, xp " + toolsmith.getVillagerXp()));
    }

    public static void toolsmithKeepsOneOfEachToolInStock(GameTestHelper helper) {
        Villager toolsmith = spawnToolsmith(helper);
        toolsmith.getInventory().addItem(new ItemStack(Items.COBBLESTONE, 8));
        toolsmith.getInventory().addItem(new ItemStack(Items.STICK, 8));

        helper.runAfterDelay(1200, () -> {
            helper.assertTrue(toolsmith.getInventory().countItem(Items.STONE_HOE) == 1, "One stone hoe in stock, has " + toolsmith.getInventory().countItem(Items.STONE_HOE));
            helper.assertTrue(toolsmith.getInventory().countItem(Items.STONE_AXE) == 1, "One stone axe in stock, has " + toolsmith.getInventory().countItem(Items.STONE_AXE));
            helper.succeed();
        });
    }

    public static void toolsmithSellsItsToolsToPlayers(GameTestHelper helper) {
        Villager toolsmith = spawnToolsmith(helper);
        toolsmith.getInventory().addItem(new ItemStack(Items.STONE_HOE));
        @SuppressWarnings("removal")
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.interactOn(toolsmith, InteractionHand.MAIN_HAND, toolsmith.position());

        Optional<MerchantOffer> hoe = toolsmith.getOffers().stream().filter(o -> o.getResult().is(Items.STONE_HOE)).findFirst();
        helper.assertTrue(hoe.isPresent() && hoe.get().getCostA().is(Items.EMERALD), "The toolsmith sells the stone hoe it has in stock for emeralds");
        helper.succeed();
    }

    public static void toolsmithChargesForMaterialsAndLabor(GameTestHelper helper) {
        Villager toolsmith = spawnToolsmith(helper);
        toolsmith.setVillagerData(toolsmith.getVillagerData().withLevel(4));
        toolsmith.getInventory().addItem(new ItemStack(Items.DIAMOND_HOE));
        @SuppressWarnings("removal")
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.interactOn(toolsmith, InteractionHand.MAIN_HAND, toolsmith.position());

        Optional<MerchantOffer> hoe = toolsmith.getOffers().stream().filter(o -> o.getResult().is(Items.DIAMOND_HOE)).findFirst();
        helper.assertTrue(hoe.isPresent(), "The toolsmith sells its diamond hoe");
        helper.assertTrue(hoe.get().getCostA().getCount() == 7, "2 diamonds at 2, 2 sticks at 1/32 and 2 emeralds of labor make 7, asks "
                + hoe.get().getCostA().getCount());
        helper.succeed();
    }

    public static void farmerWithoutAHoeBuysOneFromTheToolsmith(GameTestHelper helper) {
        Villager toolsmith = spawnToolsmith(helper);
        toolsmith.getInventory().addItem(new ItemStack(Items.STONE_HOE));
        BlockPos composter = new BlockPos(19, 1, 12);
        helper.setBlock(composter, Blocks.COMPOSTER);
        Villager farmer = GameTestHelpers.spawnEmployed(helper, composter.west(), composter, VillagerProfession.FARMER, PoiTypes.FARMER);
        farmer.getInventory().addItem(new ItemStack(Items.EMERALD, 3));

        helper.succeedWhen(() -> {
            helper.assertTrue(farmer.getInventory().countItem(Items.STONE_HOE) == 1, "A farmer without a hoe should buy one from the toolsmith");
            helper.assertTrue(farmer.getInventory().countItem(Items.EMERALD) == 2, "It pays the toolsmith's price of one emerald, has "
                    + farmer.getInventory().countItem(Items.EMERALD));
            helper.assertTrue(toolsmith.getInventory().countItem(Items.EMERALD) == 1, "The toolsmith gets the emerald");
        });
    }

    public static void minerWithoutAPickaxeBuysOneFromTheToolsmith(GameTestHelper helper) {
        Villager toolsmith = spawnToolsmith(helper);
        toolsmith.getInventory().addItem(new ItemStack(Items.COBBLESTONE, 8));
        toolsmith.getInventory().addItem(new ItemStack(Items.STICK, 6));
        BlockPos cart = new BlockPos(19, 1, 12);
        helper.setBlock(cart, com.sheepfromheaven.minenow.miner.Miner.FURNACE_CART.get());
        Villager miner = GameTestHelpers.spawnEmployed(helper, cart.west(), cart, com.sheepfromheaven.minenow.miner.Miner.PROFESSION, com.sheepfromheaven.minenow.miner.Miner.POI);
        miner.getInventory().addItem(new ItemStack(Items.EMERALD, 6));

        helper.succeedWhen(() -> helper.assertTrue(miner.getInventory().countItem(Items.STONE_PICKAXE) == 1,
                "A miner without a pickaxe should buy one the toolsmith crafts from cobblestone and sticks, has "
                        + miner.getInventory().countItem(Items.STONE_PICKAXE) + " and " + miner.getInventory().countItem(Items.EMERALD) + " emeralds"));
    }

    public static void aMinerWithFullPocketsKeepsTheNewPickaxeOnIt(GameTestHelper helper) {
        Villager toolsmith = spawnToolsmith(helper);
        toolsmith.getInventory().addItem(new ItemStack(Items.STONE_PICKAXE));
        BlockPos cart = new BlockPos(19, 1, 12);
        helper.setBlock(cart, com.sheepfromheaven.minenow.miner.Miner.FURNACE_CART.get());
        Villager miner = GameTestHelpers.spawnEmployed(helper, cart.west(), cart, com.sheepfromheaven.minenow.miner.Miner.PROFESSION, com.sheepfromheaven.minenow.miner.Miner.POI);
        helper.setBlock(new BlockPos(16, 1, 6), Blocks.CHEST);
        GameTestHelpers.giveHome(helper, miner, new BlockPos(16, 1, 8));
        miner.getInventory().addItem(new ItemStack(Items.EMERALD, 6));
        for (Item item : new Item[] {Items.DIRT, Items.SAND, Items.GRAVEL, Items.ANDESITE, Items.DIORITE, Items.GRANITE, Items.TUFF}) miner.getInventory().addItem(new ItemStack(item, 64));

        helper.succeedWhen(() -> helper.assertTrue(miner.getInventory().countItem(Items.STONE_PICKAXE) == 1,
                "A miner with full pockets and no pickaxe on it makes room for the one it buys, or it would sit in its chest and the miner wouldn't dig; has "
                        + miner.getInventory().getItems().stream().filter(stack -> !stack.isEmpty()).toList()));
    }

    public static void toolsmithBuysWhatAWoodenPickaxeDug(GameTestHelper helper) {
        Villager toolsmith = spawnToolsmith(helper);
        toolsmith.getInventory().addItem(new ItemStack(Items.EMERALD, 5));
        BlockPos cart = new BlockPos(19, 1, 12);
        helper.setBlock(cart, com.sheepfromheaven.minenow.miner.Miner.FURNACE_CART.get());
        Villager miner = GameTestHelpers.spawnEmployed(helper, cart.west(), cart, com.sheepfromheaven.minenow.miner.Miner.PROFESSION, com.sheepfromheaven.minenow.miner.Miner.POI);
        miner.getInventory().addItem(new ItemStack(Items.COBBLESTONE, Items.WOODEN_PICKAXE.components().getOrDefault(net.minecraft.core.component.DataComponents.MAX_DAMAGE, 59)));

        helper.succeedWhen(() -> helper.assertTrue(toolsmith.getInventory().countItem(Items.COBBLESTONE) > 0,
                "A wooden pickaxe digs enough for the miner to sell the toolsmith a batch of cobblestone, so it can make the next pickaxe; miner has "
                        + miner.getInventory().countItem(Items.COBBLESTONE)));
    }

    public static void aFarmerWithMoneyToSpareTradesUpItsHoe(GameTestHelper helper) {
        Villager toolsmith = spawnToolsmith(helper);
        toolsmith.getInventory().addItem(new ItemStack(Items.STONE_HOE));
        Villager farmer = farmerWithAWoodenHoe(helper, 10);

        helper.succeedWhen(() -> {
            helper.assertTrue(farmer.getInventory().countItem(Items.STONE_HOE) == 1 && farmer.getInventory().countItem(Items.WOODEN_HOE) == 0,
                    "A farmer with money above its reserve buys a better hoe than its wooden one, has " + farmer.getInventory().countItem(Items.STONE_HOE)
                            + " stone and " + farmer.getInventory().countItem(Items.WOODEN_HOE) + " wooden");
            helper.assertTrue(toolsmith.getInventory().countItem(Items.WOODEN_HOE) == 0, "Nobody buys a worn hoe, so the toolsmith doesn't take the old one");
            helper.assertTrue(farmer.getInventory().countItem(Items.EMERALD) == 9, "It pays one emerald, has " + farmer.getInventory().countItem(Items.EMERALD));
        });
    }

    public static void aFarmerWithoutMoneyToSpareKeepsItsWoodenHoe(GameTestHelper helper) {
        Villager toolsmith = spawnToolsmith(helper);
        toolsmith.getInventory().addItem(new ItemStack(Items.STONE_HOE));
        Villager farmer = farmerWithAWoodenHoe(helper, 3);

        helper.runAfterDelay(900, () -> {
            helper.assertTrue(farmer.getInventory().countItem(Items.WOODEN_HOE) == 1 && farmer.getInventory().countItem(Items.EMERALD) == 3,
                    "A farmer with nothing above its reserve keeps the hoe it has");
            helper.succeed();
        });
    }

    public static void aBrokeMinerTakesAStonePickaxeOnCreditForOreItsWoodenOneCantDig(GameTestHelper helper) {
        Villager miner = com.sheepfromheaven.minenow.miner.MinerTests.spawnMiner(helper);
        miner.getInventory().addItem(new ItemStack(Items.WOODEN_PICKAXE));
        miner.getInventory().addItem(new ItemStack(Items.LADDER, 16));
        com.sheepfromheaven.minenow.miner.MinerTests.quarry(helper);
        helper.setBlock(new BlockPos(8, 1, 12), Blocks.IRON_ORE);
        BlockPos table = new BlockPos(12, 4, 16);
        helper.setBlock(table, Blocks.SMITHING_TABLE);
        Villager toolsmith = GameTestHelpers.spawnEmployed(helper, table.east(), table, VillagerProfession.TOOLSMITH, PoiTypes.TOOLSMITH);
        toolsmith.getInventory().addItem(new ItemStack(Items.STONE_PICKAXE));

        helper.succeedWhen(() -> {
            helper.assertTrue(miner.getInventory().countItem(Items.STONE_PICKAXE) == 1,
                    "A miner with no money whose wooden pickaxe can't dig the ore in its mine takes a stone one on credit");
            helper.assertTrue(com.sheepfromheaven.minenow.villager.BuyFromVillagers.debt(miner) == 1, "It owes the toolsmith one emerald");
        });
    }

    public static void aBrokeMinerKeepsItsWoodenPickaxeWhenItDigsEverything(GameTestHelper helper) {
        Villager miner = com.sheepfromheaven.minenow.miner.MinerTests.spawnMiner(helper);
        miner.getInventory().addItem(new ItemStack(Items.WOODEN_PICKAXE));
        miner.getInventory().addItem(new ItemStack(Items.LADDER, 16));
        com.sheepfromheaven.minenow.miner.MinerTests.quarry(helper);
        BlockPos table = new BlockPos(12, 4, 16);
        helper.setBlock(table, Blocks.SMITHING_TABLE);
        Villager toolsmith = GameTestHelpers.spawnEmployed(helper, table.east(), table, VillagerProfession.TOOLSMITH, PoiTypes.TOOLSMITH);
        toolsmith.getInventory().addItem(new ItemStack(Items.STONE_PICKAXE));

        helper.runAfterDelay(900, () -> {
            helper.assertTrue(miner.getInventory().countItem(Items.STONE_PICKAXE) == 0 && com.sheepfromheaven.minenow.villager.BuyFromVillagers.debt(miner) == 0,
                    "A broke miner whose wooden pickaxe digs everything in its mine doesn't go into debt for a better one");
            helper.succeed();
        });
    }

    private static Villager farmerWithAWoodenHoe(GameTestHelper helper, int emeralds) {
        BlockPos composter = new BlockPos(19, 1, 12);
        helper.setBlock(composter, Blocks.COMPOSTER);
        Villager farmer = GameTestHelpers.spawnEmployed(helper, composter.west(), composter, VillagerProfession.FARMER, PoiTypes.FARMER);
        farmer.setVillagerData(farmer.getVillagerData().withLevel(2));
        farmer.getInventory().addItem(new ItemStack(Items.WOODEN_HOE));
        farmer.getInventory().addItem(new ItemStack(Items.EMERALD, emeralds));
        return farmer;
    }

    static Villager spawnToolsmith(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(TABLE, Blocks.SMITHING_TABLE);
        return GameTestHelpers.spawnEmployed(helper, TABLE.east(), TABLE, VillagerProfession.TOOLSMITH, PoiTypes.TOOLSMITH);
    }
}
