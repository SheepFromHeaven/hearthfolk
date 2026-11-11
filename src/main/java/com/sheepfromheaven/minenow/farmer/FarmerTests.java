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

    public static void farmerDepositsHarvestInItsWorkshopChest(GameTestHelper helper) {
        setUpFarm(helper, true);
        helper.setBlock(HOME_CHEST, Blocks.CHEST);
        Villager farmer = spawnFarmer(helper);
        GameTestHelpers.giveHome(helper, farmer, BED);

        helper.succeedWhen(() -> {
            int crops = (FIELD_MAX - FIELD_MIN + 1) * (FIELD_MAX - FIELD_MIN + 1) - 1;
            int atWorkshop = countWheat(helper, CHEST_NEAR_COMPOSTER);
            int baked = 3 * (farmer.getInventory().countItem(Items.BREAD) + countItem(helper, HOME_CHEST, Items.BREAD));
            helper.assertTrue(atWorkshop + baked >= crops - 8, "Expected about " + crops + " wheat in the chest by the composter, its workshop chest, or baked, found "
                    + atWorkshop + " and " + baked + " baked");
            helper.assertTrue(countWheat(helper, HOME_CHEST) == 0, "The harvest is trade goods, so none of it goes home");
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

    private static int countItem(GameTestHelper helper, BlockPos chest, net.minecraft.world.item.Item item) {
        var container = helper.getBlockEntity(chest, net.minecraft.world.level.block.entity.ChestBlockEntity.class);
        int count = 0;
        for (int i = 0; i < container.getContainerSize(); i++) if (container.getItem(i).is(item)) count += container.getItem(i).getCount();
        return count;
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

    public static void farmerRetillsFarmlandThatWasTrampled(GameTestHelper helper) {
        setUpFarm(helper, false);
        Villager farmer = spawnFarmer(helper);
        BlockPos trampled = new BlockPos(14, 0, 14);
        boolean[] broken = {false};
        helper.onEachTick(() -> {
            if (!broken[0] && !helper.getBlockState(trampled.above()).is(Blocks.WHEAT)) return;
            if (!broken[0] && anyFieldCropHarvested(helper)) {
                broken[0] = true;
                helper.setBlock(trampled.above(), Blocks.AIR);
                helper.setBlock(trampled, Blocks.DIRT);
            }
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(broken[0], "Field was never trampled");
            helper.assertBlockPresent(Blocks.FARMLAND, trampled);
            helper.assertTrue(helper.getBlockState(trampled.above()).getBlock() instanceof CropBlock, "Retilled ground should be replanted");
        });
    }

    public static void farmerWithAFullInventoryStillHarvests(GameTestHelper helper) {
        setUpFarm(helper, false);
        Villager farmer = spawnFarmer(helper);
        for (int i = 0; i < farmer.getInventory().getContainerSize(); i++) {
            if (farmer.getInventory().getItem(i).isEmpty()) farmer.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        }

        helper.succeedWhen(() -> helper.assertTrue(anyFieldCropHarvested(helper),
                "A farmer with nowhere to put its harvest should still reap, dropping what it can't carry"));
    }

    public static void farmerReportExplainsWhatTheFarmerIsMissing(GameTestHelper helper) {
        setUpFarm(helper, false);
        Villager farmer = spawnFarmerWithoutHoe(helper);

        String report = String.join("\n", FarmerReport.describe(helper.getLevel(), farmer).stream().map(c -> c.getString()).toList());

        helper.assertTrue(report.contains("no tool"), "Report should say the farmer has no tool:\n" + report);
        helper.assertTrue(report.contains("work"), "Report should show the current activity:\n" + report);
        helper.assertTrue(report.contains("no home chest"), "Report should say there is no home chest:\n" + report);
        helper.assertTrue(report.contains("80 ripe"), "Report should count the ripe crops in the field:\n" + report);
        helper.succeed();
    }

    public static void aFarmerHarvestsItsFieldBeforeItBakes(GameTestHelper helper) {
        setUpFarm(helper, true);
        Villager farmer = spawnFarmer(helper);
        boolean[] bakedEarly = {false};
        helper.onEachTick(() -> {
            boolean[] ripe = {false};
            forEachFieldCrop(helper, (pos, state) -> ripe[0] |= ((CropBlock) state.getBlock()).isMaxAge(state));
            if (ripe[0] && farmer.getBrain().getRunningBehaviors().stream().anyMatch(b -> b instanceof com.sheepfromheaven.minenow.villager.CraftAtWorkstation)) bakedEarly[0] = true;
        });

        helper.succeedWhen(() -> {
            boolean[] ripe = {false};
            forEachFieldCrop(helper, (pos, state) -> ripe[0] |= ((CropBlock) state.getBlock()).isMaxAge(state));
            helper.assertFalse(ripe[0], "The farmer harvests its field");
            helper.assertFalse(bakedEarly[0], "...and only bakes once there's nothing ripe left, or baking keeps calling it off the field");
        });
    }

    public static void farmerBakesBreadFromItsWheat(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(COMPOSTER, Blocks.COMPOSTER);
        Villager farmer = spawnFarmer(helper);
        farmer.getInventory().addItem(new ItemStack(Items.WHEAT, 30));

        helper.succeedWhen(() -> {
            helper.assertTrue(farmer.getInventory().countItem(Items.BREAD) >= 2, "The farmer bakes bread for the village to eat, has " + farmer.getInventory().countItem(Items.BREAD));
            helper.assertTrue(farmer.getInventory().countItem(Items.WHEAT) == 30 - 3 * farmer.getInventory().countItem(Items.BREAD),
                    "...three wheat a loaf, has " + farmer.getInventory().countItem(Items.WHEAT) + " wheat");
        });
    }

    public static void aHungryVillagerBuysTheFarmersBread(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(COMPOSTER, Blocks.COMPOSTER);
        Villager farmer = spawnFarmer(helper);
        farmer.getInventory().addItem(new ItemStack(Items.WHEAT, 64));
        farmer.getInventory().addItem(new ItemStack(Items.WHEAT, 32));
        Villager hungry = helper.spawn(net.minecraft.world.entity.EntityTypes.VILLAGER, new BlockPos(4, 1, 4));
        hungry.getInventory().addItem(new ItemStack(Items.EMERALD, 3));
        com.sheepfromheaven.minenow.villager.Meals.makeHungry(hungry);

        helper.succeedWhen(() -> helper.assertTrue(hungry.getInventory().countItem(Items.EMERALD) < 3,
                "The farmer bakes more bread than it keeps for itself, so a hungry villager can buy some; farmer has "
                        + farmer.getInventory().countItem(Items.BREAD) + " bread"));
    }

    public static void farmerDoesNotCompostSeedsAtTheComposter(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(COMPOSTER, Blocks.COMPOSTER);
        Villager farmer = spawnFarmer(helper);
        farmer.getInventory().addItem(new ItemStack(Items.WHEAT, 30));
        farmer.getInventory().addItem(new ItemStack(Items.WHEAT_SEEDS, 64));
        helper.onEachTick(() -> {
            BlockPos at = helper.absolutePos(COMPOSTER.east());
            farmer.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        });

        helper.runAfterDelay(2300, () -> {
            helper.assertTrue(farmer.getInventory().countItem(Items.WHEAT_SEEDS) == 64, "Seeds should not be composted, has " + farmer.getInventory().countItem(Items.WHEAT_SEEDS));
            helper.assertTrue(helper.getBlockState(COMPOSTER).getValue(net.minecraft.world.level.block.ComposterBlock.LEVEL) == 0, "The composter should stay empty");
            helper.succeed();
        });
    }

    public static void farmerUsesBoneMealOnGrowingCrops(GameTestHelper helper) {
        setUpFarm(helper, false);
        BlockState young = ((CropBlock) Blocks.WHEAT).getStateForAge(0);
        forEachFieldCrop(helper, (pos, state) -> helper.setBlock(pos, young));
        Villager farmer = spawnFarmer(helper);
        farmer.getInventory().addItem(new ItemStack(Items.BONE_MEAL, 8));

        helper.succeedWhen(() -> helper.assertTrue(farmer.getInventory().countItem(Items.BONE_MEAL) < 8,
                "The farmer should use bone meal on its young crops"));
    }

    public static void farmerGainsExperienceFromHarvesting(GameTestHelper helper) {
        setUpFarm(helper, false);
        Villager farmer = spawnFarmer(helper);
        GameTestHelpers.allowWorkXp(farmer);

        helper.succeedWhen(() -> helper.assertTrue(farmer.getVillagerXp() > 0,
                "A farmer should earn experience from harvesting, xp " + farmer.getVillagerXp()));
    }

    public static void cropsAndSeedsAreRecognizedBeyondVanillaWheat(GameTestHelper helper) {
        BlockState ripePitcher = Blocks.PITCHER_CROP.defaultBlockState().setValue(net.minecraft.world.level.block.PitcherCropBlock.AGE, 4);
        BlockState youngPitcher = Blocks.PITCHER_CROP.defaultBlockState().setValue(net.minecraft.world.level.block.PitcherCropBlock.AGE, 2);
        helper.assertTrue(Crops.isCrop(Blocks.WHEAT.defaultBlockState()), "wheat is a crop");
        helper.assertTrue(Crops.isCrop(youngPitcher), "a crop from #minecraft:crops counts even if it isn't a CropBlock");
        helper.assertTrue(!Crops.isCrop(Blocks.MELON_STEM.defaultBlockState()), "stems grow fruit beside them and are not field crops");
        helper.assertTrue(!Crops.isCrop(Blocks.SWEET_BERRY_BUSH.defaultBlockState()), "berry bushes are not in the crop tag");
        helper.assertTrue(Crops.isRipe(ripePitcher) && !Crops.isRipe(youngPitcher), "ripeness is read from the age property when there is no CropBlock");
        helper.assertTrue(Crops.isSeed(new ItemStack(Items.PITCHER_POD)), "an item that plants a crop is a seed");
        helper.assertTrue(!Crops.isSeed(new ItemStack(Items.MELON_SEEDS)), "melon seeds plant a stem, not a field crop");
        helper.assertTrue(!Crops.isSeed(new ItemStack(Items.WHEAT)), "wheat itself is produce, not a seed");
        helper.succeed();
    }

    public static void farmerHarvestsByRightClickWhenAModProvidesIt(GameTestHelper helper) {
        setUpFarm(helper, false);
        com.sheepfromheaven.minenow.gametest.SimulatedRightClickHarvest.enableIn(helper);
        Villager farmer = spawnFarmer(helper);

        helper.succeedWhen(() -> {
            forEachFieldCrop(helper, (pos, state) -> helper.assertTrue(state.getBlock() instanceof CropBlock crop && !crop.isMaxAge(state),
                    "Field not finished at " + pos));
            int viaRightClick = com.sheepfromheaven.minenow.gametest.SimulatedRightClickHarvest.harvestsIn(helper);
            helper.assertTrue(viaRightClick >= 75, "The farmer should harvest by right-clicking when a mod handles it, did so " + viaRightClick + " times");
            helper.assertTrue(farmer.getInventory().countItem(Items.WHEAT) >= 60, "The yield should end up in the farmer's inventory, has "
                    + farmer.getInventory().countItem(Items.WHEAT));
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, helper.getBounds()).isEmpty(),
                    "Nothing should be left lying on the ground");
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
                            "A farmer without a hoe must not harvest, but (" + x + ", 1, " + z + ") was touched: now " + state
                                    + " on " + helper.getBlockState(new BlockPos(x, 0, z)) + ", farmer holds " + farmer.getInventory().getItems());
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


    private static final BlockPos STEM = new BlockPos(12, 1, 12);
    private static final BlockPos FRUIT = new BlockPos(13, 1, 12);

    /** A stem at {@link #STEM} with its fruit at {@link #FRUIT}, on dirt near water, the first cells of the farmer's field. */
    private static void setUpFruit(GameTestHelper helper, net.minecraft.world.level.block.Block stem, net.minecraft.world.level.block.Block attached,
                                   net.minecraft.world.level.block.Block fruit, boolean withStem) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(COMPOSTER, Blocks.COMPOSTER);
        helper.setBlock(13, 0, 10, Blocks.WATER);
        helper.setBlock(STEM.below(), Blocks.FARMLAND);
        helper.setBlock(FRUIT.below(), Blocks.DIRT);
        if (withStem) {
            helper.setBlock(STEM, attached.defaultBlockState().setValue(net.minecraft.world.level.block.AttachedStemBlock.FACING, Direction.EAST));
        }
        helper.setBlock(FRUIT, fruit);
    }

    private static Villager spawnLeveledFarmer(GameTestHelper helper, int level) {
        Villager farmer = spawnFarmer(helper);
        farmer.setVillagerData(farmer.getVillagerData().withLevel(level));
        return farmer;
    }

    public static void aJourneymanFarmerHarvestsAPumpkinGrownBesideItsStem(GameTestHelper helper) {
        setUpFruit(helper, Blocks.PUMPKIN_STEM, Blocks.ATTACHED_PUMPKIN_STEM, Blocks.PUMPKIN, true);
        Villager farmer = spawnLeveledFarmer(helper, 3);

        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getBlockState(FRUIT).isAir(), "The pumpkin should be harvested");
            helper.assertTrue(farmer.getInventory().countItem(Items.PUMPKIN) == 1, "The pumpkin should be in the farmer's inventory");
            helper.assertTrue(helper.getBlockState(STEM).is(Blocks.PUMPKIN_STEM), "The stem stays to grow the next one, is " + helper.getBlockState(STEM));
        });
    }

    public static void aNoviceFarmerLeavesPumpkinsAlone(GameTestHelper helper) {
        setUpFruit(helper, Blocks.PUMPKIN_STEM, Blocks.ATTACHED_PUMPKIN_STEM, Blocks.PUMPKIN, true);
        spawnLeveledFarmer(helper, 2);

        helper.runAfterDelay(400, () -> {
            helper.assertBlockPresent(Blocks.PUMPKIN, FRUIT);
            helper.succeed();
        });
    }

    public static void aPumpkinWithoutAStemIsNeverTaken(GameTestHelper helper) {
        setUpFruit(helper, Blocks.PUMPKIN_STEM, Blocks.ATTACHED_PUMPKIN_STEM, Blocks.PUMPKIN, false);
        spawnLeveledFarmer(helper, 5);

        helper.runAfterDelay(400, () -> {
            helper.assertBlockPresent(Blocks.PUMPKIN, FRUIT);
            helper.succeed();
        });
    }

    public static void melonsWaitForAnExpertFarmer(GameTestHelper helper) {
        setUpFruit(helper, Blocks.MELON_STEM, Blocks.ATTACHED_MELON_STEM, Blocks.MELON, true);
        spawnLeveledFarmer(helper, 3);

        helper.runAfterDelay(400, () -> {
            helper.assertBlockPresent(Blocks.MELON, FRUIT);
            helper.succeed();
        });
    }

    public static void anExpertFarmerHarvestsMelons(GameTestHelper helper) {
        setUpFruit(helper, Blocks.MELON_STEM, Blocks.ATTACHED_MELON_STEM, Blocks.MELON, true);
        Villager farmer = spawnLeveledFarmer(helper, 4);

        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getBlockState(FRUIT).isAir(), "The melon should be harvested");
            helper.assertTrue(farmer.getInventory().countItem(Items.MELON_SLICE) >= 3, "The melon slices should be in the farmer's inventory");
            helper.assertTrue(helper.getBlockState(STEM).is(Blocks.MELON_STEM), "The stem stays");
        });
    }

    public static void aFarmerNeverFillsTheLastFreeCellBesideAStemWithCrops(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(COMPOSTER, Blocks.COMPOSTER);
        helper.setBlock(13, 0, 10, Blocks.WATER);
        for (int x = 12; x <= 14; x++) helper.setBlock(x, 0, 12, Blocks.FARMLAND);
        Villager farmer = spawnLeveledFarmer(helper, 3);
        farmer.getInventory().addItem(new ItemStack(Items.WHEAT_SEEDS, 16));
        farmer.getInventory().addItem(new ItemStack(Items.PUMPKIN_SEEDS, 1));

        helper.runAfterDelay(500, () -> {
            int stems = 0;
            BlockPos stem = null;
            for (int x = 12; x <= 14; x++) {
                if (helper.getBlockState(new BlockPos(x, 1, 12)).is(Blocks.PUMPKIN_STEM)) {
                    stems++;
                    stem = new BlockPos(x, 1, 12);
                }
            }
            helper.assertTrue(stems == 1, "The farmer plants its one pumpkin seed, found " + stems + " stems");
            helper.assertTrue(farmer.getInventory().countItem(Items.PUMPKIN_SEEDS) == 0, "The seed is used up");
            boolean free = false;
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos beside = stem.relative(side);
                if (helper.getBlockState(beside).isAir() && Field.isFarmland(helper.getBlockState(beside.below()))) free = true;
            }
            helper.assertTrue(free, "A stem must keep a free farmland cell beside it for its fruit");
            helper.succeed();
        });
    }

    public static void aNoviceFarmerDoesNotPlantPumpkinSeeds(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(COMPOSTER, Blocks.COMPOSTER);
        helper.setBlock(13, 0, 10, Blocks.WATER);
        for (int x = 12; x <= 14; x++) helper.setBlock(x, 0, 12, Blocks.FARMLAND);
        Villager farmer = spawnLeveledFarmer(helper, 2);
        farmer.getInventory().addItem(new ItemStack(Items.PUMPKIN_SEEDS, 1));

        helper.runAfterDelay(400, () -> {
            for (int x = 12; x <= 14; x++) helper.assertTrue(!helper.getBlockState(new BlockPos(x, 1, 12)).is(Blocks.PUMPKIN_STEM), "A novice plants no stems");
            helper.assertTrue(farmer.getInventory().countItem(Items.PUMPKIN_SEEDS) == 1, "It keeps the seed");
            helper.succeed();
        });
    }

    public static void stemsAndFruitAreRecognized(GameTestHelper helper) {
        helper.assertTrue(Crops.isStemSeed(new ItemStack(Items.PUMPKIN_SEEDS)) && Crops.isStemSeed(new ItemStack(Items.MELON_SEEDS)), "pumpkin and melon seeds plant stems");
        helper.assertTrue(!Crops.isStemSeed(new ItemStack(Items.WHEAT_SEEDS)), "wheat seeds don't");
        helper.assertTrue(Crops.isFruit(Blocks.PUMPKIN.defaultBlockState()) && Crops.isFruit(Blocks.MELON.defaultBlockState()), "pumpkins and melons are fruit");
        helper.assertTrue(!Crops.isFruit(Blocks.CARVED_PUMPKIN.defaultBlockState()), "a carved pumpkin is the player's");
        helper.assertTrue(Crops.fruitLevel(Blocks.PUMPKIN) == 3 && Crops.fruitLevel(Blocks.MELON) == 4, "pumpkins from journeyman, melons from expert");
        helper.succeed();
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
