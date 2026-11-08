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
            helper.assertTrue(solidCells(helper, 2) == 0 && solidCells(helper, 1) == 0, "The miner should dig both layers of its quarry, solid left "
                    + solidCells(helper, 2) + " above, " + solidCells(helper, 1) + " below");
            helper.assertFalse(layerSkipped[0], "It finishes a layer before digging the one below: " + order);
            helper.assertTrue(count(miner, Items.COBBLESTONE) >= 18, "The stone goes into its inventory as cobblestone, has " + count(miner, Items.COBBLESTONE));
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

    /** A stone platform (top at y 4) with a 3x3 pit dug one block deep beside the furnace: the quarry, two layers above a barrier floor. */
    static void quarry(GameTestHelper helper) {
        for (int x = 4; x <= 12; x++) {
            for (int z = 8; z <= 16; z++) {
                for (int y = 1; y <= 3; y++) helper.setBlock(x, y, z, Blocks.STONE);
            }
        }
        for (int x = 7; x <= 9; x++) {
            for (int z = 11; z <= 13; z++) {
                helper.setBlock(x, 3, z, Blocks.AIR);
                helper.setBlock(x, 0, z, Blocks.BARRIER);
            }
        }
        helper.setBlock(FURNACE, Miner.FURNACE_CART.get());
    }

    static int solidCells(GameTestHelper helper, int y) {
        int solid = 0;
        for (int x = 7; x <= 9; x++) {
            for (int z = 11; z <= 13; z++) {
                var state = helper.getBlockState(new BlockPos(x, y, z));
                if (!state.isAir() && !state.is(Blocks.LADDER)) solid++;
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
        helper.setBlock(FURNACE, Miner.FURNACE_CART.get());
        for (int x = 4; x <= 6; x++) {
            for (int y = 1; y <= 3; y++) helper.setBlock(x, y, 12, Blocks.STONE);
        }
        return GameTestHelpers.spawnEmployed(helper, FURNACE.west(), FURNACE, Miner.PROFESSION, Miner.POI);
    }
}
