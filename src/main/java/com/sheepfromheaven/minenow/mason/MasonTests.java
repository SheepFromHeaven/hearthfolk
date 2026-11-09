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

import java.util.HashSet;
import java.util.Set;

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
            Set<Block> used = new HashSet<>();
            int stone = 0;
            for (int x = 6; x < 18; x++) {
                var state = helper.getBlockState(new BlockPos(x, 0, 14));
                helper.assertFalse(state.is(Blocks.DIRT_PATH), "The whole path gets paved, at x " + x + " it's still a dirt path");
                helper.assertTrue(state.is(TIER_2) || state.is(TIER_3), "...with road blocks, at x " + x + " it's " + state.getBlock().getName().getString());
                used.add(state.getBlock());
                if (state.is(TIER_3)) stone++;
            }
            helper.assertTrue(stone >= 6, "A journeyman lays mostly stone bricks and slabs, " + stone + " of 12");
            helper.assertTrue(used.size() >= 2, "...in an irregular mix rather than one block, used " + used);
        });
    }

    public static void aMasonFollowsItsRoadsAndLeavesStrayPathsAlone(GameTestHelper helper) {
        Villager mason = spawnMason(helper, 2);
        mason.getInventory().addItem(new ItemStack(Items.COBBLESTONE, 64));
        mason.getInventory().addItem(new ItemStack(Items.GRAVEL, 32));
        for (int x = 6; x <= 20; x++) helper.setBlock(new BlockPos(x, 0, 14), Blocks.DIRT_PATH);
        for (int z = 15; z <= 21; z++) helper.setBlock(new BlockPos(20, 0, z), Blocks.DIRT_PATH);
        BlockPos stray = new BlockPos(14, 0, 20);
        helper.setBlock(stray, Blocks.DIRT_PATH);
        helper.setBlock(STONECUTTER.north(), Blocks.REDSTONE_BLOCK);

        helper.succeedWhen(() -> {
            for (int z = 15; z <= 21; z++) {
                var state = helper.getBlockState(new BlockPos(20, 0, z));
                helper.assertTrue(state.is(TIER_2), "It follows the road around the corner to its far end, at z " + z + " it's " + state.getBlock().getName().getString());
            }
            helper.assertTrue(helper.getBlockState(stray).is(Blocks.DIRT_PATH), "A path not joined to its roads is left alone");
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
