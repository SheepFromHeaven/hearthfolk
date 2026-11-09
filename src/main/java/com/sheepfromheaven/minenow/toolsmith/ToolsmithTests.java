package com.sheepfromheaven.minenow.toolsmith;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
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

    public static void minerBuysAWoodenPickaxeTheToolsmithMadeFromPlanks(GameTestHelper helper) {
        Villager toolsmith = spawnToolsmith(helper);
        toolsmith.getInventory().addItem(new ItemStack(Items.OAK_PLANKS, 8));
        toolsmith.getInventory().addItem(new ItemStack(Items.STICK, 6));
        BlockPos cart = new BlockPos(19, 1, 12);
        helper.setBlock(cart, com.sheepfromheaven.minenow.miner.Miner.FURNACE_CART.get());
        Villager miner = GameTestHelpers.spawnEmployed(helper, cart.west(), cart, com.sheepfromheaven.minenow.miner.Miner.PROFESSION, com.sheepfromheaven.minenow.miner.Miner.POI);
        miner.getInventory().addItem(new ItemStack(Items.EMERALD, 6));

        helper.succeedWhen(() -> helper.assertTrue(miner.getInventory().countItem(Items.WOODEN_PICKAXE) == 1,
                "Without cobblestone the toolsmith still makes wooden tools from planks, so a miner gets a pickaxe; has "
                        + miner.getInventory().countItem(Items.WOODEN_PICKAXE)));
    }

    static Villager spawnToolsmith(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(TABLE, Blocks.SMITHING_TABLE);
        return GameTestHelpers.spawnEmployed(helper, TABLE.east(), TABLE, VillagerProfession.TOOLSMITH, PoiTypes.TOOLSMITH);
    }
}
