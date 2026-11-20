package com.sheepfromheaven.minenow.farmer;

import com.sheepfromheaven.minenow.MineNowConfig;
import com.sheepfromheaven.minenow.MineNowConfig.Profession;
import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import com.sheepfromheaven.minenow.villager.ProfessionTrades;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.List;

/**
 * A farmer switched off farms as vanilla's, and switched on as Hearthfolk's. Vanilla's work sits inside a RunOne, which
 * the brain doesn't list, so these tests show it by its effect: a crop harvested, bread baked, a crop grown, while
 * TendFields, which the brain does list, is not running.
 */
public final class SwitchedFarmerTests {
    private static final BlockPos COMPOSTER = new BlockPos(11, 1, 12);
    private static final BlockPos CROP = new BlockPos(12, 1, 12);
    private static final BlockPos LATER_CROP = new BlockPos(14, 1, 12);

    private SwitchedFarmerTests() {}

    /** Switched off, a ripe crop is harvested while TendFields stays out. */
    public static void aSwitchedOffFarmerHarvestsTheVanillaWay(GameTestHelper helper) {
        helper.assertTrue(!MineNowConfig.professionEnabled(Profession.FARMER), "The farmer's environment switches the farmer off");
        setUpCrop(helper);
        Villager farmer = spawnFarmer(helper);
        boolean[] tended = {false};
        helper.onEachTick(() -> tended[0] |= farmer.getBrain().getRunningBehaviors().stream().anyMatch(TendFields.class::isInstance));
        helper.succeedWhen(() -> {
            helper.assertTrue(!ripe(helper, CROP), "Expected the ripe wheat to be harvested");
            helper.assertTrue(!tended[0], "Expected TendFields to stay out while the farmer is switched off");
        });
    }

    /** Switched on, the same ripe crop is harvested by TendFields. */
    public static void aFarmerThatIsOnHarvestsWithTendFields(GameTestHelper helper) {
        helper.assertTrue(MineNowConfig.professionEnabled(Profession.FARMER), "A farmer in a plain test is switched on");
        setUpCrop(helper);
        Villager farmer = spawnFarmer(helper);
        boolean[] tended = {false};
        helper.onEachTick(() -> tended[0] |= farmer.getBrain().getRunningBehaviors().stream().anyMatch(TendFields.class::isInstance));
        helper.succeedWhen(() -> {
            helper.assertTrue(!ripe(helper, CROP), "Expected the ripe wheat to be harvested");
            helper.assertTrue(tended[0], "Expected TendFields to do it");
        });
    }

    /** Switched off, the farmer bakes bread from its wheat at the composter, as vanilla's farmer does. */
    public static void aSwitchedOffFarmerBakesBreadAtItsComposter(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(COMPOSTER, Blocks.COMPOSTER);
        Villager farmer = GameTestHelpers.spawnEmployed(helper, CROP, COMPOSTER, VillagerProfession.FARMER, PoiTypes.FARMER);
        farmer.getInventory().addItem(new ItemStack(Items.WHEAT, 9));
        helper.succeedWhen(() -> helper.assertTrue(farmer.getInventory().countItem(Items.BREAD) > 0, "Expected the farmer to bake bread from its wheat"));
    }

    /** Switched off, the farmer uses bone meal on a growing crop, as vanilla's farmer does. */
    public static void aSwitchedOffFarmerBoneMealsGrowingCrops(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(COMPOSTER, Blocks.COMPOSTER);
        helper.setBlock(CROP.below(), Blocks.FARMLAND);
        helper.setBlock(CROP, Blocks.WHEAT.defaultBlockState());
        Villager farmer = spawnFarmer(helper);
        farmer.getInventory().addItem(new ItemStack(Items.BONE_MEAL, 8));
        boolean[] tended = {false};
        helper.onEachTick(() -> tended[0] |= farmer.getBrain().getRunningBehaviors().stream().anyMatch(TendFields.class::isInstance));
        helper.succeedWhen(() -> {
            BlockState state = helper.getBlockState(CROP);
            helper.assertTrue(state.getBlock() instanceof CropBlock crop && crop.getAge(state) > 0, "Expected bone meal to grow the crop");
            helper.assertTrue(!tended[0], "Expected TendFields to stay out while the farmer is switched off");
        });
    }

