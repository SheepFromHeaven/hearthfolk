package com.sheepfromheaven.minenow.fletcher;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

public final class FletcherTests {
    private static final BlockPos TABLE = new BlockPos(5, 1, 12);

    private FletcherTests() {}

    public static void fletcherMakesArrowsFromFlintSticksAndFeathers(GameTestHelper helper) {
        Villager fletcher = spawnFletcher(helper);
        fletcher.getInventory().addItem(new ItemStack(Items.FLINT, 2));
        fletcher.getInventory().addItem(new ItemStack(Items.STICK, 2));
        fletcher.getInventory().addItem(new ItemStack(Items.FEATHER, 2));

        helper.succeedWhen(() -> helper.assertTrue(fletcher.getInventory().countItem(Items.ARROW) == 8,
                "Each flint, stick and feather make four arrows, has " + fletcher.getInventory().countItem(Items.ARROW)));
    }

    public static void fletcherMakesSticksFromAnyPlanks(GameTestHelper helper) {
        Villager fletcher = spawnFletcher(helper);
        fletcher.getInventory().addItem(new ItemStack(Items.SPRUCE_PLANKS, 2));

        helper.succeedWhen(() -> helper.assertTrue(fletcher.getInventory().countItem(Items.STICK) == 4,
                "Two spruce planks make four sticks, has " + fletcher.getInventory().countItem(Items.STICK)));
    }

    public static void aJourneymanFletcherMakesBows(GameTestHelper helper) {
        Villager fletcher = spawnFletcher(helper);
        fletcher.setVillagerData(fletcher.getVillagerData().withLevel(3));
        fletcher.getInventory().addItem(new ItemStack(Items.STICK, 3));
        fletcher.getInventory().addItem(new ItemStack(Items.STRING, 3));

        helper.succeedWhen(() -> helper.assertTrue(fletcher.getInventory().countItem(Items.BOW) == 1,
                "A journeyman strings a bow from three sticks and three string, has " + fletcher.getInventory().countItem(Items.BOW)));
    }

    public static void aNoviceFletcherMakesNoBows(GameTestHelper helper) {
        Villager fletcher = spawnFletcher(helper);
        fletcher.getInventory().addItem(new ItemStack(Items.STICK, 3));
        fletcher.getInventory().addItem(new ItemStack(Items.STRING, 3));

        helper.runAfterDelay(400, () -> {
            helper.assertTrue(fletcher.getInventory().countItem(Items.BOW) == 0, "Bows unlock at journeyman");
            helper.succeed();
        });
    }

