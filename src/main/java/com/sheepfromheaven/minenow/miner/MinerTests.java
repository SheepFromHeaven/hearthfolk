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

    public static void minerSmeltsItsOreInItsFurnaceMinecart(GameTestHelper helper) {
        Villager miner = spawnMiner(helper);
        miner.getInventory().addItem(new ItemStack(Items.WOODEN_PICKAXE));
        miner.getInventory().addItem(new ItemStack(Items.RAW_IRON, 3));
        miner.getInventory().addItem(new ItemStack(Items.COAL, 2));
        miner.getInventory().addItem(new ItemStack(Items.COBBLESTONE, 10));

        helper.succeedWhen(() -> {
            var furnace = helper.getLevel().getBlockEntity(helper.absolutePos(FURNACE)) instanceof net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity f ? f : null;
            helper.assertTrue(count(miner, Items.IRON_INGOT) >= 3, "The miner should smelt its raw iron in its furnace minecart, has " + count(miner, Items.IRON_INGOT) + " ingots, "
                    + count(miner, Items.RAW_IRON) + " raw, " + count(miner, Items.COAL) + " coal; furnace " + (furnace == null ? "missing" : furnace.getItem(0) + " / " + furnace.getItem(1) + " / " + furnace.getItem(2))
                    + "; miner at " + helper.relativePos(miner.blockPosition()).toShortString() + ", running " + miner.getBrain().getRunningBehaviors().stream().map(b -> b.getClass().getSimpleName()).filter(n -> !n.isEmpty()).toList()
                    + ", activity " + miner.getBrain().getActiveNonCoreActivity().map(Object::toString).orElse("-"));
            helper.assertTrue(count(miner, Items.COBBLESTONE) == 10, "It keeps its cobblestone as it is rather than smelting it into stone");
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
        toolsmith.getInventory().addItem(new ItemStack(Items.EMERALD, 9));

        helper.succeedWhen(() -> {
            helper.assertTrue(count(toolsmith, Items.DIAMOND) == 3, "The toolsmith should buy the miner's diamonds, has " + count(toolsmith, Items.DIAMOND));
            helper.assertTrue(count(miner, Items.EMERALD) == 9, "It pays 3 emeralds each, the miner got " + count(miner, Items.EMERALD));
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
            helper.assertTrue(torches >= 1, "It puts torches on the quarry walls, made from its coal and a stick");
            for (int x = 7; x <= 9; x++) {
                for (int z = 11; z <= 13; z++) {
                    int light = helper.getLevel().getBrightness(net.minecraft.world.level.LightLayer.BLOCK, helper.absolutePos(new BlockPos(x, 1, z)));
                    helper.assertTrue(light >= 8, "No dark spot on the quarry floor where monsters could spawn, light " + light + " at " + x + ", 1, " + z);
                }
            }
        });
    }

    public static void minerUsesMoreFurnacesAtHigherLevels(GameTestHelper helper) {
        Villager miner = spawnMiner(helper);
        miner.setVillagerData(miner.getVillagerData().withLevel(3));
        miner.getInventory().addItem(new ItemStack(Items.WOODEN_PICKAXE));
        miner.getInventory().addItem(new ItemStack(Items.RAW_IRON, 9));
        miner.getInventory().addItem(new ItemStack(Items.COAL, 3));
        List<BlockPos> furnaces = List.of(new BlockPos(6, 4, 14), new BlockPos(6, 4, 10));
        furnaces.forEach(furnace -> helper.setBlock(furnace, Blocks.FURNACE));
        boolean[] usedTogether = {false};
        helper.onEachTick(() -> {
            long busy = furnaces.stream().filter(furnace -> helper.getBlockEntity(furnace, net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity.class).getItem(0).is(Items.RAW_IRON)).count();
            if (busy == furnaces.size()) usedTogether[0] = true;
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(usedTogether[0], "A journeyman shares its ore between its furnace minecart and two furnaces beside it");
            helper.assertTrue(count(miner, Items.IRON_INGOT) == 9, "...and collects every ingot, has " + count(miner, Items.IRON_INGOT));
        });
    }

    public static void noviceMinerUsesOnlyItsFurnaceMinecart(GameTestHelper helper) {
        Villager miner = spawnMiner(helper);
        miner.getInventory().addItem(new ItemStack(Items.WOODEN_PICKAXE));
        miner.getInventory().addItem(new ItemStack(Items.RAW_IRON, 3));
        miner.getInventory().addItem(new ItemStack(Items.COAL, 2));
        BlockPos furnace = new BlockPos(6, 4, 14);
        helper.setBlock(furnace, Blocks.FURNACE);

        helper.succeedWhen(() -> {
            helper.assertTrue(count(miner, Items.IRON_INGOT) == 3, "The novice smelts its ore, has " + count(miner, Items.IRON_INGOT) + " ingots");
            var plain = helper.getBlockEntity(furnace, net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity.class);
            helper.assertTrue(plain.getItem(0).isEmpty() && plain.getItem(2).isEmpty(), "...in its furnace minecart alone, leaving the furnace beside it to you");
        });
    }

    /** A 3x3 pit dug one block deep into the platform beside the furnace minecart: the quarry, two layers above a barrier floor. */
    static void quarry(GameTestHelper helper) {
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
