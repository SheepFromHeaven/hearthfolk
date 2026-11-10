package com.sheepfromheaven.minenow.smelter;

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
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

import java.util.List;

public final class SmelterTests {
    static final BlockPos BLAST_FURNACE = new BlockPos(5, 1, 12);

    private SmelterTests() {}

    public static void aBlastFurnaceMakesASmelter(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(new BlockPos(10, 1, 12), Blocks.BLAST_FURNACE);
        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(13, 1, 12));

        helper.succeedWhen(() -> helper.assertTrue(villager.getVillagerData().profession().is(Smelter.PROFESSION),
                "A villager at a blast furnace becomes a smelter, it's " + villager.getVillagerData().profession().getRegisteredName()));
    }

    public static void anAnvilMakesAnArmorer(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(new BlockPos(10, 1, 12), Blocks.ANVIL);
        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(13, 1, 12));

        helper.succeedWhen(() -> helper.assertTrue(villager.getVillagerData().profession().is(VillagerProfession.ARMORER),
                "A villager at an anvil becomes an armorer, it's " + villager.getVillagerData().profession().getRegisteredName()));
    }

    public static void smelterSmeltsRawOreInItsBlastFurnace(GameTestHelper helper) {
        Villager smelter = spawnSmelter(helper);
        smelter.getInventory().addItem(new ItemStack(Items.RAW_IRON, 3));
        smelter.getInventory().addItem(new ItemStack(Items.COAL, 2));

        helper.succeedWhen(() -> helper.assertTrue(smelter.getInventory().countItem(Items.IRON_INGOT) == 3,
                "The smelter smelts its raw iron in its blast furnace, has " + smelter.getInventory().countItem(Items.IRON_INGOT) + " ingots"));
    }

    public static void smelterBurnsLogsIntoCharcoalInAFurnaceBesideIt(GameTestHelper helper) {
        Villager smelter = spawnSmelter(helper);
        smelter.getInventory().addItem(new ItemStack(Items.OAK_LOG, 4));
        smelter.getInventory().addItem(new ItemStack(Items.COAL, 1));
        helper.setBlock(BLAST_FURNACE.south(2), Blocks.FURNACE);

        helper.succeedWhen(() -> helper.assertTrue(smelter.getInventory().countItem(Items.CHARCOAL) >= 4,
                "Logs go into the plain furnace beside its blast furnace and come out as charcoal, has " + smelter.getInventory().countItem(Items.CHARCOAL)));
    }

    public static void smelterUsesMoreFurnacesAtHigherLevels(GameTestHelper helper) {
        Villager smelter = spawnSmelter(helper);
        smelter.setVillagerData(smelter.getVillagerData().withLevel(3));
        smelter.getInventory().addItem(new ItemStack(Items.RAW_IRON, 9));
        smelter.getInventory().addItem(new ItemStack(Items.COAL, 3));
        List<BlockPos> furnaces = List.of(BLAST_FURNACE.south(2), BLAST_FURNACE.north(2));
        furnaces.forEach(furnace -> helper.setBlock(furnace, Blocks.BLAST_FURNACE));
        boolean[] usedTogether = {false};
        helper.onEachTick(() -> {
            long busy = furnaces.stream().filter(furnace -> helper.getBlockEntity(furnace, AbstractFurnaceBlockEntity.class).getItem(0).is(Items.RAW_IRON)).count();
            if (busy == furnaces.size()) usedTogether[0] = true;
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(usedTogether[0], "A journeyman shares its ore between its blast furnace and two more beside it");
            helper.assertTrue(smelter.getInventory().countItem(Items.IRON_INGOT) == 9, "...and collects every ingot, has " + smelter.getInventory().countItem(Items.IRON_INGOT));
        });
    }

    public static void smelterBuysRawIronFromTheMiner(GameTestHelper helper) {
        Villager smelter = spawnSmelter(helper);
        smelter.getInventory().addItem(new ItemStack(Items.EMERALD, 6));
        BlockPos cart = new BlockPos(18, 1, 12);
        helper.setBlock(cart, com.sheepfromheaven.minenow.miner.Miner.FURNACE_CART.get());
        Villager miner = GameTestHelpers.spawnEmployed(helper, cart.west(), cart, com.sheepfromheaven.minenow.miner.Miner.PROFESSION, com.sheepfromheaven.minenow.miner.Miner.POI);
        miner.getInventory().addItem(new ItemStack(Items.WOODEN_PICKAXE));
        miner.getInventory().addItem(new ItemStack(Items.RAW_IRON, 20));
        miner.getInventory().addItem(new ItemStack(Items.COAL, 4));

        helper.succeedWhen(() -> {
            helper.assertTrue(smelter.getInventory().countItem(Items.RAW_IRON) + smelter.getInventory().countItem(Items.IRON_INGOT) >= 5,
                    "The smelter buys the miner's raw iron, has " + smelter.getInventory().countItem(Items.RAW_IRON) + " raw, " + smelter.getInventory().countItem(Items.IRON_INGOT) + " ingots");
            helper.assertTrue(miner.getInventory().countItem(Items.IRON_INGOT) == 0, "The miner no longer smelts its ore itself");
        });
    }

    public static void aBrokeSmelterGetsOreOnCreditAndPaysItBack(GameTestHelper helper) {
        Villager smelter = spawnSmelter(helper);
        smelter.getInventory().addItem(new ItemStack(Items.EMERALD, 2));
        smelter.getInventory().addItem(new ItemStack(Items.COAL, 4));
        BlockPos cart = new BlockPos(18, 1, 12);
        helper.setBlock(cart, com.sheepfromheaven.minenow.miner.Miner.FURNACE_CART.get());
        Villager miner = GameTestHelpers.spawnEmployed(helper, cart.west(), cart, com.sheepfromheaven.minenow.miner.Miner.PROFESSION, com.sheepfromheaven.minenow.miner.Miner.POI);
        miner.getInventory().addItem(new ItemStack(Items.WOODEN_PICKAXE));
        miner.getInventory().addItem(new ItemStack(Items.RAW_IRON, 20));
        boolean[] gotOre = {false};
        helper.onEachTick(() -> {
            if (smelter.getInventory().countItem(Items.RAW_IRON) + smelter.getInventory().countItem(Items.IRON_INGOT) > 0 && !gotOre[0]) {
                gotOre[0] = true;
                smelter.getInventory().addItem(new ItemStack(Items.EMERALD, 3));
            }
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(gotOre[0], "A smelter with only its food money still gets raw iron from the miner, on credit");
            helper.assertTrue(miner.getInventory().countItem(Items.EMERALD) >= 1, "...and pays the miner back once it has money, miner has " + miner.getInventory().countItem(Items.EMERALD));
        });
    }

    static Villager spawnSmelter(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(BLAST_FURNACE, Blocks.BLAST_FURNACE);
        return GameTestHelpers.spawnEmployed(helper, BLAST_FURNACE.east(), BLAST_FURNACE, Smelter.PROFESSION, PoiTypes.ARMORER);
    }
}
