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

    public static void foresterGathersWhatFallsFromItsTrees(GameTestHelper helper) {
        Villager forester = spawnForester(helper);
        forester.getInventory().addItem(new ItemStack(Items.STONE_AXE));
        drop(helper, new BlockPos(12, 1, 8), new ItemStack(Items.OAK_SAPLING, 2));
        drop(helper, new BlockPos(13, 1, 15), new ItemStack(Items.APPLE));
        drop(helper, new BlockPos(9, 1, 17), new ItemStack(Items.STICK, 2));

        helper.succeedWhen(() -> {
            helper.assertTrue(forester.getInventory().countItem(Items.OAK_SAPLING) == 2, "It gathers the saplings in its grove, has " + forester.getInventory().countItem(Items.OAK_SAPLING));
            helper.assertTrue(forester.getInventory().countItem(Items.APPLE) == 1, "...the apples");
            helper.assertTrue(forester.getInventory().countItem(Items.STICK) == 2, "...and the sticks");
        });
    }

    public static void foresterMakesPlanksAndStrippedLogs(GameTestHelper helper) {
        Villager forester = spawnForester(helper);
        forester.getInventory().addItem(new ItemStack(Items.STONE_AXE));
        forester.getInventory().addItem(new ItemStack(Items.OAK_LOG, 20));

        helper.succeedWhen(() -> {
            int planks = forester.getInventory().countItem(Items.OAK_PLANKS);
            int stripped = forester.getInventory().countItem(Items.STRIPPED_OAK_LOG);
            helper.assertTrue(planks >= 4 && stripped >= 1, "At its nursery it saws planks and strips logs with its axe, has " + planks + " planks, " + stripped + " stripped logs");
            helper.assertTrue(forester.getInventory().countItem(Items.STICK) == 0 && forester.getInventory().countItem(Items.LADDER) == 0, "Sticks and ladders are the fletcher's fine work");
            helper.assertTrue(forester.getInventory().getItem(com.sheepfromheaven.minenow.villager.Tools.slot(forester)).getDamageValue() >= stripped, "Stripping wears its axe");
        });
    }

    public static void fletcherTurnsPlanksIntoSticksAndLadders(GameTestHelper helper) {
        Villager fletcher = spawnFletcher(helper, new BlockPos(5, 1, 12));
        fletcher.getInventory().addItem(new ItemStack(Items.OAK_PLANKS, 24));

        helper.succeedWhen(() -> helper.assertTrue(fletcher.getInventory().countItem(Items.LADDER) >= 3,
                "At its fletching table it makes sticks from planks and ladders from sticks, has " + fletcher.getInventory().countItem(Items.LADDER) + " ladders, "
                        + fletcher.getInventory().countItem(Items.STICK) + " sticks, " + fletcher.getInventory().countItem(Items.OAK_PLANKS) + " planks"));
    }

    public static void minerBuysLaddersFromTheFletcher(GameTestHelper helper) {
        Villager fletcher = spawnFletcher(helper, new BlockPos(5, 1, 12));
        fletcher.getInventory().addItem(new ItemStack(Items.LADDER, 20));
        BlockPos cart = new BlockPos(18, 1, 12);
        helper.setBlock(cart, com.sheepfromheaven.minenow.miner.Miner.FURNACE_CART.get());
        Villager miner = GameTestHelpers.spawnEmployed(helper, cart.west(), cart, com.sheepfromheaven.minenow.miner.Miner.PROFESSION, com.sheepfromheaven.minenow.miner.Miner.POI);
        miner.getInventory().addItem(new ItemStack(Items.WOODEN_PICKAXE));
        miner.getInventory().addItem(new ItemStack(Items.EMERALD, 5));

        helper.succeedWhen(() -> {
            helper.assertTrue(miner.getInventory().countItem(Items.LADDER) >= 8, "The miner buys its ladders from the fletcher, has " + miner.getInventory().countItem(Items.LADDER));
            helper.assertTrue(fletcher.getInventory().countItem(Items.EMERALD) >= 1, "The fletcher is paid");
        });
    }

    public static void craftedGoodsAreSoldInWholeEmeraldBatches(GameTestHelper helper) {
        Villager forester = spawnForester(helper);
        forester.getInventory().addItem(new ItemStack(Items.STONE_AXE));
        forester.getInventory().addItem(new ItemStack(Items.OAK_PLANKS, 40));
        forester.getInventory().addItem(new ItemStack(Items.STRIPPED_OAK_LOG, 5));
        @SuppressWarnings("removal")
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        player.interactOn(forester, InteractionHand.MAIN_HAND, forester.position());

        String offers = forester.getOffers().stream().map(o -> o.getCostA() + "->" + o.getResult() + " x" + o.getMaxUses()).toList().toString();
        var planks = forester.getOffers().stream().filter(offer -> offer.getResult().is(Items.OAK_PLANKS)).findFirst()
                .orElseThrow(() -> helper.assertionException("It should sell its planks, offers " + offers));
        helper.assertTrue(planks.getCostA().is(Items.EMERALD) && planks.getCostA().getCount() == 1 && planks.getResult().getCount() > 1,
                "Planks are sold as many as one emerald buys, got " + offers);
        helper.assertTrue(planks.getMaxUses() == 40 / planks.getResult().getCount(), "...in whole batches of its stock, " + offers);
        var table = com.sheepfromheaven.minenow.villager.TradeTable.forVillager(forester).orElseThrow();
        double log = table.sellPrice(Items.OAK_LOG, 1).orElseThrow().perItem();
        double stripped = table.sellPrice(Items.STRIPPED_OAK_LOG, 1).orElseThrow().perItem();
        helper.assertTrue(stripped > log, "A stripped log costs its log plus the labor and axe wear, " + stripped + " vs " + log);
        helper.assertTrue(forester.getOffers().stream().noneMatch(offer -> offer.getResult().is(Items.STRIPPED_OAK_LOG)),
                "Five stripped logs are less than a batch, so none are offered, " + offers);
        helper.succeed();
    }

    private static void drop(GameTestHelper helper, BlockPos pos, ItemStack stack) {
        var item = new net.minecraft.world.entity.item.ItemEntity(helper.getLevel(), helper.absolutePos(pos).getX() + 0.5, helper.absolutePos(pos).getY(), helper.absolutePos(pos).getZ() + 0.5, stack);
        item.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        helper.getLevel().addFreshEntity(item);
    }

    private static Villager spawnFletcher(GameTestHelper helper, BlockPos table) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(table, Blocks.FLETCHING_TABLE);
        return GameTestHelpers.spawnEmployed(helper, table.east(), table, net.minecraft.world.entity.npc.villager.VillagerProfession.FLETCHER,
                net.minecraft.world.entity.ai.village.poi.PoiTypes.FLETCHER);
    }

    public static void aJourneymanForesterLeavesBigTreesAlone(GameTestHelper helper) {
        Villager forester = spawnForester(helper, NurseryTree.DARK_OAK);
        forester.setVillagerData(forester.getVillagerData().withLevel(3));
        forester.getInventory().addItem(new ItemStack(Items.STONE_AXE));
        BlockPos base = new BlockPos(12, 1, 12);
        bigTree(helper, base);

        helper.runAfterDelay(600, () -> {
            helper.assertTrue(bigTreeLogs(helper, base) == 12, "A 2x2 tree is expert work, " + bigTreeLogs(helper, base) + " of 12 logs left");
            helper.succeed();
        });
    }

    public static void anExpertForesterFellsABigTreeAndReplantsAllFourSaplings(GameTestHelper helper) {
        Villager forester = spawnForester(helper, NurseryTree.DARK_OAK);
        forester.setVillagerData(forester.getVillagerData().withLevel(4));
        forester.getInventory().addItem(new ItemStack(Items.STONE_AXE));
        forester.getInventory().addItem(new ItemStack(Items.DARK_OAK_SAPLING, 4));
        BlockPos base = new BlockPos(12, 1, 12);
        bigTree(helper, base);

        helper.succeedWhen(() -> {
            helper.assertTrue(bigTreeLogs(helper, base) == 0, "An expert fells the 2x2 tree, " + bigTreeLogs(helper, base) + " logs left");
            int saplings = 0;
            for (int dx = 0; dx <= 1; dx++) {
                for (int dz = 0; dz <= 1; dz++) if (helper.getBlockState(base.offset(dx, 0, dz)).is(Blocks.DARK_OAK_SAPLING)) saplings++;
            }
            helper.assertTrue(saplings == 4, "...and puts four saplings back so it grows again, planted " + saplings);
        });
    }

    /** A 2x2 dark oak: three logs high in each of four columns on dirt, with a crown of natural leaves. */
    private static void bigTree(GameTestHelper helper, BlockPos base) {
        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                helper.setBlock(base.offset(dx, -1, dz), Blocks.DIRT);
                for (int y = 0; y < 3; y++) helper.setBlock(base.offset(dx, y, dz), Blocks.DARK_OAK_LOG);
            }
        }
        for (int dx = -1; dx <= 2; dx++) {
            for (int dz = -1; dz <= 2; dz++) {
                BlockPos pos = base.offset(dx, 3, dz);
                helper.setBlock(pos, Blocks.DARK_OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.DISTANCE, 1));
            }
        }
    }

    private static int bigTreeLogs(GameTestHelper helper, BlockPos base) {
        int logs = 0;
        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                for (int y = 0; y < 3; y++) if (helper.getBlockState(base.offset(dx, y, dz)).is(net.minecraft.tags.BlockTags.LOGS)) logs++;
            }
        }
        return logs;
    }

    public static void theToolsmithBuysTheSticksTheForesterGathers(GameTestHelper helper) {
        Villager forester = spawnForester(helper);
        forester.getInventory().addItem(new ItemStack(Items.STICK, 40));
        BlockPos table = new BlockPos(18, 1, 12);
        helper.setBlock(table, Blocks.SMITHING_TABLE);
        Villager toolsmith = GameTestHelpers.spawnEmployed(helper, table.west(), table, net.minecraft.world.entity.npc.villager.VillagerProfession.TOOLSMITH,
                net.minecraft.world.entity.ai.village.poi.PoiTypes.TOOLSMITH);
        toolsmith.getInventory().addItem(new ItemStack(Items.EMERALD, 5));

        helper.succeedWhen(() -> helper.assertTrue(toolsmith.getInventory().countItem(Items.STICK) > 0,
                "The forester sells the sticks it gathers in its grove, so tools don't wait on the fletcher; toolsmith has " + toolsmith.getInventory().countItem(Items.STICK)));
    }

    private static Villager spawnForester(GameTestHelper helper) {
        return spawnForester(helper, NurseryTree.OAK);
    }

    private static Villager spawnForester(GameTestHelper helper, NurseryTree kind) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        BlockPos nursery = new BlockPos(5, 2, 12);
        helper.setBlock(nursery.below(), Blocks.OAK_LOG);
        helper.setBlock(nursery, Forester.nursery(kind));
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
