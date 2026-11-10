package com.sheepfromheaven.minenow.mason;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;


public final class MasonTests {
    private static final BlockPos STONECUTTER = new BlockPos(5, 1, 12);
    private static final TagKey<Block> TIER_2 = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("minenow", "villager_roads/tier_2"));
    private static final TagKey<Block> TIER_3 = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("minenow", "villager_roads/tier_3"));

    private MasonTests() {}

    public static void aNoviceMasonCutsCobblestone(GameTestHelper helper) {
        Villager mason = spawnMason(helper, 1);
        mason.getInventory().addItem(new ItemStack(Items.COBBLESTONE, 48));

        helper.succeedWhen(() -> helper.assertTrue(mason.getInventory().countItem(Items.COBBLESTONE_SLAB) >= 4,
                "At its stonecutter it cuts cobblestone, two slabs from each, has " + mason.getInventory().countItem(Items.COBBLESTONE_SLAB) + " slabs"));
    }

    public static void anApprenticeMasonCutsStoneBricks(GameTestHelper helper) {
        Villager mason = spawnMason(helper, 2);
        mason.getInventory().addItem(new ItemStack(Items.STONE, 48));

        helper.succeedWhen(() -> helper.assertTrue(mason.getInventory().countItem(Items.STONE_BRICKS) >= 1,
                "An apprentice cuts stone into stone bricks, has " + mason.getInventory().countItem(Items.STONE_BRICKS)));
    }

    public static void smelterSmeltsCobblestoneIntoStone(GameTestHelper helper) {
        Villager smelter = com.sheepfromheaven.minenow.smelter.SmelterTestAccess.spawnSmelter(helper);
        smelter.getInventory().addItem(new ItemStack(Items.COBBLESTONE, 4));
        smelter.getInventory().addItem(new ItemStack(Items.COAL, 1));
        helper.setBlock(com.sheepfromheaven.minenow.smelter.SmelterTestAccess.BLAST_FURNACE.south(2), Blocks.FURNACE);

        helper.succeedWhen(() -> helper.assertTrue(smelter.getInventory().countItem(Items.STONE) >= 4,
                "The smelter smelts cobblestone into stone for the mason, has " + smelter.getInventory().countItem(Items.STONE)));
    }

    public static void aMasonPavesDirtPathsWhileItsStonecutterIsPowered(GameTestHelper helper) {
        Villager mason = spawnMason(helper, 2);
        mason.getInventory().addItem(new ItemStack(Items.COBBLESTONE, 32));
        mason.getInventory().addItem(new ItemStack(Items.GRAVEL, 16));
        road(helper, 8, 8);
        helper.setBlock(new BlockPos(9, 0, 15), Blocks.GRASS_BLOCK);
        helper.setBlock(STONECUTTER.north(), Blocks.REDSTONE_BLOCK);

        helper.succeedWhen(() -> {
            for (int x = 8; x < 16; x++) {
                var state = helper.getBlockState(new BlockPos(x, 0, 14));
                helper.assertTrue(state.is(TIER_2), "The path is paved with cobblestone and gravel, at x " + x + " it's " + state.getBlock().getName().getString());
            }
            helper.assertTrue(helper.getBlockState(new BlockPos(9, 0, 15)).is(Blocks.GRASS_BLOCK), "It only paves paths, never the grass beside them");
        });
    }

    public static void aMasonLeavesPathsAloneWithoutASignal(GameTestHelper helper) {
        Villager mason = spawnMason(helper, 2);
        mason.getInventory().addItem(new ItemStack(Items.COBBLESTONE, 32));
        road(helper, 8, 8);

        helper.runAfterDelay(600, () -> {
            for (int x = 8; x < 16; x++) helper.assertTrue(helper.getBlockState(new BlockPos(x, 0, 14)).is(Blocks.DIRT_PATH), "Without a redstone signal into its stonecutter it doesn't pave, at x " + x);
            helper.succeed();
        });
    }

    public static void aJourneymanPavesANaturalStoneRoad(GameTestHelper helper) {
        Villager mason = spawnMason(helper, 3);
        mason.getInventory().addItem(new ItemStack(Items.COBBLESTONE, 32));
        mason.getInventory().addItem(new ItemStack(Items.GRAVEL, 16));
        mason.getInventory().addItem(new ItemStack(Items.STONE_BRICKS, 32));
        mason.getInventory().addItem(new ItemStack(Items.STONE_BRICK_SLAB, 32));
        road(helper, 6, 12);
        helper.setBlock(STONECUTTER.north(), Blocks.REDSTONE_BLOCK);

        helper.succeedWhen(() -> {
            for (int x = 6; x < 18; x++) {
                BlockPos spot = new BlockPos(x, 0, 14);
                var state = helper.getBlockState(spot);
                helper.assertFalse(state.is(Blocks.DIRT_PATH), "The whole path gets paved, at x " + x + " it's still a dirt path");
                RoadPattern.Material wanted = RoadPattern.material(net.minecraft.util.Mth.getSeed(helper.absolutePos(spot)), true);
                boolean stone = wanted == RoadPattern.Material.STONE_BRICKS || wanted == RoadPattern.Material.STONE_BRICK_SLAB;
                helper.assertTrue(state.is(stone ? TIER_3 : TIER_2), "A journeyman lays stone where its pattern wants stone and leaves the rest cobblestone or gravel, at x " + x + " it wants " + wanted + " and laid " + state.getBlock().getName().getString());
            }
        });
    }

    public static void inItsFreeTimeAMasonPavesOnlyAroundItsWorkshop(GameTestHelper helper) {
        Villager mason = spawnMason(helper, 2);
        mason.getInventory().addItem(new ItemStack(Items.COBBLESTONE, 64));
        mason.getInventory().addItem(new ItemStack(Items.GRAVEL, 32));
        for (int x = 6; x <= 21; x++) helper.setBlock(new BlockPos(x, 0, 14), Blocks.DIRT_PATH);
        BlockPos stray = new BlockPos(12, 0, 20);
        helper.setBlock(stray, Blocks.DIRT_PATH);
        helper.setBlock(STONECUTTER.north(), Blocks.REDSTONE_BLOCK);

        helper.succeedWhen(() -> {
            for (int x = 6; x <= 17; x++) {
                var state = helper.getBlockState(new BlockPos(x, 0, 14));
                helper.assertTrue(state.is(TIER_2), "It paves the road within 12 of its stonecutter, at x " + x + " it's " + state.getBlock().getName().getString());
            }
            helper.assertTrue(helper.getBlockState(stray).is(TIER_2), "...and a path on its own there too");
        });
        helper.runAfterDelay(1200, () -> {
            for (int x = 19; x <= 21; x++) helper.assertTrue(helper.getBlockState(new BlockPos(x, 0, 14)).is(Blocks.DIRT_PATH), "Further out it paves nothing nobody paid for, at x " + x);
        });
    }

    public static void aVillagerPaysTheMasonToPaveTheWholePathToItsHome(GameTestHelper helper) {
        Villager mason = spawnMason(helper, 2);
        mason.getInventory().addItem(new ItemStack(Items.COBBLESTONE, 64));
        mason.getInventory().addItem(new ItemStack(Items.GRAVEL, 32));
        for (int x = 6; x <= 21; x++) helper.setBlock(new BlockPos(x, 0, 14), Blocks.DIRT_PATH);
        for (int z = 15; z <= 20; z++) helper.setBlock(new BlockPos(21, 0, z), Blocks.DIRT_PATH);
        helper.setBlock(STONECUTTER.north(), Blocks.REDSTONE_BLOCK);
        Villager neighbour = GameTestHelpers.spawnWithHome(helper, new BlockPos(19, 1, 20), new BlockPos(19, 1, 21));
        neighbour.getInventory().addItem(new ItemStack(Items.EMERALD, 20));
        neighbour.getInventory().addItem(new ItemStack(Items.BREAD, 12));

        helper.succeedWhen(() -> {
            for (int x = 6; x <= 21; x++) {
                var state = helper.getBlockState(new BlockPos(x, 0, 14));
                helper.assertTrue(state.is(TIER_2), "Paid, it paves the whole path to the villager's home, however far from its workshop; at x " + x + " it's "
                        + state.getBlock().getName().getString() + ", the villager has " + com.sheepfromheaven.minenow.villager.Economy.emeralds(helper.getLevel(), neighbour) + " emeralds");
            }
            for (int z = 15; z <= 20; z++) helper.assertTrue(helper.getBlockState(new BlockPos(21, 0, z)).is(TIER_2), "...round the corner to its door, at z " + z);
            helper.assertTrue(mason.getInventory().countItem(Items.EMERALD) >= 1 && com.sheepfromheaven.minenow.villager.Economy.emeralds(helper.getLevel(), neighbour) < 20,
                    "The villager paid the mason for the stretch beyond its own (an emerald for ten blocks), mason has " + mason.getInventory().countItem(Items.EMERALD));
        });
    }

    public static void anUnpoweredMasonTakesNoPavingJobs(GameTestHelper helper) {
        Villager mason = spawnMason(helper, 2);
        mason.getInventory().addItem(new ItemStack(Items.COBBLESTONE, 64));
        for (int x = 6; x <= 21; x++) helper.setBlock(new BlockPos(x, 0, 14), Blocks.DIRT_PATH);
        Villager neighbour = GameTestHelpers.spawnWithHome(helper, new BlockPos(19, 1, 17), new BlockPos(19, 1, 16));
        neighbour.getInventory().addItem(new ItemStack(Items.EMERALD, 20));
        neighbour.getInventory().addItem(new ItemStack(Items.BREAD, 12));

        helper.runAfterDelay(900, () -> {
            helper.assertTrue(mason.getInventory().countItem(Items.EMERALD) == 0, "Without a redstone signal into its stonecutter it takes no money for paving");
            helper.succeed();
        });
    }

    /** A dirt path along z 14 from x {@code from}, sunk into the floor. */
    private static void road(GameTestHelper helper, int from, int length) {
        for (int x = from; x < from + length; x++) helper.setBlock(new BlockPos(x, 0, 14), Blocks.DIRT_PATH);
    }

    static Villager spawnMason(GameTestHelper helper, int level) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(STONECUTTER, Blocks.STONECUTTER);
        Villager mason = GameTestHelpers.spawnEmployed(helper, STONECUTTER.east(), STONECUTTER, VillagerProfession.MASON, PoiTypes.MASON);
        mason.setVillagerData(mason.getVillagerData().withLevel(level));
        return mason;
    }
}