    /** Switched off, nothing stops a farmer trampling the farmland it works on, as in vanilla. */
    public static void aSwitchedOffFarmerMayTrampleFarmland(GameTestHelper helper) {
        helper.assertTrue(!MineNowConfig.professionEnabled(Profession.FARMER), "The farmer's environment switches the farmer off");
        Villager farmer = setUpTramplingFarmer(helper);
        BlockEvent.FarmlandTrampleEvent event = new BlockEvent.FarmlandTrampleEvent(helper.getLevel(), helper.absolutePos(CROP.below()),
                Blocks.FARMLAND.defaultBlockState(), 3.0, farmer);
        FarmerRules.onFarmlandTrample(event);
        helper.assertTrue(!event.isCanceled(), "A farmer switched off may trample farmland, as in vanilla");
        helper.succeed();
    }

    /** Switched on, a farmer never tramples the farmland it works on. */
    public static void aFarmerThatIsOnDoesNotTrampleFarmland(GameTestHelper helper) {
        helper.assertTrue(MineNowConfig.professionEnabled(Profession.FARMER), "A farmer in a plain test is switched on");
        Villager farmer = setUpTramplingFarmer(helper);
        BlockEvent.FarmlandTrampleEvent event = new BlockEvent.FarmlandTrampleEvent(helper.getLevel(), helper.absolutePos(CROP.below()),
                Blocks.FARMLAND.defaultBlockState(), 3.0, farmer);
        FarmerRules.onFarmlandTrample(event);
        helper.assertTrue(event.isCanceled(), "A farmer that is on never tramples its farmland");
        helper.succeed();
    }