    public static void fletcherPractisesAtATargetAndCollectsItsArrows(GameTestHelper helper) {
        Villager fletcher = spawnFletcher(helper);
        fletcher.setVillagerData(fletcher.getVillagerData().withLevel(5));
        fletcher.getInventory().addItem(new ItemStack(Items.BOW));
        fletcher.getInventory().addItem(new ItemStack(Items.ARROW, 8));
        BlockPos target = new BlockPos(17, 2, 12);
        helper.setBlock(target.below(), Blocks.STONE);
        helper.setBlock(target, Blocks.TARGET);
        boolean[] hit = {false};
        int[] fewest = {8};
        helper.onEachTick(() -> {
            if (helper.getBlockState(target).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWER) > 0) hit[0] = true;
            fewest[0] = Math.min(fewest[0], fletcher.getInventory().countItem(Items.ARROW));
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(hit[0], "The fletcher shoots at the target, arrows " + fletcher.getInventory().countItem(Items.ARROW) + ", fewest " + fewest[0]
                    + ", at " + helper.relativePos(fletcher.blockPosition()).toShortString() + ", running " + fletcher.getBrain().getRunningBehaviors().stream().map(b -> b.getClass().getSimpleName()).filter(n -> !n.isEmpty()).toList());
            helper.assertTrue(fewest[0] < 8, "...with its own arrows");
            helper.assertTrue(fletcher.getInventory().countItem(Items.ARROW) == 8, "...and collects them again, has " + fletcher.getInventory().countItem(Items.ARROW));
            helper.assertTrue(fletcher.getInventory().countItem(Items.BOW) == 1, "It keeps its bow");
        });
    }

    public static void minerBuysAWoodenPickaxeTheFletcherMadeFromPlanks(GameTestHelper helper) {
        Villager fletcher = spawnFletcher(helper);
        fletcher.getInventory().addItem(new ItemStack(Items.OAK_PLANKS, 24));
        BlockPos cart = new BlockPos(19, 1, 12);
        helper.setBlock(cart, com.sheepfromheaven.minenow.miner.Miner.FURNACE_CART.get());
        Villager miner = GameTestHelpers.spawnEmployed(helper, cart.west(), cart, com.sheepfromheaven.minenow.miner.Miner.PROFESSION, com.sheepfromheaven.minenow.miner.Miner.POI);
        miner.getInventory().addItem(new ItemStack(Items.EMERALD, 6));

        helper.succeedWhen(() -> helper.assertTrue(miner.getInventory().countItem(Items.WOODEN_PICKAXE) == 1,
                "The fletcher makes wooden tools from planks and its own sticks, so a miner gets a pickaxe without cobblestone; has "
                        + miner.getInventory().countItem(Items.WOODEN_PICKAXE) + ", fletcher holds " + fletcher.getInventory().getItems().stream().filter(st -> !st.isEmpty()).toList()));
    }

    public static void aMinerWithSticksInItsChestBuysNoMore(GameTestHelper helper) {
        Villager fletcher = spawnFletcher(helper);
        Villager miner = minerWithAChestOfSticks(helper);
        helper.runAfterDelay(200, () -> fletcher.getInventory().addItem(new ItemStack(Items.STICK, 64)));

        helper.runAfterDelay(900, () -> {
            helper.assertTrue(miner.getInventory().countItem(Items.EMERALD) == 9 && miner.getInventory().countItem(Items.STICK) == 0,
                    "A miner with a lot of sticks in its chest buys no more, has " + miner.getInventory().getItems().stream().filter(st -> !st.isEmpty()).toList());
            helper.succeed();
        });
    }

    public static void aMinerTakesItsSticksBackOutOfItsChestWhenUnloading(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        Villager miner = minerWithAChestOfSticks(helper);
        miner.getInventory().addItem(new ItemStack(Items.DIRT, 64));
        miner.getInventory().addItem(new ItemStack(Items.DIRT, 64));

        helper.succeedWhen(() -> helper.assertTrue(miner.getInventory().countItem(Items.STICK) == 16,
                "Unloading at its chest it takes back the sticks it keeps (16) for its torches, has " + miner.getInventory().countItem(Items.STICK)));
    }

    private static Villager minerWithAChestOfSticks(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        BlockPos cart = new BlockPos(19, 1, 12);
        helper.setBlock(cart, com.sheepfromheaven.minenow.miner.Miner.FURNACE_CART.get());
        Villager miner = GameTestHelpers.spawnEmployed(helper, cart.west(), cart, com.sheepfromheaven.minenow.miner.Miner.PROFESSION, com.sheepfromheaven.minenow.miner.Miner.POI);
        BlockPos chest = new BlockPos(16, 1, 6);
        helper.setBlock(chest, Blocks.CHEST);
        GameTestHelpers.giveHome(helper, miner, new BlockPos(16, 1, 8));
        GameTestHelpers.fillOnceClaimed(helper, () -> com.sheepfromheaven.minenow.villager.HomeStorage.storageOf(miner).isPresent(), chest, new ItemStack(Items.STICK, 40));
        miner.getInventory().addItem(new ItemStack(Items.STONE_PICKAXE));
        miner.getInventory().addItem(new ItemStack(Items.LADDER, 32));
        miner.getInventory().addItem(new ItemStack(Items.TORCH, 32));
        helper.runAfterDelay(200, () -> miner.getInventory().addItem(new ItemStack(Items.EMERALD, 9)));
        return miner;
    }

    public static void aMinerOutOfSticksBuysALot(GameTestHelper helper) {
        Villager fletcher = spawnFletcher(helper);
        fletcher.getInventory().addItem(new ItemStack(Items.STICK, 64));
        BlockPos cart = new BlockPos(19, 1, 12);
        helper.setBlock(cart, com.sheepfromheaven.minenow.miner.Miner.FURNACE_CART.get());
        Villager miner = GameTestHelpers.spawnEmployed(helper, cart.west(), cart, com.sheepfromheaven.minenow.miner.Miner.PROFESSION, com.sheepfromheaven.minenow.miner.Miner.POI);
        miner.getInventory().addItem(new ItemStack(Items.STONE_PICKAXE));
        miner.getInventory().addItem(new ItemStack(Items.LADDER, 32));
        miner.getInventory().addItem(new ItemStack(Items.TORCH, 32));
        miner.getInventory().addItem(new ItemStack(Items.EMERALD, 10));

        helper.succeedWhen(() -> helper.assertTrue(miner.getInventory().countItem(Items.STICK) > 0, "A miner out of sticks buys a lot from the fletcher"));
    }

    static Villager spawnFletcher(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(TABLE, Blocks.FLETCHING_TABLE);
        return GameTestHelpers.spawnEmployed(helper, TABLE.east(), TABLE, VillagerProfession.FLETCHER, PoiTypes.FLETCHER);
    }
}
