package com.sheepfromheaven.minenow.miner;

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
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class MinerTests {
    static final BlockPos FURNACE = new BlockPos(6, 4, 12);

    private MinerTests() {}

    public static void unemployedVillagerBecomesAMinerAtAFreeFurnace(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(new BlockPos(10, 1, 12), Miner.FURNACE_CART.get());
        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(13, 1, 12));

        helper.succeedWhen(() -> helper.assertTrue(villager.getVillagerData().profession().is(Miner.PROFESSION),
                "An unemployed villager should take a free furnace minecart and become a miner"));
    }

    public static void aPlainFurnaceIsNoJobSite(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(new BlockPos(10, 1, 12), Blocks.FURNACE);
        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(13, 1, 12));

        helper.runAfterDelay(1200, () -> {
            helper.assertTrue(villager.getVillagerData().profession().is(net.minecraft.world.entity.npc.villager.VillagerProfession.NONE),
                    "Players use furnaces everywhere, so a plain furnace makes no miner");
            helper.succeed();
        });
    }

    public static void aFurnaceMinecartCanBePlacedOffRails(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        ServerPlayer player = playerHolding(helper, Items.FURNACE_MINECART);
        BlockPos ground = new BlockPos(10, 0, 12);

        useOn(helper, player, ground);

        helper.assertTrue(helper.getBlockState(ground.above()).is(Miner.FURNACE_CART.get()), "A furnace minecart used off a rail is placed as a block");
        helper.assertTrue(helper.getBlockState(ground.above()).getBlock() instanceof net.minecraft.world.level.block.AbstractFurnaceBlock, "It works as a furnace");
        helper.succeed();
    }

    public static void aFurnaceMinecartOnARailStaysAMinecart(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        ServerPlayer player = playerHolding(helper, Items.FURNACE_MINECART);
        BlockPos rail = new BlockPos(10, 1, 12);
        helper.setBlock(rail, Blocks.RAIL);

        useOn(helper, player, rail);

        helper.assertFalse(helper.getBlockState(rail).is(Miner.FURNACE_CART.get()), "On a rail it is placed as a minecart, not a block");
        helper.assertFalse(helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.vehicle.minecart.MinecartFurnace.class, helper.getBounds()).isEmpty(),
                "...the vanilla furnace minecart");
        helper.succeed();
    }

    private static ServerPlayer playerHolding(GameTestHelper helper, Item item) {
        @SuppressWarnings("removal")
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
        return player;
    }

    private static void useOn(GameTestHelper helper, ServerPlayer player, BlockPos pos) {
        BlockPos absolute = helper.absolutePos(pos);
        var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(absolute).add(0, 0.5, 0), net.minecraft.core.Direction.UP, absolute, false);
        player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
    }

    public static void minerWithoutAPickaxeOffersToBuyOne(GameTestHelper helper) {
        Villager miner = spawnMiner(helper);
        GameTestHelpers.giveStartingPurse(helper, miner);
        @SuppressWarnings("removal")
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.interactOn(miner, InteractionHand.MAIN_HAND, miner.position());

        Optional<MerchantOffer> buyPick = miner.getOffers().stream().filter(o -> o.getCostA().is(Items.WOODEN_PICKAXE)).findFirst();
        helper.assertTrue(buyPick.isPresent() && buyPick.get().getResult().is(Items.EMERALD),
                "A miner without a pickaxe should offer emeralds for one, offers: " + miner.getOffers().size());
        helper.succeed();
    }

    public static void minerDigsItsQuarryLayerByLayer(GameTestHelper helper) {
        Villager miner = spawnMiner(helper);
        miner.getInventory().addItem(new ItemStack(Items.WOODEN_PICKAXE));
        miner.getInventory().addItem(new ItemStack(Items.LADDER, 16));
        quarry(helper);
        List<String> order = new ArrayList<>();
        boolean[] layerSkipped = {false};
        helper.onEachTick(() -> {
            int upper = solidCells(helper, 2);
            int lower = solidCells(helper, 1);
            if (lower < 9 && upper > 0) layerSkipped[0] = true;
            String state = upper + "/" + lower;
            if (order.isEmpty() || !order.getLast().equals(state)) order.add(state);
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(solidCells(helper, 3) == 0, "The miner digs the ground inside the rim first, " + solidCells(helper, 3) + " left");
            helper.assertTrue(solidCells(helper, 2) == 0 && solidCells(helper, 1) == 0, "The miner should dig both layers of its quarry, solid left "
                    + solidCells(helper, 2) + " above, " + solidCells(helper, 1) + " below");
            helper.assertTrue(rimIntact(helper, 7, 9, 11, 13), "It leaves the rim in place");
            helper.assertFalse(layerSkipped[0], "It finishes a layer before digging the one below: " + order);
            helper.assertTrue(count(miner, Items.COBBLESTONE) >= 27, "The stone goes into its inventory as cobblestone, has " + count(miner, Items.COBBLESTONE));
            helper.assertTrue(pickaxe(miner).getDamageValue() >= 18, "Every block wears the pickaxe");
            helper.assertTrue(untouchedOutsideQuarry(helper), "It only digs inside the quarry you marked");
            for (int y = 1; y <= 3; y++) {
                int ladders = 0;
                for (int x = 7; x <= 9; x++) {
                    for (int z = 11; z <= 13; z++) if (helper.getBlockState(new BlockPos(x, y, z)).is(Blocks.LADDER)) ladders++;
                }
                helper.assertTrue(ladders == 1, "It places one ladder per layer down a wall of the quarry, " + ladders + " at y " + y);
            }
        });
    }

    public static void anOpenPitWithoutARimIsNoQuarry(GameTestHelper helper) {
        Villager miner = spawnMiner(helper);
        miner.getInventory().addItem(new ItemStack(Items.WOODEN_PICKAXE));
        miner.getInventory().addItem(new ItemStack(Items.LADDER, 16));
        for (int x = 7; x <= 9; x++) {
            for (int z = 11; z <= 13; z++) helper.setBlock(x, 3, z, Blocks.AIR);
        }

        helper.runAfterDelay(600, () -> {
            helper.assertTrue(solidCells(helper, 2) == 9, "A hole beside the cart could be part of a build, so the miner leaves it alone, dug " + (9 - solidCells(helper, 2)));
            helper.succeed();
        });
    }

    public static void aRimWithAGapIsNoQuarry(GameTestHelper helper) {
        Villager miner = spawnMiner(helper);
        miner.getInventory().addItem(new ItemStack(Items.WOODEN_PICKAXE));
        miner.getInventory().addItem(new ItemStack(Items.LADDER, 16));
        quarry(helper);
        helper.setBlock(10, 3, 13, Blocks.STONE);

        helper.runAfterDelay(600, () -> {
            helper.assertTrue(solidCells(helper, 3) == 9, "Only a closed rim marks a quarry, dug " + (9 - solidCells(helper, 3)));
            helper.succeed();
        });
    }

    private static boolean rimIntact(GameTestHelper helper, int x0, int x1, int z0, int z1) {
        for (int x = x0 - 1; x <= x1 + 1; x++) {
            for (int z = z0 - 1; z <= z1 + 1; z++) {
                if ((x < x0 || x > x1 || z < z0 || z > z1) && !helper.getBlockState(new BlockPos(x, 3, z)).is(Blocks.COBBLESTONE)) return false;
            }
        }
        return true;
    }

    public static void minerLeavesOreItsPickaxeCannotHarvest(GameTestHelper helper) {
        Villager miner = spawnMiner(helper);
        miner.getInventory().addItem(new ItemStack(Items.WOODEN_PICKAXE));
        miner.getInventory().addItem(new ItemStack(Items.LADDER, 16));
        quarry(helper);
        BlockPos ore = new BlockPos(8, 2, 12);
        helper.setBlock(ore, Blocks.IRON_ORE);

        helper.succeedWhen(() -> {
            helper.assertTrue(solidCells(helper, 1) == 0, "The miner digs past the ore, " + solidCells(helper, 1) + " solid left in the lower layer, "
                    + solidCells(helper, 2) + " in the upper; miner at " + String.format("%.2f %.2f %.2f", miner.getX() - helper.absolutePos(BlockPos.ZERO).getX(), miner.getY() - helper.absolutePos(BlockPos.ZERO).getY(), miner.getZ() - helper.absolutePos(BlockPos.ZERO).getZ())
                    + " ground " + miner.onGround() + " climbing " + miner.onClimbable() + ", running " + miner.getBrain().getRunningBehaviors().stream().map(b -> b.getClass().getSimpleName()).filter(n -> !n.isEmpty()).toList()
                    + ", walk " + miner.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET).map(w -> helper.relativePos(w.getTarget().currentBlockPosition()).toShortString()).orElse("-")
                    + ", ladders " + ladderCells(helper) + ", pickaxe " + pickaxe(miner) + ", activity " + miner.getBrain().getActiveNonCoreActivity().map(Object::toString).orElse("-"));
            helper.assertTrue(helper.getBlockState(ore).is(Blocks.IRON_ORE), "A wooden pickaxe can't harvest iron ore, so it is left in place");
        });
    }

    public static void minerWithoutLaddersDoesNotDigItselfIn(GameTestHelper helper) {
        Villager miner = spawnMiner(helper);
        miner.getInventory().addItem(new ItemStack(Items.WOODEN_PICKAXE));
        quarry(helper);

        helper.runAfterDelay(600, () -> {
            helper.assertTrue(solidCells(helper, 2) == 9, "Without ladders to climb back out the miner doesn't dig down, dug " + (9 - solidCells(helper, 2)));
            helper.succeed();
        });
    }

    public static void buyersWaitForAMinerToComeUpOutOfItsMine(GameTestHelper helper) {
        Villager miner = spawnMiner(helper);
        quarry(helper);
        for (int y = 1; y <= 3; y++) {
            for (int x = 7; x <= 9; x++) {
                for (int z = 11; z <= 13; z++) helper.setBlock(x, y, z, Blocks.AIR);
            }
            helper.setBlock(new BlockPos(7, y, 11), Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING, net.minecraft.core.Direction.EAST));
        }
        miner.getInventory().addItem(new ItemStack(Items.STONE_PICKAXE));
        miner.getInventory().addItem(new ItemStack(Items.COBBLESTONE, 64));
        miner.snapTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(9.5, 1, 13.5)));
        miner.setNoAi(true);
        BlockPos table = new BlockPos(16, 4, 12);
        helper.setBlock(table, Blocks.SMITHING_TABLE);
        Villager toolsmith = GameTestHelpers.spawnEmployed(helper, table.east(), table, net.minecraft.world.entity.npc.villager.VillagerProfession.TOOLSMITH,
                net.minecraft.world.entity.ai.village.poi.PoiTypes.TOOLSMITH);
        toolsmith.getInventory().addItem(new ItemStack(Items.EMERALD, 5));
        int surface = helper.absolutePos(new BlockPos(0, 4, 0)).getY();
        helper.onEachTick(() -> helper.assertTrue(toolsmith.getY() >= surface,
                "A buyer doesn't follow a miner down its mine, toolsmith at " + helper.relativePos(toolsmith.blockPosition()).toShortString()));

        helper.runAfterDelay(600, () -> {
            helper.assertTrue(toolsmith.getInventory().countItem(Items.COBBLESTONE) == 0, "It buys nothing from a miner down its mine");
            helper.succeed();
        });
    }

    public static void minerClimbsOutAfterWorkThoughItIsHeadedElsewhere(GameTestHelper helper) {
        Villager miner = spawnMiner(helper);
        helper.setTime(GameTestHelpers.LATE_AFTERNOON);
        quarry(helper);
        for (int y = 1; y <= 3; y++) {
            for (int x = 7; x <= 9; x++) {
                for (int z = 11; z <= 13; z++) helper.setBlock(x, y, z, Blocks.AIR);
            }
            helper.setBlock(new BlockPos(7, y, 11), Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING, net.minecraft.core.Direction.EAST));
        }
        miner.getInventory().addItem(new ItemStack(Items.STONE_PICKAXE));
        miner.snapTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(9.5, 1, 13.5)));
        GameTestHelpers.giveHome(helper, miner, new BlockPos(18, 4, 6));

        helper.runAfterDelay(300, () -> {
            helper.assertTrue(miner.getY() >= helper.absolutePos(new BlockPos(0, 4, 0)).getY(),
                    "After work the miner climbs its ladder out of the mine, wherever it's headed, at " + helper.relativePos(miner.blockPosition()).toShortString());
            helper.succeed();
        });
    }

    public static void aVillagerDiagonalToTheLadderStillClimbsOut(GameTestHelper helper) {
        spawnMiner(helper);
        quarry(helper);
        for (int y = 2; y <= 3; y++) {
            for (int x = 7; x <= 9; x++) {
                for (int z = 11; z <= 13; z++) helper.setBlock(x, y, z, Blocks.AIR);
            }
        }
        helper.setBlock(new BlockPos(8, 1, 12), Blocks.AIR);
        for (int y = 1; y <= 3; y++) {
            helper.setBlock(new BlockPos(7, y, 11), Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING, net.minecraft.core.Direction.EAST));
        }
        Villager visitor = helper.spawn(net.minecraft.world.entity.EntityTypes.VILLAGER, new BlockPos(8, 1, 12));

        int surface = helper.absolutePos(new BlockPos(0, 4, 0)).getY();
        helper.runAfterDelay(200, () -> helper.assertTrue(visitor.getY() >= surface && visitor.onGround(),
                "A villager in a dug pocket diagonal to the ladder climbs out rather than pushing into the corner, at " + helper.relativePos(visitor.blockPosition()).toShortString()));
        helper.runAfterDelay(260, () -> {
            helper.assertTrue(visitor.getY() >= surface && visitor.onGround(), "...and stays out, at " + helper.relativePos(visitor.blockPosition()).toShortString());
            helper.succeed();
        });
    }

    public static void aWorkerHeadedForItsJobSiteStillClimbsOutOfAMine(GameTestHelper helper) {
        spawnMiner(helper);
        quarry(helper);
        for (int y = 1; y <= 3; y++) {
            for (int x = 7; x <= 9; x++) {
                for (int z = 11; z <= 13; z++) helper.setBlock(x, y, z, Blocks.AIR);
            }
            helper.setBlock(new BlockPos(7, y, 11), Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING, net.minecraft.core.Direction.EAST));
        }
        BlockPos composter = new BlockPos(18, 4, 12);
        helper.setBlock(composter, Blocks.COMPOSTER);
        Villager farmer = GameTestHelpers.spawnEmployed(helper, new BlockPos(9, 1, 13), composter,
                net.minecraft.world.entity.npc.villager.VillagerProfession.FARMER, net.minecraft.world.entity.ai.village.poi.PoiTypes.FARMER);
        int surface = helper.absolutePos(new BlockPos(0, 4, 0)).getY();

        helper.runAfterDelay(300, () -> helper.assertTrue(farmer.getY() >= surface && farmer.onGround(),
                "A farmer at work climbs out of a mine though vanilla keeps steering it towards its composter, at " + helper.relativePos(farmer.blockPosition()).toShortString()));
        helper.runAfterDelay(360, () -> {
            helper.assertTrue(farmer.getY() >= surface && farmer.onGround(), "...and stays out, at " + helper.relativePos(farmer.blockPosition()).toShortString());
            helper.succeed();
        });
    }

    public static void aMinerInAnotherMinersMineClimbsOut(GameTestHelper helper) {
        spawnMiner(helper);
        quarry(helper);
        for (int y = 1; y <= 3; y++) {
            for (int x = 7; x <= 9; x++) {
                for (int z = 11; z <= 13; z++) helper.setBlock(x, y, z, Blocks.AIR);
            }
            helper.setBlock(new BlockPos(7, y, 11), Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING, net.minecraft.core.Direction.EAST));
        }
        BlockPos otherCart = new BlockPos(18, 4, 18);
        helper.setBlock(otherCart, Miner.FURNACE_CART.get());
        Villager visitor = GameTestHelpers.spawnEmployed(helper, new BlockPos(9, 1, 13), otherCart, Miner.PROFESSION, Miner.POI);
        visitor.getInventory().addItem(new ItemStack(Items.STONE_PICKAXE));
        int surface = helper.absolutePos(new BlockPos(0, 4, 0)).getY();

        helper.runAfterDelay(300, () -> helper.assertTrue(visitor.getY() >= surface && visitor.onGround(),
                "A miner down another miner's mine climbs out by that mine's ladder, at " + helper.relativePos(visitor.blockPosition()).toShortString()));
        helper.runAfterDelay(360, () -> {
            helper.assertTrue(visitor.getY() >= surface && visitor.onGround(), "...and stays out, at " + helper.relativePos(visitor.blockPosition()).toShortString());
            helper.succeed();
        });
    }

    public static void villagersDoNotWalkIntoAMine(GameTestHelper helper) {
        spawnMiner(helper);
        quarry(helper);
        for (int y = 1; y <= 3; y++) {
            for (int x = 7; x <= 9; x++) {
                for (int z = 11; z <= 13; z++) helper.setBlock(x, y, z, Blocks.AIR);
            }
        }
        Villager walker = helper.spawn(net.minecraft.world.entity.EntityTypes.VILLAGER, new BlockPos(13, 4, 12));
        BlockPos bottom = helper.absolutePos(new BlockPos(8, 1, 12));
        int surface = helper.absolutePos(new BlockPos(0, 4, 0)).getY();
        helper.onEachTick(() -> {
            walker.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET,
                    new net.minecraft.world.entity.ai.memory.WalkTarget(bottom, 0.5F, 0));
            helper.assertTrue(walker.getY() >= surface, "A villager never walks into a mine it can't climb out of, at " + helper.relativePos(walker.blockPosition()).toShortString());
        });

        helper.runAfterDelay(300, helper::succeed);
    }

    public static void aVillagerThatFallsIntoAMineClimbsOutByTheLadder(GameTestHelper helper) {
        spawnMiner(helper);
        quarry(helper);
        for (int y = 1; y <= 3; y++) {
            for (int x = 7; x <= 9; x++) {
                for (int z = 11; z <= 13; z++) helper.setBlock(x, y, z, Blocks.AIR);
            }
            helper.setBlock(new BlockPos(7, y, 11), Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING, net.minecraft.core.Direction.EAST));
        }
        Villager visitor = helper.spawn(net.minecraft.world.entity.EntityTypes.VILLAGER, new BlockPos(9, 1, 13));

        int surface = helper.absolutePos(new BlockPos(0, 4, 0)).getY();
        helper.runAfterDelay(200, () -> helper.assertTrue(visitor.getY() >= surface && visitor.onGround(),
                "A villager down someone's mine climbs out by its ladder instead of staying trapped, at " + helper.relativePos(visitor.blockPosition()).toShortString()));
        helper.runAfterDelay(260, () -> {
            helper.assertTrue(visitor.getY() >= surface && visitor.onGround(), "...and stays out, at " + helper.relativePos(visitor.blockPosition()).toShortString());
            helper.succeed();
        });
    }

    public static void minerClimbsOutOfItsQuarryToUnload(GameTestHelper helper) {
        Villager miner = spawnMiner(helper);
        miner.getInventory().addItem(new ItemStack(Items.WOODEN_PICKAXE));
        quarry(helper);
        for (int y = 1; y <= 2; y++) {
            for (int x = 7; x <= 9; x++) {
                for (int z = 11; z <= 13; z++) helper.setBlock(x, y, z, Blocks.AIR);
            }
        }
        for (int y = 1; y <= 3; y++) {
            helper.setBlock(new BlockPos(7, y, 11), Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING, net.minecraft.core.Direction.EAST));
        }
        miner.snapTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(8.5, 1, 12.5)));
        miner.getInventory().addItem(new ItemStack(Items.COBBLESTONE, 64));
        miner.getInventory().addItem(new ItemStack(Items.COBBLESTONE, 32));
        BlockPos chest = new BlockPos(5, 4, 15);
        helper.setBlock(chest, Blocks.CHEST);
        GameTestHelpers.giveHome(helper, miner, new BlockPos(5, 4, 9));

        helper.succeedWhen(() -> {
            var container = helper.getBlockEntity(chest, net.minecraft.world.level.block.entity.ChestBlockEntity.class);
            int cobble = 0;
            for (int i = 0; i < container.getContainerSize(); i++) if (container.getItem(i).is(Items.COBBLESTONE)) cobble += container.getItem(i).getCount();
            helper.assertTrue(cobble >= 64, "The miner should climb its ladder out of the quarry and unload at its home chest, chest has " + cobble
                    + "; miner at " + helper.relativePos(miner.blockPosition()).toShortString());
        });
    }

    public static void minerSellsADiamondForSeveralEmeralds(GameTestHelper helper) {
        Villager miner = spawnMiner(helper);
        miner.setVillagerData(miner.getVillagerData().withLevel(4));
        miner.getInventory().addItem(new ItemStack(Items.WOODEN_PICKAXE));
        miner.getInventory().addItem(new ItemStack(Items.DIAMOND, 2));
        @SuppressWarnings("removal")
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.interactOn(miner, InteractionHand.MAIN_HAND, miner.position());

        Optional<MerchantOffer> diamond = miner.getOffers().stream().filter(o -> o.getResult().is(Items.DIAMOND)).findFirst();
        helper.assertTrue(diamond.isPresent(), "A miner with diamonds offers them");
        helper.assertTrue(diamond.get().getResult().getCount() == 1 && diamond.get().getCostA().is(Items.EMERALD) && diamond.get().getCostA().getCount() == 3,
                "A diamond costs 3 emeralds, offered " + diamond.get().getResult().getCount() + " for " + diamond.get().getCostA().getCount());
        helper.succeed();
    }

    public static void toolsmithBuysDiamondsFromTheMinerAtItsPrice(GameTestHelper helper) {
        Villager miner = spawnMiner(helper);
        miner.setVillagerData(miner.getVillagerData().withLevel(4));
        miner.getInventory().addItem(new ItemStack(Items.WOODEN_PICKAXE));
        miner.getInventory().addItem(new ItemStack(Items.DIAMOND, 3));
        BlockPos table = new BlockPos(16, 4, 12);
        helper.setBlock(table, Blocks.SMITHING_TABLE);
        Villager toolsmith = GameTestHelpers.spawnEmployed(helper, table.west(), table,
                net.minecraft.world.entity.npc.villager.VillagerProfession.TOOLSMITH, net.minecraft.world.entity.ai.village.poi.PoiTypes.TOOLSMITH);
        toolsmith.setVillagerData(toolsmith.getVillagerData().withLevel(4));
        toolsmith.getInventory().addItem(new ItemStack(Items.EMERALD, 11));

        helper.succeedWhen(() -> {
            helper.assertTrue(count(toolsmith, Items.DIAMOND) == 3, "The toolsmith should buy the miner's diamonds, has " + count(toolsmith, Items.DIAMOND));
            helper.assertTrue(count(miner, Items.EMERALD) == 9, "It pays 3 emeralds each, the miner got " + count(miner, Items.EMERALD));
            helper.assertTrue(count(toolsmith, Items.EMERALD) == 2, "...keeping its food money, has " + count(toolsmith, Items.EMERALD));
        });
    }

    public static void minerLightsItsQuarryWithTorches(GameTestHelper helper) {
        Villager miner = spawnMiner(helper);
        miner.getInventory().addItem(new ItemStack(Items.WOODEN_PICKAXE));
        miner.getInventory().addItem(new ItemStack(Items.LADDER, 16));
        miner.getInventory().addItem(new ItemStack(Items.COAL, 1));
        miner.getInventory().addItem(new ItemStack(Items.STICK, 1));
        quarry(helper);

        helper.succeedWhen(() -> {
            helper.assertTrue(solidCells(helper, 1) == 0, "The miner digs its quarry first, " + solidCells(helper, 1) + " left");
            int torches = 0;
            for (int y = 1; y <= 3; y++) {
                for (int x = 7; x <= 9; x++) {
                    for (int z = 11; z <= 13; z++) if (helper.getBlockState(new BlockPos(x, y, z)).is(Blocks.WALL_TORCH)) torches++;
                }
            }
            helper.assertTrue(torches == 1, "One torch lights this small quarry, made from its coal and a stick; placed " + torches);
            for (int y = 1; y <= 3; y++) {
                for (int x = 7; x <= 9; x++) {
                    for (int z = 11; z <= 13; z++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        if (!helper.getBlockState(pos).is(Blocks.WALL_TORCH)) continue;
                        helper.assertTrue(helper.getBlockState(pos.below()).isAir(),
                                "Torches hang above the floor (one block above it when placed), not at floor level; one at " + pos.toShortString());
                    }
                }
            }
            for (int x = 7; x <= 9; x++) {
                for (int z = 11; z <= 13; z++) {
                    int light = helper.getLevel().getBrightness(net.minecraft.world.level.LightLayer.BLOCK, helper.absolutePos(new BlockPos(x, 1, z)));
                    helper.assertTrue(light >= 8, "No dark spot on the quarry floor where monsters could spawn, light " + light + " at " + x + ", 1, " + z);
                }
            }
        });
    }

    public static void minerLeavesOutTorchesWhereYourLightIsEnough(GameTestHelper helper) {
        Villager miner = spawnMiner(helper);
        miner.getInventory().addItem(new ItemStack(Items.WOODEN_PICKAXE));
        miner.getInventory().addItem(new ItemStack(Items.LADDER, 16));
        miner.getInventory().addItem(new ItemStack(Items.TORCH, 16));
        quarry(helper);
        helper.setBlock(new BlockPos(10, 2, 12), Blocks.GLOWSTONE);

        helper.succeedWhen(() -> {
            helper.assertTrue(solidCells(helper, 1) == 0, "The miner digs its quarry first, " + solidCells(helper, 1) + " left");
            helper.assertTrue(count(miner, Items.TORCH) == 16, "Glowstone in the wall already lights the floor, so no torches are needed; it used " + (16 - count(miner, Items.TORCH)));
        });
    }

    public static void minerSealsLavaItUncovers(GameTestHelper helper) {
        Villager miner = spawnMiner(helper);
        miner.getInventory().addItem(new ItemStack(Items.WOODEN_PICKAXE));
        miner.getInventory().addItem(new ItemStack(Items.LADDER, 16));
        miner.getInventory().addItem(new ItemStack(Items.TORCH, 16));
        quarry(helper);
        BlockPos lava = new BlockPos(10, 1, 12);
        helper.setBlock(lava, Blocks.LAVA);

        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getBlockState(lava).is(Blocks.COBBLESTONE), "The miner covers the lava behind its wall with cobblestone, it's " + helper.getBlockState(lava).getBlock().getName().getString());
            helper.assertTrue(solidCells(helper, 1) == 0, "...and then digs the cell beside it too, " + solidCells(helper, 1) + " left");
        });
    }

    public static void minerWallsOffACave(GameTestHelper helper) {
        Villager miner = spawnMiner(helper);
        miner.getInventory().addItem(new ItemStack(Items.WOODEN_PICKAXE));
        miner.getInventory().addItem(new ItemStack(Items.LADDER, 16));
        miner.getInventory().addItem(new ItemStack(Items.TORCH, 16));
        quarry(helper);
        BlockPos cave = new BlockPos(10, 2, 12);
        helper.setBlock(cave, Blocks.AIR);
        helper.setBlock(cave.east(), Blocks.AIR);

        helper.succeedWhen(() -> {
            helper.assertTrue(solidCells(helper, 1) == 0, "The miner digs its quarry, " + solidCells(helper, 1) + " left");
            helper.assertTrue(helper.getBlockState(cave).is(Blocks.COBBLESTONE), "It fills the cave opening in its wall with cobblestone, it's " + helper.getBlockState(cave).getBlock().getName().getString());
        });
    }

    public static void minerDigsNewEmeraldsOutOfEmeraldOre(GameTestHelper helper) {
        Villager miner = spawnMiner(helper);
        miner.getInventory().addItem(new ItemStack(Items.IRON_PICKAXE));
        miner.getInventory().addItem(new ItemStack(Items.LADDER, 16));
        quarry(helper);
        helper.setBlock(new BlockPos(8, 2, 12), Blocks.EMERALD_ORE);

        helper.succeedWhen(() -> helper.assertTrue(count(miner, Items.EMERALD) >= 1,
                "Emerald ore it digs is new money for the village, has " + count(miner, Items.EMERALD) + " emeralds; ore " + helper.getBlockState(new BlockPos(8, 2, 12)).getBlock().getName().getString()));
    }

    public static void minerKnapsGravelIntoFlintAtItsCart(GameTestHelper helper) {
        Villager miner = spawnMiner(helper);
        miner.getInventory().addItem(new ItemStack(Items.STONE_PICKAXE));
        miner.getInventory().addItem(new ItemStack(Items.GRAVEL, 4));

        helper.succeedWhen(() -> {
            helper.assertTrue(count(miner, Items.FLINT) >= 1, "The miner knaps flint out of its gravel at its cart, has " + count(miner, Items.FLINT) + " flint, " + count(miner, Items.GRAVEL) + " gravel");
            helper.assertTrue(pickaxe(miner).getDamageValue() >= 10 * count(miner, Items.FLINT), "A novice needs about ten tries a flint, each wearing its pickaxe, damage " + pickaxe(miner).getDamageValue());
        });
    }

    public static void betterMinersKnapFlintWithFewerTries(GameTestHelper helper) {
        var table = com.sheepfromheaven.minenow.villager.TradeTable.forProfession(Miner.PROFESSION).orElseThrow();
        double novice = table.sellPrice(Items.FLINT, 1).orElseThrow().perItem();
        double master = table.sellPrice(Items.FLINT, 5).orElseThrow().perItem();
        helper.assertTrue(master < novice, "Fewer tries mean less pickaxe wear, so a master's flint is cheaper, " + master + " vs " + novice);
        helper.succeed();
    }

    /** A 7x7 shaft marked beside the cart at y 3, already dug down to y -1; the first platform is y -2. */
    public static void minerLeavesPlatformsAndBranchesTunnelsOffThem(GameTestHelper helper) {
        Villager miner = spawnMiner(helper);
        miner.getInventory().addItem(new ItemStack(Items.NETHERITE_PICKAXE));
        miner.getInventory().addItem(new ItemStack(Items.LADDER, 32));
        miner.getInventory().addItem(new ItemStack(Items.TORCH, 32));
        for (int x = 0; x <= 23; x++) {
            for (int z = 0; z <= 23; z++) {
                for (int y = -6; y <= 0; y++) helper.setBlock(x, y, z, Blocks.STONE);
            }
        }
        for (int x = 7; x <= 13; x++) {
            for (int z = 9; z <= 15; z++) {
                for (int y = -1; y <= 3; y++) helper.setBlock(x, y, z, Blocks.AIR);
            }
        }
        rim(helper, 7, 13, 9, 15);
        for (int y = -1; y <= 1; y++) {
            for (int across = -1; across <= 1; across++) {
                helper.setBlock(17, y, 12 + across, Blocks.BARRIER);
                helper.setBlock(3, y, 12 + across, Blocks.BARRIER);
                helper.setBlock(10 + across, y, 5, Blocks.BARRIER);
                helper.setBlock(10 + across, y, 19, Blocks.BARRIER);
            }
        }
        boolean[] dugBelowBeforeTunnels = {false};
        List<String> trail = new ArrayList<>();
        helper.onEachTick(() -> {
            if (helper.getTick() % 400 == 0) {
                trail.add(helper.getTick() + " t" + openTunnelCells(helper) + " c" + openCells(helper, 9, 11, 11, 13, -2, -2) + " b" + openCells(helper, 7, 13, 9, 15, -4, -3) + " " + helper.relativePos(miner.blockPosition()).toShortString() + "/"
                        + miner.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.LOOK_TARGET).map(t -> helper.relativePos(t.currentBlockPosition()).toShortString()).orElse("-")
                        + (miner.getBrain().getRunningBehaviors().stream().anyMatch(b -> b instanceof DigQuarry) ? "D" : "")
                        + " w" + miner.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET).map(w -> helper.relativePos(w.getTarget().currentBlockPosition()).toShortString()).orElse("-")
                        + " p" + miner.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.PATH).map(path -> path.getNodeCount() + "/" + path.canReach() + "/" + helper.relativePos(path.getEndNode().asBlockPos()).toShortString()).orElse("-"));
            }
        });
        boolean[] twoTunnelsAtOnce = {false};
        boolean[] centerBeforeTunnels = {false};
        helper.onEachTick(() -> {
            if (openCells(helper, 7, 13, 9, 15, -3, -3) > 1 && openTunnelCells(helper) < 4 * 27) dugBelowBeforeTunnels[0] = true;
            if (openCells(helper, 9, 11, 11, 13, -2, -2) > 0 && openTunnelCells(helper) < 4 * 27) centerBeforeTunnels[0] = true;
            int partial = 0;
            for (int[] box : new int[][] {{14, 16, 11, 13}, {4, 6, 11, 13}, {9, 11, 6, 8}, {9, 11, 16, 18}}) {
                int dug = openCells(helper, box[0], box[1], box[2], box[3], -1, 1);
                if (dug > 0 && dug < 27) partial++;
            }
            if (partial > 1) twoTunnelsAtOnce[0] = true;
        });

        helper.succeedWhen(() -> {
            int ring = 0;
            int ringKept = 0;
            for (int x = 7; x <= 13; x++) {
                for (int z = 9; z <= 15; z++) {
                    if (x >= 9 && x <= 11 && z >= 11 && z <= 13) continue;
                    ring++;
                    if (!open(helper, new BlockPos(x, -2, z))) ringKept++;
                }
            }
            List<String> ladders = new ArrayList<>();
            for (int y = -5; y <= 3; y++) {
                for (int x = 7; x <= 13; x++) {
                    for (int z = 9; z <= 15; z++) if (helper.getBlockState(new BlockPos(x, y, z)).is(Blocks.LADDER)) ladders.add(x + "," + y + "," + z);
                }
            }
            helper.assertTrue(openCells(helper, 9, 11, 11, 13, -2, -2) == 9, "The platform's middle 3x3 is opened, " + openCells(helper, 9, 11, 11, 13, -2, -2) + " of 9; miner at "
                    + helper.relativePos(miner.blockPosition()).toShortString() + ", ladders " + ladders + ", running " + miner.getBrain().getRunningBehaviors().stream().map(b -> b.getClass().getSimpleName()).filter(n -> !n.isEmpty()).toList()
                    + ", walk " + miner.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET).map(w -> helper.relativePos(w.getTarget().currentBlockPosition()).toShortString()).orElse("-")
                    + ", ladders held " + count(miner, Items.LADDER) + ", activity " + miner.getBrain().getActiveNonCoreActivity().map(Object::toString).orElse("-") + ", trail " + trail.subList(0, Math.min(14, trail.size())));
            List<String> missing = new ArrayList<>();
            for (int[] box : new int[][] {{14, 16, 11, 13}, {4, 6, 11, 13}, {9, 11, 6, 8}, {9, 11, 16, 18}}) {
                for (int x = box[0]; x <= box[1]; x++) {
                    for (int z = box[2]; z <= box[3]; z++) {
                        for (int y = -1; y <= 1; y++) if (!open(helper, new BlockPos(x, y, z))) missing.add(x + "," + y + "," + z + "=" + helper.getBlockState(new BlockPos(x, y, z)).getBlock().getName().getString());
                    }
                }
            }
            helper.assertTrue(openTunnelCells(helper) == 4 * 27, "A 3x3 tunnel runs out from the middle of each wall, " + openTunnelCells(helper) + " of 108 cells dug, missing " + missing
                    + "; below " + openCells(helper, 7, 13, 9, 15, -4, -3) + "/98, miner at " + helper.relativePos(miner.blockPosition()).toShortString()
                    + " looking at " + miner.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.LOOK_TARGET).map(t -> helper.relativePos(t.currentBlockPosition()).toShortString()).orElse("-")
                    + ", walk " + miner.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET).map(w -> helper.relativePos(w.getTarget().currentBlockPosition()).toShortString()).orElse("-")
                    + ", running " + miner.getBrain().getRunningBehaviors().stream().map(b -> b.getClass().getSimpleName()).filter(n -> !n.isEmpty()).toList()
                    + "; trail " + trail.subList(0, Math.min(8, trail.size())));
            helper.assertTrue(openCells(helper, 7, 13, 9, 15, -4, -3) == 98, "The 7x7 shaft continues below the platform, " + openCells(helper, 7, 13, 9, 15, -4, -3) + " of 98"
                    + " (" + openCells(helper, 7, 13, 9, 15, -3, -3) + " upper, " + openCells(helper, 7, 13, 9, 15, -4, -4) + " lower); miner at " + helper.relativePos(miner.blockPosition()).toShortString()
                    + " ground " + miner.onGround() + " climbing " + miner.onClimbable()
                    + " looking at " + miner.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.LOOK_TARGET).map(t -> helper.relativePos(t.currentBlockPosition()).toShortString()).orElse("-")
                    + ", walk " + miner.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET).map(w -> helper.relativePos(w.getTarget().currentBlockPosition()).toShortString()).orElse("-")
                    + ", running " + miner.getBrain().getRunningBehaviors().stream().map(b -> b.getClass().getSimpleName()).filter(n -> !n.isEmpty()).toList()
                    + ", origin " + helper.absolutePos(BlockPos.ZERO).toShortString() + ", trail " + trail.subList(0, Math.min(10, trail.size())));
            helper.assertTrue(ringKept == ring - 1, "The platform keeps its 2-block ring except the ladder hole, " + ringKept + " of " + ring + " kept");
            helper.assertFalse(dugBelowBeforeTunnels[0], "It digs a platform's tunnels before going deeper");
            helper.assertFalse(twoTunnelsAtOnce[0], "It finishes one tunnel before starting the next");
            helper.assertFalse(centerBeforeTunnels[0], "It opens the platform's middle only after all four tunnels");
        });
    }

    /** The same 7x7 shaft, with a redstone signal of strength 1 into the cart: one platform deep and no further. */
    public static void aRedstoneSignalStopsTheMinerAtThatManyPlatforms(GameTestHelper helper) {
        Villager miner = spawnMiner(helper);
        miner.setVillagerData(miner.getVillagerData().withLevel(5));
        miner.getInventory().addItem(new ItemStack(Items.NETHERITE_PICKAXE));
        miner.getInventory().addItem(new ItemStack(Items.LADDER, 32));
        miner.getInventory().addItem(new ItemStack(Items.TORCH, 32));
        for (int x = 0; x <= 23; x++) {
            for (int z = 0; z <= 23; z++) {
                for (int y = -6; y <= 0; y++) helper.setBlock(x, y, z, Blocks.STONE);
            }
        }
        for (int x = 7; x <= 13; x++) {
            for (int z = 9; z <= 15; z++) {
                for (int y = -1; y <= 3; y++) helper.setBlock(x, y, z, Blocks.AIR);
            }
        }
        rim(helper, 7, 13, 9, 15);
        for (int y = -1; y <= 1; y++) {
            for (int across = -1; across <= 1; across++) {
                helper.setBlock(17, y, 12 + across, Blocks.BARRIER);
                helper.setBlock(3, y, 12 + across, Blocks.BARRIER);
                helper.setBlock(10 + across, y, 5, Blocks.BARRIER);
                helper.setBlock(10 + across, y, 19, Blocks.BARRIER);
            }
        }
        for (int x = 1; x <= 5; x++) helper.setBlock(x, 4, 22, Blocks.REDSTONE_WIRE);
        for (int z = 13; z <= 22; z++) helper.setBlock(6, 4, z, Blocks.REDSTONE_WIRE);
        helper.setBlock(1, 4, 21, Blocks.REDSTONE_BLOCK);

        helper.runAfterDelay(5000, () -> {
            int signal = helper.getLevel().getBestNeighborSignal(helper.absolutePos(FURNACE));
            helper.assertTrue(signal == 1, "The test powers the cart with strength 1, got " + signal);
            helper.assertTrue(openTunnelCells(helper) == 4 * 27, "It still digs the tunnels of the one platform it may reach, " + openTunnelCells(helper) + " of 108");
            helper.assertTrue(openCells(helper, 9, 11, 11, 13, -2, -2) == 0, "...but doesn't open that platform's middle, " + openCells(helper, 9, 11, 11, 13, -2, -2) + " dug");
            helper.assertTrue(openCells(helper, 7, 13, 9, 15, -4, -3) <= 1, "...and digs nothing below it, " + openCells(helper, 7, 13, 9, 15, -4, -3) + " dug");
            helper.succeed();
        });
    }

    /** A novice's shaft already dug to its 8 layers below the cart, with the strongest redstone signal into the cart: it still goes no deeper. */
    public static void aRedstoneSignalCannotTakeTheMinerPastItsLevelsDepth(GameTestHelper helper) {
        Villager miner = spawnMiner(helper);
        miner.getInventory().addItem(new ItemStack(Items.IRON_PICKAXE));
        miner.getInventory().addItem(new ItemStack(Items.LADDER, 16));
        miner.getInventory().addItem(new ItemStack(Items.TORCH, 16));
        for (int x = 4; x <= 12; x++) {
            for (int z = 8; z <= 16; z++) {
                for (int y = -6; y <= 0; y++) helper.setBlock(x, y, z, Blocks.STONE);
            }
        }
        for (int x = 7; x <= 9; x++) {
            for (int z = 11; z <= 13; z++) {
                for (int y = -4; y <= 3; y++) helper.setBlock(x, y, z, Blocks.AIR);
            }
        }
        rim(helper, 7, 9, 11, 13);
        helper.setBlock(FURNACE.north(), Blocks.REDSTONE_BLOCK);

        helper.runAfterDelay(1200, () -> {
            int signal = helper.getLevel().getBestNeighborSignal(helper.absolutePos(FURNACE));
            helper.assertTrue(signal == 15, "The test powers the cart with strength 15, got " + signal);
            helper.assertTrue(ladderCellsDown(helper, -4) >= 8, "The miner ladders its shaft down to its depth, " + ladderCellsDown(helper, -4) + " of 8");
            helper.assertTrue(openCells(helper, 7, 9, 11, 13, -5, -5) == 0, "A novice digs no deeper than 8 below its cart, whatever the signal, " + openCells(helper, 7, 9, 11, 13, -5, -5) + " dug");
            helper.succeed();
        });
    }

    private static int ladderCellsDown(GameTestHelper helper, int bottom) {
        int ladders = 0;
        for (int y = bottom; y <= 3; y++) {
            for (int x = 7; x <= 9; x++) {
                for (int z = 11; z <= 13; z++) if (helper.getBlockState(new BlockPos(x, y, z)).is(Blocks.LADDER)) ladders++;
            }
        }
        return ladders;
    }

    private static boolean open(GameTestHelper helper, BlockPos pos) {
        var state = helper.getBlockState(pos);
        return state.isAir() || state.is(Blocks.LADDER) || state.is(Blocks.WALL_TORCH);
    }

    private static int openCells(GameTestHelper helper, int x0, int x1, int z0, int z1, int y0, int y1) {
        int open = 0;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                for (int y = y0; y <= y1; y++) if (open(helper, new BlockPos(x, y, z))) open++;
            }
        }
        return open;
    }

    private static int openTunnelCells(GameTestHelper helper) {
        return openCells(helper, 14, 16, 11, 13, -1, 1) + openCells(helper, 4, 6, 11, 13, -1, 1)
                + openCells(helper, 9, 11, 6, 8, -1, 1) + openCells(helper, 9, 11, 16, 18, -1, 1);
    }

    /** A 3x3 quarry rimmed with cobblestone in the ground, the furnace minecart standing on the rim, two layers above a barrier floor. */
    static void quarry(GameTestHelper helper) {
        for (int x = 7; x <= 9; x++) {
            for (int z = 11; z <= 13; z++) helper.setBlock(x, 0, z, Blocks.BARRIER);
        }
        rim(helper, 7, 9, 11, 13);
        helper.setBlock(FURNACE, Miner.FURNACE_CART.get());
    }

    /** Cobblestone around these cells, flush in the ground the cart stands on. */
    static void rim(GameTestHelper helper, int x0, int x1, int z0, int z1) {
        for (int x = x0 - 1; x <= x1 + 1; x++) {
            for (int z = z0 - 1; z <= z1 + 1; z++) {
                if (x < x0 || x > x1 || z < z0 || z > z1) helper.setBlock(x, 3, z, Blocks.COBBLESTONE);
            }
        }
    }

    static int solidCells(GameTestHelper helper, int y) {
        int solid = 0;
        for (int x = 7; x <= 9; x++) {
            for (int z = 11; z <= 13; z++) {
                var state = helper.getBlockState(new BlockPos(x, y, z));
                if (!state.isAir() && !state.is(Blocks.LADDER) && !state.is(Blocks.WALL_TORCH)) solid++;
            }
        }
        return solid;
    }

    private static boolean untouchedOutsideQuarry(GameTestHelper helper) {
        for (int y = 1; y <= 2; y++) {
            for (int i = 6; i <= 10; i++) {
                for (BlockPos pos : List.of(new BlockPos(i, y, 10), new BlockPos(i, y, 14), new BlockPos(6, y, i + 4), new BlockPos(10, y, i + 4))) {
                    if (helper.getBlockState(pos).isAir()) return false;
                }
            }
        }
        return true;
    }

    private static List<String> ladderCells(GameTestHelper helper) {
        List<String> cells = new ArrayList<>();
        for (int y = 1; y <= 3; y++) {
            for (int x = 7; x <= 9; x++) {
                for (int z = 11; z <= 13; z++) if (helper.getBlockState(new BlockPos(x, y, z)).is(Blocks.LADDER)) cells.add(x + "," + y + "," + z);
            }
        }
        return cells;
    }

    static int count(Villager villager, Item item) {
        return villager.getInventory().countItem(item);
    }

    static ItemStack pickaxe(Villager miner) {
        return miner.getInventory().getItems().stream().filter(stack -> stack.is(net.minecraft.tags.ItemTags.PICKAXES)).findFirst().orElse(ItemStack.EMPTY);
    }

    static Villager spawnMiner(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        for (int x = 1; x <= 22; x++) {
            for (int z = 1; z <= 22; z++) {
                for (int y = 1; y <= 3; y++) helper.setBlock(x, y, z, Blocks.STONE);
            }
        }
        helper.setBlock(FURNACE, Miner.FURNACE_CART.get());
        return GameTestHelpers.spawnEmployed(helper, FURNACE.west(), FURNACE, Miner.PROFESSION, Miner.POI);
    }
}