    /**
     * Switched back on in the middle of its work, the farmer stops vanilla's work and TendFields takes over the field. Vanilla
     * has harvested the first crop while the farmer was off; once it is on, wheat handed to it is no longer baked into bread.
     * The test's {@code profession_farmer_switching} environment keeps the farmer off for the batch, and switches it back on
     * when the batch ends, even if this test fails.
     */
    public static void aFarmerSwitchedBackOnStopsItsVanillaFarmWork(GameTestHelper helper) {
        helper.assertTrue(!MineNowConfig.professionEnabled(Profession.FARMER), "The farmer starts switched off");
        setUpCrop(helper);
        helper.setBlock(LATER_CROP.below(), Blocks.FARMLAND);
        helper.setBlock(LATER_CROP.below().west(), Blocks.FARMLAND);
        Villager farmer = spawnFarmer(helper);
        int[] phase = {0};
        int[] breadWhenOn = {0};
        boolean[] tendedWhileOff = {false};
        boolean[] tendedWhileOn = {false};
        boolean[] laterHarvested = {false};
        helper.onEachTick(() -> {
            if (phase[0] == 0) {
                tendedWhileOff[0] |= farmer.getBrain().getRunningBehaviors().stream().anyMatch(TendFields.class::isInstance);
                if (!ripe(helper, CROP)) {
                    breadWhenOn[0] = farmer.getInventory().countItem(Items.BREAD);
                    MineNowConfig.set(Profession.FARMER, true);
                    helper.setBlock(LATER_CROP.below(), Blocks.FARMLAND);
                    helper.setBlock(LATER_CROP.below().west(), Blocks.FARMLAND);
                    helper.setBlock(LATER_CROP, ripeWheat());
                    farmer.getInventory().addItem(new ItemStack(Items.WHEAT, 9));
                    phase[0] = 1;
                }
            } else if (phase[0] == 1) {
                tendedWhileOn[0] |= farmer.getBrain().getRunningBehaviors().stream().anyMatch(TendFields.class::isInstance);
                if (!ripe(helper, LATER_CROP)) laterHarvested[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(!tendedWhileOff[0], "Expected TendFields to stay out while the farmer is switched off");
            helper.assertTrue(laterHarvested[0], "Expected TendFields to harvest the crop that ripened after the switch");
            helper.assertTrue(tendedWhileOn[0], "Expected TendFields to do it");
            helper.assertTrue(farmer.getInventory().countItem(Items.BREAD) == breadWhenOn[0],
                    "Expected vanilla's composter work to stop once the farmer is switched on, bread went from " + breadWhenOn[0]
                            + " to " + farmer.getInventory().countItem(Items.BREAD));
        });
    }

    /**
     * Switched off, a journeyman farmer shows vanilla's trades, rolled for each of its three levels, and the same ones every
     * time trading is opened.
     */
    public static void aSwitchedOffFarmerShowsVanillaTradesForEveryLevel(GameTestHelper helper) {
        helper.assertTrue(!MineNowConfig.professionEnabled(Profession.FARMER), "The farmer's environment switches the farmer off");
        Villager farmer = journeyman(helper);
        List<String> first = offers(helper, farmer);
        helper.assertTrue(first.size() == 6, "Expected vanilla's two trades for each of its three levels, got " + first);
        helper.assertTrue(first.equals(offers(helper, farmer)), "Expected the same trades on opening trading again, got " + offers(helper, farmer));
        helper.succeed();
    }

    /**
     * Switched on, Hearthfolk's offers replace vanilla's; switched off again, the vanilla trades come back unchanged, so a
     * player can't reroll them by toggling. A level gained while off adds vanilla's trades for it, as vanilla does.
     */
    public static void aSwitchedOffFarmerKeepsItsVanillaTradesAcrossToggling(GameTestHelper helper) {
        Villager farmer = journeyman(helper);
        List<String> vanilla = offers(helper, farmer);
        try {
            MineNowConfig.set(Profession.FARMER, true);
            List<String> hearthfolk = offers(helper, farmer);
            helper.assertTrue(!hearthfolk.equals(vanilla), "Expected Hearthfolk's offers once switched on, got " + hearthfolk);
        } finally {
            MineNowConfig.set(Profession.FARMER, false);
        }
        helper.assertTrue(offers(helper, farmer).equals(vanilla), "Expected the same vanilla trades after switching on and off, got "
                + offers(helper, farmer) + " instead of " + vanilla);
        farmer.increaseMerchantCareer(helper.getLevel());
        List<String> levelled = offers(helper, farmer);
        helper.assertTrue(levelled.size() == vanilla.size() + 2 && levelled.subList(0, vanilla.size()).equals(vanilla),
                "Expected a new level to add vanilla's trades for it and keep the rest, got " + levelled);
        helper.succeed();
    }

    private static Villager journeyman(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(COMPOSTER, Blocks.COMPOSTER);
        Villager farmer = GameTestHelpers.spawnEmployed(helper, CROP, COMPOSTER, VillagerProfession.FARMER, PoiTypes.FARMER);
        farmer.setVillagerData(farmer.getVillagerData().withLevel(3));
        return farmer;
    }

    private static List<String> offers(GameTestHelper helper, Villager villager) {
        return ProfessionTrades.currentOffers(helper.getLevel(), villager).stream()
                .map(offer -> offer.getBaseCostA().getCount() + " " + offer.getBaseCostA().getItem() + offer.getItemCostB().map(b -> " + " + b.count() + " " + b.item().value()).orElse("")
                        + " -> " + offer.getResult().getCount() + " " + offer.getResult().getItem())
                .toList();
    }

    private static void setUpCrop(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(COMPOSTER, Blocks.COMPOSTER);
        helper.setBlock(CROP.below(), Blocks.FARMLAND);
        helper.setBlock(CROP, ripeWheat());
    }

    private static Villager setUpTramplingFarmer(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(COMPOSTER, Blocks.COMPOSTER);
        helper.setBlock(CROP.below(), Blocks.FARMLAND);
        return GameTestHelpers.spawnEmployed(helper, CROP, COMPOSTER, VillagerProfession.FARMER, PoiTypes.FARMER);
    }

    private static Villager spawnFarmer(GameTestHelper helper) {
        Villager farmer = GameTestHelpers.spawnEmployed(helper, CROP, COMPOSTER, VillagerProfession.FARMER, PoiTypes.FARMER);
        farmer.getInventory().addItem(new ItemStack(Items.IRON_HOE));
        return farmer;
    }

    private static BlockState ripeWheat() {
        return ((CropBlock) Blocks.WHEAT).getStateForAge(((CropBlock) Blocks.WHEAT).getMaxAge());
    }

    private static boolean ripe(GameTestHelper helper, BlockPos pos) {
        BlockState state = helper.getBlockState(pos);
        return state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state);
    }
}
