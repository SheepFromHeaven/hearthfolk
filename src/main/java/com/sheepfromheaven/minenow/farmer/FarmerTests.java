package com.sheepfromheaven.minenow.farmer;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class FarmerTests {
    private static final BlockPos COMPOSTER = new BlockPos(2, 1, 2);
    private static final BlockPos CHEST_NEAR_COMPOSTER = new BlockPos(2, 1, 4);
    private static final BlockPos BED = new BlockPos(6, 1, 18);
    private static final BlockPos HOME_CHEST = new BlockPos(5, 1, 20);
    private static final int FIELD_MIN = 12;
    private static final int FIELD_MAX = 20;

    private FarmerTests() {}

    public static void farmerHarvestsAndReplantsFieldBeyondVanillaReach(GameTestHelper helper) {
        setUpFarm(helper, false);
        spawnFarmer(helper);

        helper.succeedWhen(() -> forEachFieldCrop(helper, (pos, state) -> {
            helper.assertTrue(state.getBlock() instanceof CropBlock, "Expected a replanted crop at " + pos);
            helper.assertTrue(!((CropBlock) state.getBlock()).isMaxAge(state), "Expected crop at " + pos + " to be harvested");
        }));
    }

    public static void farmerKeepsTendingAfterBrainIsRebuilt(GameTestHelper helper) {
        setUpFarm(helper, false);
        Villager farmer = spawnFarmer(helper);
        helper.runAfterDelay(40, () -> farmer.refreshBrain(helper.getLevel()));

        helper.succeedWhen(() -> forEachFieldCrop(helper, (pos, state) ->
                helper.assertTrue(!((CropBlock) state.getBlock()).isMaxAge(state), "Expected crop at " + pos + " to be harvested")));
    }

    public static void farmerDepositsHarvestInOwnChestAtHome(GameTestHelper helper) {
        setUpFarm(helper, true);
        helper.setBlock(HOME_CHEST, Blocks.CHEST);
        Villager farmer = spawnFarmer(helper);
        GameTestHelpers.giveHome(helper, farmer, BED);

        helper.succeedWhen(() -> {
            int crops = (FIELD_MAX - FIELD_MIN + 1) * (FIELD_MAX - FIELD_MIN + 1) - 1;
            int atHome = countWheat(helper, HOME_CHEST);
            helper.assertTrue(atHome >= crops - 8, "Expected about " + crops + " wheat in the farmer's own chest, found " + atHome);
            helper.assertTrue(countWheat(helper, CHEST_NEAR_COMPOSTER) == 0, "Farmer should not use a chest it doesn't own");
            helper.assertTrue(farmer.getInventory().countItem(Items.WHEAT_SEEDS) > 0, "Farmer should keep seeds for replanting");
            helper.assertTrue(Hoes.has(farmer), "Farmer should keep its hoe when depositing");
        });
    }

    private static int countWheat(GameTestHelper helper, BlockPos chestPos) {
        ChestBlockEntity chest = helper.getBlockEntity(chestPos, ChestBlockEntity.class);
        int wheat = 0;
        for (int i = 0; i < chest.getContainerSize(); i++) {
            ItemStack stack = chest.getItem(i);
            if (stack.is(Items.WHEAT)) wheat += stack.getCount();
        }
        return wheat;
    }

    private static Villager spawnFarmer(GameTestHelper helper) {
        Villager farmer = spawnFarmerWithoutHoe(helper);
        farmer.getInventory().addItem(new ItemStack(Items.IRON_HOE));
        return farmer;
    }

    private static Villager spawnFarmerWithoutHoe(GameTestHelper helper) {
        return GameTestHelpers.spawnEmployed(helper, COMPOSTER.east(), COMPOSTER, VillagerProfession.FARMER, PoiTypes.FARMER);
    }

    public static void betterHoesReapFaster(GameTestHelper helper) {
        java.util.function.ToIntFunction<net.minecraft.world.item.Item> ticks = hoe -> Hoes.harvestTicks(new ItemStack(hoe), 0);
        helper.assertTrue(ticks.applyAsInt(Items.WOODEN_HOE) > ticks.applyAsInt(Items.STONE_HOE), "stone should beat wood");
        helper.assertTrue(ticks.applyAsInt(Items.STONE_HOE) > ticks.applyAsInt(Items.IRON_HOE), "iron should beat stone");
        helper.assertTrue(ticks.applyAsInt(Items.IRON_HOE) > ticks.applyAsInt(Items.DIAMOND_HOE), "diamond should beat iron");
        helper.assertTrue(ticks.applyAsInt(Items.DIAMOND_HOE) > ticks.applyAsInt(Items.NETHERITE_HOE), "netherite should beat diamond");
        helper.assertTrue(ticks.applyAsInt(Items.GOLDEN_HOE) >= 5, "even the fastest hoe takes a moment");
        ItemStack iron = new ItemStack(Items.IRON_HOE);
        helper.assertTrue(Hoes.harvestTicks(iron, 3) < Hoes.harvestTicks(iron, 0), "Efficiency III should reap faster");
        helper.succeed();
    }

    public static void harvestingTakesTimeAndSwingsTheHoe(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(COMPOSTER, Blocks.COMPOSTER);
        BlockPos crop = COMPOSTER.east().south();
        helper.setBlock(crop.below(), Blocks.FARMLAND);
        helper.setBlock(crop, ((CropBlock) Blocks.WHEAT).getStateForAge(((CropBlock) Blocks.WHEAT).getMaxAge()));
        Villager farmer = spawnFarmerWithoutHoe(helper);
        farmer.getInventory().addItem(new ItemStack(Items.WOODEN_HOE));
        boolean[] swung = {false};
        helper.onEachTick(() -> swung[0] |= farmer.getCurrentSwing() != null);

        helper.runAfterDelay(20, () -> {
            BlockState state = helper.getBlockState(crop);
            helper.assertTrue(state.getBlock() instanceof CropBlock c && c.isMaxAge(state), "A wooden hoe should not reap a crop within a second");
        });
        helper.runAfterDelay(200, () -> {
            BlockState state = helper.getBlockState(crop);
            helper.assertTrue(!(state.getBlock() instanceof CropBlock c && c.isMaxAge(state)), "The crop should be reaped eventually");
            helper.assertTrue(swung[0], "The farmer should swing its hoe while reaping");
            helper.succeed();
        });
    }

    public static void farmerHoldsItsHoeWhileWorkingWithAPlayerWatching(GameTestHelper helper) {
        setUpFarm(helper, false);
        Villager farmer = spawnFarmer(helper);
        @SuppressWarnings("removal")
        net.minecraft.server.level.ServerPlayer watcher = helper.makeMockServerPlayerInLevel();
        int[] working = {0};
        int[] emptyHanded = {0};
        helper.onEachTick(() -> {
            watcher.snapTo(farmer.getX() + 2, farmer.getY(), farmer.getZ());
            boolean tending = farmer.getBrain().getRunningBehaviors().stream().anyMatch(TendFields.class::isInstance);
            if (!tending) return;
            working[0]++;
            if (!Hoes.isHoe(farmer.getMainHandItem())) emptyHanded[0]++;
        });

        helper.runAfterDelay(600, () -> {
            helper.assertTrue(working[0] > 100, "Farmer should have been working, ticks=" + working[0]);
            helper.assertTrue(emptyHanded[0] <= 5, "Farmer should hold its hoe while working, empty-handed for " + emptyHanded[0] + " of " + working[0] + " ticks");
            helper.succeed();
        });
    }

    public static void farmerWithoutHoeLeavesRipeCropsAlone(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(COMPOSTER, Blocks.COMPOSTER);
        BlockState ripeWheat = ((CropBlock) Blocks.WHEAT).getStateForAge(((CropBlock) Blocks.WHEAT).getMaxAge());
        for (int x = 3; x <= 5; x++) {
            for (int z = 3; z <= 5; z++) {
                helper.setBlock(x, 0, z, Blocks.FARMLAND);
                helper.setBlock(x, 1, z, ripeWheat);
            }
        }
        Villager farmer = spawnFarmerWithoutHoe(helper);
        farmer.getInventory().addItem(new ItemStack(Items.WHEAT_SEEDS, 8));

        helper.runAfterDelay(400, () -> {
            for (int x = 3; x <= 5; x++) {
                for (int z = 3; z <= 5; z++) {
                    BlockState state = helper.getBlockState(new BlockPos(x, 1, z));
                    helper.assertTrue(state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state),
                            "A farmer without a hoe must not harvest, but (" + x + ", 1, " + z + ") was touched");
                }
            }
            helper.succeed();
        });
    }

    public static void harvestingWearsOutTheHoe(GameTestHelper helper) {
        setUpFarm(helper, false);
        Villager farmer = spawnFarmerWithoutHoe(helper);
        ItemStack hoe = new ItemStack(Items.IRON_HOE);
        hoe.setDamageValue(hoe.getMaxDamage() - 5);
        farmer.getInventory().addItem(hoe);

        helper.runAfterDelay(1200, () -> {
            int[] harvested = {0};
            forEachFieldCrop(helper, (pos, state) -> {
                if (!(state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state))) harvested[0]++;
            });
            helper.assertTrue(harvested[0] == 5, "A hoe with 5 uses left should harvest exactly 5 crops, harvested " + harvested[0]);
            helper.assertTrue(!Hoes.has(farmer), "The worn-out hoe should be gone");
            helper.succeed();
        });
    }

    public static void farmerPicksUpAHoeThrownToIt(GameTestHelper helper) {
        setUpFarm(helper, false);
        Villager farmer = spawnFarmerWithoutHoe(helper);
        helper.runAfterDelay(20, () -> {
            BlockPos at = helper.absolutePos(COMPOSTER.east(3).south(3));
            helper.getLevel().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(
                    helper.getLevel(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5, new ItemStack(Items.IRON_HOE)));
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(Hoes.has(farmer), "Farmer should pick up the hoe");
            helper.assertTrue(anyFieldCropHarvested(helper), "Farmer should start farming once it has a hoe");
        });
    }

    public static void farmerFetchesASpareHoeFromItsHomeChest(GameTestHelper helper) {
        setUpFarm(helper, false);
        helper.setBlock(HOME_CHEST, Blocks.CHEST);
        helper.getBlockEntity(HOME_CHEST, ChestBlockEntity.class).setItem(0, new ItemStack(Items.IRON_HOE));
        Villager farmer = spawnFarmerWithoutHoe(helper);
        GameTestHelpers.giveHome(helper, farmer, BED);

        helper.succeedWhen(() -> {
            helper.assertTrue(Hoes.has(farmer), "Farmer should take the hoe from its own chest");
            helper.assertTrue(helper.getBlockEntity(HOME_CHEST, ChestBlockEntity.class).isEmpty(), "Hoe should have left the chest");
            helper.assertTrue(anyFieldCropHarvested(helper), "Farmer should farm with the fetched hoe");
        });
    }

    private static boolean anyFieldCropHarvested(GameTestHelper helper) {
        boolean[] any = {false};
        forEachFieldCrop(helper, (pos, state) -> {
            if (!(state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state))) any[0] = true;
        });
        return any[0];
    }

    private static void setUpFarm(GameTestHelper helper, boolean withChest) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(COMPOSTER, Blocks.COMPOSTER);
        if (withChest) helper.setBlock(CHEST_NEAR_COMPOSTER, Blocks.CHEST);
        BlockState ripeWheat = ((CropBlock) Blocks.WHEAT).getStateForAge(((CropBlock) Blocks.WHEAT).getMaxAge());
        helper.setBlock(FIELD_MIN + 4, 0, FIELD_MIN + 4, Blocks.WATER);
        for (int x = FIELD_MIN; x <= FIELD_MAX; x++) {
            for (int z = FIELD_MIN; z <= FIELD_MAX; z++) {
                if (x == FIELD_MIN + 4 && z == FIELD_MIN + 4) continue;
                helper.setBlock(x, 0, z, Blocks.FARMLAND);
                helper.setBlock(x, 1, z, ripeWheat);
            }
        }
    }

    private interface CropCheck {
        void check(BlockPos pos, BlockState state);
    }

    private static void forEachFieldCrop(GameTestHelper helper, CropCheck check) {
        for (int x = FIELD_MIN; x <= FIELD_MAX; x++) {
            for (int z = FIELD_MIN; z <= FIELD_MAX; z++) {
                if (x == FIELD_MIN + 4 && z == FIELD_MIN + 4) continue;
                BlockPos pos = new BlockPos(x, 1, z);
                check.check(pos, helper.getBlockState(pos));
            }
        }
    }
}
