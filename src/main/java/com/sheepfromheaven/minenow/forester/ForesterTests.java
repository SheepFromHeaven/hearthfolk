package com.sheepfromheaven.minenow.forester;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

public final class ForesterTests {
    private static final BlockPos LOG = new BlockPos(10, 1, 12);
    private static final BlockPos POT = LOG.above();

    private ForesterTests() {}

    public static void pottingASaplingOnALogMakesANursery(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        helper.setBlock(LOG, Blocks.SPRUCE_LOG);
        helper.setBlock(POT, Blocks.FLOWER_POT);
        ServerPlayer player = playerHolding(helper, Items.SPRUCE_SAPLING);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);

        useOn(helper, player, POT);

        helper.assertTrue(helper.getBlockState(POT).is(Forester.NURSERY.get()), "A sapling potted on a log makes a nursery, got " + helper.getBlockState(POT));
        helper.assertTrue(Forester.tree(helper.getBlockState(POT)) == NurseryTree.SPRUCE, "The nursery grows what was potted");
        helper.assertTrue(player.getMainHandItem().isEmpty(), "The sapling went into the pot, still holding " + player.getMainHandItem() + ", creative " + player.isCreative());
        helper.succeed();
    }

    public static void aSaplingPottedOffALogStaysAPlainPottedSapling(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        helper.setBlock(LOG, Blocks.STONE);
        helper.setBlock(POT, Blocks.FLOWER_POT);
        ServerPlayer player = playerHolding(helper, Items.OAK_SAPLING);

        useOn(helper, player, POT);

        helper.assertTrue(helper.getBlockState(POT).is(Blocks.POTTED_OAK_SAPLING), "Anywhere else it's vanilla's potted sapling, got " + helper.getBlockState(POT));
        helper.succeed();
    }

    public static void aNurseryWithoutItsLogIsAPlainPottedSapling(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        helper.setBlock(LOG, Blocks.OAK_LOG);
        helper.setBlock(POT, Forester.nursery(NurseryTree.BIRCH));
        helper.setBlock(LOG, Blocks.STONE);

        helper.assertTrue(helper.getBlockState(POT).is(Blocks.POTTED_BIRCH_SAPLING), "Without the log under it the nursery is just a potted sapling, got " + helper.getBlockState(POT));
        helper.succeed();
    }

    public static void breakingANurseryGivesBackThePotAndTheSapling(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        helper.setBlock(LOG, Blocks.OAK_LOG);
        helper.setBlock(POT, Forester.nursery(NurseryTree.CHERRY));

        List<ItemStack> drops = Block.getDrops(helper.getBlockState(POT), helper.getLevel(), helper.absolutePos(POT), null);

        helper.assertTrue(drops.stream().anyMatch(stack -> stack.is(Items.FLOWER_POT)), "The pot drops, got " + drops);
        helper.assertTrue(drops.stream().anyMatch(stack -> stack.is(Items.CHERRY_SAPLING)), "So does its sapling, got " + drops);
        helper.succeed();
    }

    public static void unemployedVillagerBecomesAForesterAtAFreeNursery(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(LOG, Blocks.OAK_LOG);
        helper.setBlock(POT, Forester.nursery(NurseryTree.OAK));
        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(13, 1, 12));

        helper.succeedWhen(() -> helper.assertTrue(villager.getVillagerData().profession().is(Forester.PROFESSION),
                "An unemployed villager should take a free nursery and become a forester"));
    }

    public static void foresterFellsAGrownTreeAndReplantsIt(GameTestHelper helper) {
        Villager forester = spawnForester(helper);
        forester.getInventory().addItem(new ItemStack(Items.STONE_AXE));
        forester.getInventory().addItem(new ItemStack(Items.OAK_SAPLING));
        BlockPos base = new BlockPos(12, 1, 12);
        tree(helper, base, Blocks.DIRT);

        helper.succeedWhen(() -> {
            helper.assertTrue(logs(helper, base) == 0, "The forester fells the whole tree, " + logs(helper, base) + " logs left");
            helper.assertTrue(forester.getInventory().countItem(Items.OAK_LOG) >= 4, "The logs go into its inventory, has " + forester.getInventory().countItem(Items.OAK_LOG));
            helper.assertTrue(helper.getBlockState(base).is(Blocks.OAK_SAPLING), "It replants the stump with its nursery's sapling, there's " + helper.getBlockState(base));
        });
    }

    public static void foresterLeavesATreeStandingOnCobblestone(GameTestHelper helper) {
        Villager forester = spawnForester(helper);
        forester.getInventory().addItem(new ItemStack(Items.STONE_AXE));
        BlockPos base = new BlockPos(12, 1, 12);
        tree(helper, base, Blocks.COBBLESTONE);

        helper.runAfterDelay(600, () -> {
            helper.assertTrue(logs(helper, base) == 4, "Cobblestone under a tree means keep it, " + logs(helper, base) + " logs left");
            helper.succeed();
        });
    }

    public static void foresterLeavesALogPostWithoutLeavesAlone(GameTestHelper helper) {
        Villager forester = spawnForester(helper);
        forester.getInventory().addItem(new ItemStack(Items.STONE_AXE));
        BlockPos base = new BlockPos(12, 1, 12);
        helper.setBlock(base.below(), Blocks.DIRT);
        for (int y = 0; y < 3; y++) helper.setBlock(base.above(y), Blocks.OAK_LOG);

        helper.runAfterDelay(600, () -> {
            helper.assertTrue(logs(helper, base) == 3, "Logs without leaves of their own are a build, not a tree, " + logs(helper, base) + " left");
            helper.succeed();
        });
    }

    private static Villager spawnForester(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        BlockPos nursery = new BlockPos(5, 2, 12);
        helper.setBlock(nursery.below(), Blocks.OAK_LOG);
        helper.setBlock(nursery, Forester.nursery(NurseryTree.OAK));
        return GameTestHelpers.spawnEmployed(helper, nursery.east(2).below(), nursery, Forester.PROFESSION, Forester.POI);
    }

    /** A small oak: four logs on {@code ground} with a crown of natural (non-persistent) leaves. */
    private static void tree(GameTestHelper helper, BlockPos base, Block ground) {
        helper.setBlock(base.below(), ground);
        for (int y = 0; y < 4; y++) helper.setBlock(base.above(y), Blocks.OAK_LOG);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int y = 2; y <= 4; y++) {
                    BlockPos pos = base.offset(dx, y, dz);
                    if (helper.getBlockState(pos).isAir()) helper.setBlock(pos, Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.DISTANCE, 1));
                }
            }
        }
    }

    private static int logs(GameTestHelper helper, BlockPos base) {
        int logs = 0;
        for (int y = 0; y < 5; y++) if (helper.getBlockState(base.above(y)).is(net.minecraft.tags.BlockTags.LOGS)) logs++;
        return logs;
    }

    private static ServerPlayer playerHolding(GameTestHelper helper, Item item) {
        @SuppressWarnings("removal")
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
        return player;
    }

    private static void useOn(GameTestHelper helper, ServerPlayer player, BlockPos pos) {
        BlockPos absolute = helper.absolutePos(pos);
        var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(absolute), net.minecraft.core.Direction.UP, absolute, false);
        player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
    }
}
