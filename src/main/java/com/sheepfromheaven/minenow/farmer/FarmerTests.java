package com.sheepfromheaven.minenow.farmer;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
    private static final BlockPos COMPOSTER = new BlockPos(11, 1, 12);
    private static final BlockPos CHEST_NEAR_COMPOSTER = new BlockPos(11, 1, 8);
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
        BlockPos crop = COMPOSTER.east();
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

    public static void onlyTheFieldConnectedToTheComposterIsFarmed(GameTestHelper helper) {
        setUpFarm(helper, false);
        helper.setBlock(HOME_CHEST, Blocks.CHEST);
        BlockState ripeWheat = ((CropBlock) Blocks.WHEAT).getStateForAge(((CropBlock) Blocks.WHEAT).getMaxAge());
        helper.setBlock(4, 0, 4, Blocks.WATER);
        for (int x = 2; x <= 6; x++) {
            for (int z = 2; z <= 6; z++) {
                if (x == 4 && z == 4) continue;
                helper.setBlock(x, 0, z, Blocks.FARMLAND);
                helper.setBlock(x, 1, z, ripeWheat);
            }
        }
        Villager farmer = spawnFarmer(helper);
        GameTestHelpers.giveHome(helper, farmer, BED);

        helper.succeedWhen(() -> {
            forEachFieldCrop(helper, (pos, state) ->
                    helper.assertTrue(!((CropBlock) state.getBlock()).isMaxAge(state), "Connected field not finished yet at " + pos));
            for (int x = 2; x <= 6; x++) {
                for (int z = 2; z <= 6; z++) {
                    if (x == 4 && z == 4) continue;
                    BlockState state = helper.getBlockState(new BlockPos(x, 1, z));
                    helper.assertTrue(state.getBlock() instanceof CropBlock c && c.isMaxAge(state),
                            "A separate farm that doesn't touch the composter must be left alone, but (" + x + ", 1, " + z + ") was harvested");
                }
            }
        });
    }

    public static void noviceFarmerOnlyWorksTenBlocksOut(GameTestHelper helper) {
        setUpFarmWithFarStrip(helper);
        Villager farmer = spawnFarmer(helper);
        GameTestHelpers.giveHome(helper, farmer, BED);
        boolean[] checking = {false};

        helper.onEachTick(() -> {
            if (checking[0] || !anyRipeLeftExcept(helper, new BlockPos(22, 1, 12))) {
                if (checking[0]) return;
                checking[0] = true;
                helper.runAfterDelay(300, () -> {
                    assertHarvested(helper, new BlockPos(22, 1, 12), false, "11 blocks out is beyond a novice's reach");
                    helper.succeed();
                });
            }
        });
    }

    public static void masterFarmerWorksFurtherOut(GameTestHelper helper) {
        setUpFarmWithFarStrip(helper);
        Villager farmer = spawnFarmer(helper);
        farmer.setVillagerData(farmer.getVillagerData().withLevel(5));
        GameTestHelpers.giveHome(helper, farmer, BED);

        helper.succeedWhen(() -> assertHarvested(helper, new BlockPos(22, 1, 12), true, "A master should reach 11 blocks out"));
    }

    private static boolean anyRipeLeftExcept(GameTestHelper helper, BlockPos ignored) {
        boolean[] ripe = {false};
        forEachFieldCrop(helper, (pos, state) -> ripe[0] |= ((CropBlock) state.getBlock()).isMaxAge(state));
        BlockState near = helper.getBlockState(new BlockPos(21, 1, 12));
        return ripe[0] || (near.getBlock() instanceof CropBlock crop && crop.isMaxAge(near));
    }

    private static void setUpFarmWithFarStrip(GameTestHelper helper) {
        setUpFarm(helper, false);
        helper.setBlock(HOME_CHEST, Blocks.CHEST);
        BlockState ripeWheat = ((CropBlock) Blocks.WHEAT).getStateForAge(((CropBlock) Blocks.WHEAT).getMaxAge());
        for (int x = 21; x <= 22; x++) {
            helper.setBlock(x, 0, 12, Blocks.FARMLAND);
            helper.setBlock(x, 1, 12, ripeWheat);
        }
    }

    private static void assertHarvested(GameTestHelper helper, BlockPos pos, boolean expected, String message) {
        BlockState state = helper.getBlockState(pos);
        boolean harvested = !(state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state));
        helper.assertTrue(harvested == expected, message + " (" + pos + ")");
    }

    public static void composterWalledInOnFourSidesHasNoField(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        for (int x = 7; x <= 15; x++) {
            for (int z = 8; z <= 16; z++) helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
        }
        helper.setBlock(14, 0, 15, Blocks.WATER);
        helper.setBlock(COMPOSTER, Blocks.COMPOSTER);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            helper.setBlock(COMPOSTER.relative(side), Blocks.OAK_STAIRS.defaultBlockState().setValue(
                    net.minecraft.world.level.block.StairBlock.FACING, side.getOpposite()));
        }
        Villager farmer = GameTestHelpers.spawnEmployed(helper, new BlockPos(14, 1, 9), COMPOSTER, VillagerProfession.FARMER, PoiTypes.FARMER);
        farmer.getInventory().addItem(new ItemStack(Items.IRON_HOE));
        farmer.getInventory().addItem(new ItemStack(Items.WHEAT_SEEDS, 16));

        helper.runAfterDelay(400, () -> {
            for (int x = 7; x <= 15; x++) {
                for (int z = 8; z <= 16; z++) {
                    helper.assertTrue(!helper.getBlockState(new BlockPos(x, 0, z)).is(Blocks.FARMLAND),
                            "A composter walled in on all four sides has no field, but (" + x + ", 0, " + z + ") was tilled");
                }
            }
            helper.succeed();
        });
    }

    public static void aLineOfStringEndsTheField(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        for (int x = 12; x <= 20; x++) {
            for (int z = 8; z <= 16; z++) helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
        }
        helper.setBlock(16, 0, 12, Blocks.WATER);
        for (int z = 8; z <= 16; z++) helper.setBlock(15, 1, z, Blocks.TRIPWIRE);
        helper.setBlock(COMPOSTER, Blocks.COMPOSTER);
        Villager farmer = GameTestHelpers.spawnEmployed(helper, new BlockPos(13, 1, 10), COMPOSTER, VillagerProfession.FARMER, PoiTypes.FARMER);
        farmer.getInventory().addItem(new ItemStack(Items.IRON_HOE));

        boolean[] checking = {false};
        helper.onEachTick(() -> {
            if (checking[0]) return;
            for (int x = 12; x <= 14; x++) {
                for (int z = 8; z <= 16; z++) {
                    if (!helper.getBlockState(new BlockPos(x, 0, z)).is(Blocks.FARMLAND)) return;
                }
            }
            checking[0] = true;
            helper.runAfterDelay(300, () -> {
                for (int x = 15; x <= 20; x++) {
                    for (int z = 8; z <= 16; z++) {
                        helper.assertTrue(!helper.getBlockState(new BlockPos(x, 0, z)).is(Blocks.FARMLAND),
                                "Ground under or beyond the string must stay out of the field, but (" + x + ", 0, " + z + ") was tilled");
                    }
                }
                helper.succeed();
            });
        });
    }

    public static void farmerTillsConnectedGroundNearWater(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(COMPOSTER, Blocks.COMPOSTER);
        helper.setBlock(15, 0, 11, Blocks.WATER);
        for (int x = 12; x <= 14; x++) helper.setBlock(x, 0, 12, Blocks.FARMLAND);
        for (int x = 15; x <= 21; x++) helper.setBlock(x, 0, 12, Blocks.DIRT);
        for (int x = 12; x <= 14; x++) helper.setBlock(x, 0, 13, Blocks.GRASS_BLOCK);
        helper.setBlock(13, 1, 13, Blocks.SHORT_GRASS);
        Villager farmer = spawnFarmer(helper);
        farmer.getInventory().addItem(new ItemStack(Items.WHEAT_SEEDS, 32));

        helper.succeedWhen(() -> {
            for (int x = 15; x <= 19; x++) helper.assertBlockPresent(Blocks.FARMLAND, new BlockPos(x, 0, 12));
            for (int x = 12; x <= 14; x++) helper.assertBlockPresent(Blocks.FARMLAND, new BlockPos(x, 0, 13));
            helper.assertBlockPresent(Blocks.DIRT, new BlockPos(20, 0, 12));
            helper.assertBlockPresent(Blocks.DIRT, new BlockPos(21, 0, 12));
            helper.assertTrue(helper.getBlockState(new BlockPos(16, 1, 12)).getBlock() instanceof CropBlock, "Tilled ground should be planted");
        });
    }

    public static void farmerWithoutHoeLeavesRipeCropsAlone(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(COMPOSTER, Blocks.COMPOSTER);
        BlockState ripeWheat = ((CropBlock) Blocks.WHEAT).getStateForAge(((CropBlock) Blocks.WHEAT).getMaxAge());
        for (int x = 12; x <= 14; x++) {
            for (int z = 11; z <= 13; z++) {
                helper.setBlock(x, 0, z, Blocks.FARMLAND);
                helper.setBlock(x, 1, z, ripeWheat);
            }
        }
        Villager farmer = spawnFarmerWithoutHoe(helper);
        farmer.getInventory().addItem(new ItemStack(Items.WHEAT_SEEDS, 8));

        helper.runAfterDelay(400, () -> {
            for (int x = 12; x <= 14; x++) {
                for (int z = 11; z <= 13; z++) {
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
