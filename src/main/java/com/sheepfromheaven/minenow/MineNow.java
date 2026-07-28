///
package com.sheepfromheaven.minenow;

import com.sheepfromheaven.minenow.block.ModBlocks;
import com.sheepfromheaven.minenow.item.ModCreativeTabs;
import com.sheepfromheaven.minenow.item.ModItems;
import com.sheepfromheaven.minenow.network.ModNetwork;
import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(MineNow.MOD_ID)
public final class MineNow {
    public static final String MOD_ID = "minenow";
    private static final Logger LOGGER = LogUtils.getLogger();

    public MineNow(FMLJavaModLoadingContext context) {
        var modBusGroup = context.getModBusGroup();

        ModBlocks.register(modBusGroup);
        ModItems.register(modBusGroup);
        ModCreativeTabs.register(modBusGroup);
        com.sheepfromheaven.minenow.entity.ModEntities.register(modBusGroup);
        com.sheepfromheaven.minenow.inventory.ModMenus.register(modBusGroup);

        FMLCommonSetupEvent.getBus(modBusGroup).addListener(this::commonSetup);

        net.minecraftforge.event.RegisterCommandsEvent.BUS.addListener(event ->
                com.sheepfromheaven.minenow.command.DebugCommands.register(event.getDispatcher()));

        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        net.minecraftforge.event.entity.EntityAttributeCreationEvent.BUS.addListener(event ->
                event.put(com.sheepfromheaven.minenow.entity.ModEntities.INHABITANT.get(),
                        com.sheepfromheaven.minenow.entity.InhabitantEntity.createAttributes().build()));

        net.minecraftforge.registries.RegisterEvent.getBus(modBusGroup).addListener(event ->
                event.register(net.minecraft.core.registries.Registries.TEST_FUNCTION, helper -> {
                    helper.register("inhabitant_sleeps_in_assigned_bed_at_night",
                            com.sheepfromheaven.minenow.gametest.GoHomeGoalTests::inhabitantSleepsInAssignedBedAtNight);
                    helper.register("inhabitant_walks_home_when_requested",
                            com.sheepfromheaven.minenow.gametest.GoHomeGoalTests::inhabitantWalksHomeWhenRequested);
                    helper.register("rest_need_decays_while_inhabitant_is_awake",
                            com.sheepfromheaven.minenow.gametest.RestNeedTests::restNeedDecaysWhileInhabitantIsAwake);
                    helper.register("rest_need_restores_while_inhabitant_is_sleeping",
                            com.sheepfromheaven.minenow.gametest.RestNeedTests::restNeedRestoresWhileInhabitantIsSleeping);
                    helper.register("inhabitant_walks_around_house_to_bed",
                            com.sheepfromheaven.minenow.gametest.GoHomeGoalTests::inhabitantWalksAroundHouseToBed);
                    helper.register("inhabitant_opens_door_to_reach_bed",
                            com.sheepfromheaven.minenow.gametest.GoHomeGoalTests::inhabitantOpensDoorToReachBed);
                    helper.register("inhabitant_opens_gate_to_reach_bed",
                            com.sheepfromheaven.minenow.gametest.GoHomeGoalTests::inhabitantOpensGateToReachBed);
                    helper.register("disbanding_dominion_converts_inhabitants_and_clears_data",
                            com.sheepfromheaven.minenow.gametest.DominionTests::disbandingDominionConvertsInhabitantsAndClearsData);
                    helper.register("destroying_structure_removes_inhabitant_home_and_job",
                            com.sheepfromheaven.minenow.gametest.DominionTests::destroyingStructureRemovesInhabitantHomeAndJob);
                    helper.register("assigning_new_home_unassigns_inhabitant_from_previous_home",
                            com.sheepfromheaven.minenow.gametest.DominionTests::assigningNewHomeUnassignsInhabitantFromPreviousHome);
                    helper.register("assigning_new_job_unassigns_inhabitant_from_previous_job",
                            com.sheepfromheaven.minenow.gametest.DominionTests::assigningNewJobUnassignsInhabitantFromPreviousJob);
                    helper.register("assigning_house_reserves_bed_from_vanilla_villagers",
                            com.sheepfromheaven.minenow.gametest.DominionTests::assigningHouseReservesBedFromVanillaVillagers);
                    helper.register("assigning_inhabitant_to_house_without_bed_is_refused",
                            com.sheepfromheaven.minenow.gametest.DominionTests::assigningInhabitantToHouseWithoutBedIsRefused);
                    helper.register("inhabitant_complains_when_assigned_bed_is_broken",
                            com.sheepfromheaven.minenow.gametest.DominionTests::inhabitantComplainsWhenAssignedBedIsBroken);
                    helper.register("storage_containers_skips_workstations",
                            com.sheepfromheaven.minenow.gametest.FisheryStorageTests::storageContainersSkipsWorkstations);
                    helper.register("storage_containers_finds_extra_containers_not_just_required_barrel",
                            com.sheepfromheaven.minenow.gametest.FisheryStorageTests::storageContainersFindsExtraContainersNotJustRequiredBarrel);
                    helper.register("log_on_soil_with_natural_leaf_is_recognized_as_tree",
                            com.sheepfromheaven.minenow.gametest.TreeScannerTests::logOnSoilWithNaturalLeafIsRecognizedAsTree);
                    helper.register("log_pillar_without_leaves_is_not_a_tree",
                            com.sheepfromheaven.minenow.gametest.TreeScannerTests::logPillarWithoutLeavesIsNotATree);
                    helper.register("log_with_only_persistent_leaf_is_not_a_tree",
                            com.sheepfromheaven.minenow.gametest.TreeScannerTests::logWithOnlyPersistentLeafIsNotATree);
                    helper.register("log_not_on_soil_is_not_a_tree",
                            com.sheepfromheaven.minenow.gametest.TreeScannerTests::logNotOnSoilIsNotATree);
                    helper.register("log_adjacent_to_planks_aborts_even_with_leaves_higher_up",
                            com.sheepfromheaven.minenow.gametest.TreeScannerTests::logAdjacentToPlanksAbortsEvenWithLeavesHigherUp);
                    helper.register("beehive_neighbor_allows_climbing_to_leaves_above",
                            com.sheepfromheaven.minenow.gametest.TreeScannerTests::beehiveNeighborAllowsClimbingToLeavesAbove);
                    helper.register("same_axis_log_neighbor_allows_climbing_to_leaves_above",
                            com.sheepfromheaven.minenow.gametest.TreeScannerTests::sameAxisLogNeighborAllowsClimbingToLeavesAbove);
                    helper.register("different_axis_log_neighbor_aborts_tree",
                            com.sheepfromheaven.minenow.gametest.TreeScannerTests::differentAxisLogNeighborAbortsTree);
                    helper.register("non_log_pillar_two_blocks_away_does_not_abort_tree",
                            com.sheepfromheaven.minenow.gametest.TreeScannerTests::nonLogPillarTwoBlocksAwayDoesNotAbortTree);
                    helper.register("grass_and_flowers_adjacent_to_trunk_do_not_abort_tree",
                            com.sheepfromheaven.minenow.gametest.TreeScannerTests::grassAndFlowersAdjacentToTrunkDoNotAbortTree);
                    helper.register("find_tree_in_range_finds_valid_tree",
                            com.sheepfromheaven.minenow.gametest.LumberyardTests::findTreeInRangeFindsValidTree);
                    helper.register("find_tree_in_range_skips_log_pillar_without_leaves",
                            com.sheepfromheaven.minenow.gametest.LumberyardTests::findTreeInRangeSkipsLogPillarWithoutLeaves);
                    helper.register("find_tree_in_range_ignores_nearby_log_house_and_still_finds_real_tree",
                            com.sheepfromheaven.minenow.gametest.LumberyardTests::findTreeInRangeIgnoresNearbyLogHouseAndStillFindsRealTree);
                    helper.register("default_work_area_is_centered_on_marker_with_default_radius",
                            com.sheepfromheaven.minenow.gametest.WorkAreaTests::defaultWorkAreaIsCenteredOnMarkerWithDefaultRadius);
                    helper.register("setting_work_area_center_and_radius_is_respected",
                            com.sheepfromheaven.minenow.gametest.WorkAreaTests::settingWorkAreaCenterAndRadiusIsRespected);
                    helper.register("work_area_radius_is_clamped_to_valid_range",
                            com.sheepfromheaven.minenow.gametest.WorkAreaTests::workAreaRadiusIsClampedToValidRange);
                    helper.register("find_tree_in_range_ignores_tree_outside_moved_work_area",
                            com.sheepfromheaven.minenow.gametest.WorkAreaTests::findTreeInRangeIgnoresTreeOutsideMovedWorkArea);
                    helper.register("find_tree_in_range_finds_tree_after_moving_work_area_center",
                            com.sheepfromheaven.minenow.gametest.WorkAreaTests::findTreeInRangeFindsTreeAfterMovingWorkAreaCenter);
                    helper.register("find_smoking_recipe_returns_cooked_result_for_raw_beef",
                            com.sheepfromheaven.minenow.gametest.CookingRecipesTests::findSmokingRecipeReturnsCookedResultForRawBeef);
                    helper.register("find_smoking_recipe_is_empty_for_cooked_beef",
                            com.sheepfromheaven.minenow.gametest.CookingRecipesTests::findSmokingRecipeIsEmptyForCookedBeef);
                    helper.register("is_fuel_true_for_log",
                            com.sheepfromheaven.minenow.gametest.CookingRecipesTests::isFuelTrueForLog);
                    helper.register("is_fuel_false_for_stone",
                            com.sheepfromheaven.minenow.gametest.CookingRecipesTests::isFuelFalseForStone);
                    helper.register("is_ready_to_eat_true_for_cooked_beef",
                            com.sheepfromheaven.minenow.gametest.CookingRecipesTests::isReadyToEatTrueForCookedBeef);
                    helper.register("is_ready_to_eat_false_for_raw_beef",
                            com.sheepfromheaven.minenow.gametest.CookingRecipesTests::isReadyToEatFalseForRawBeef);
                    helper.register("is_ready_to_eat_false_for_non_food_item",
                            com.sheepfromheaven.minenow.gametest.CookingRecipesTests::isReadyToEatFalseForNonFoodItem);
                    helper.register("is_ready_to_eat_true_for_bread_without_any_smoking_recipe",
                            com.sheepfromheaven.minenow.gametest.CookingRecipesTests::isReadyToEatTrueForBreadWithoutAnySmokingRecipe);
                    helper.register("extract_one_removes_exactly_one_item",
                            com.sheepfromheaven.minenow.gametest.ContainerUtilTests::extractOneRemovesExactlyOneItem);
                    helper.register("extract_one_returns_empty_when_no_match",
                            com.sheepfromheaven.minenow.gametest.ContainerUtilTests::extractOneReturnsEmptyWhenNoMatch);
                    helper.register("find_nearest_structure_of_type_picks_closest_match",
                            com.sheepfromheaven.minenow.gametest.DominionTests::findNearestStructureOfTypePicksClosestMatch);
                    helper.register("find_nearest_structure_of_type_skips_closest_when_filter_fails",
                            com.sheepfromheaven.minenow.gametest.DominionTests::findNearestStructureOfTypeSkipsClosestWhenFilterFails);
                    helper.register("inhabitant_stores_tool_in_storage_at_night",
                            com.sheepfromheaven.minenow.gametest.FinishWorkGoalTests::inhabitantStoresToolInStorageAtNight);
                    helper.register("interrupted_lumberjack_returns_to_hut_instead_of_resuming_chopping",
                            com.sheepfromheaven.minenow.gametest.JobInterruptionTests::interruptedLumberjackReturnsToHutInsteadOfResumingChopping);
                    helper.register("unassigning_lumberjack_mid_chop_drops_stale_job",
                            com.sheepfromheaven.minenow.gametest.JobInterruptionTests::unassigningLumberjackMidChopDropsStaleJob);
                    helper.register("approach_pos_is_marker_when_marker_is_on_the_floor",
                            com.sheepfromheaven.minenow.gametest.WorkplaceApproachTests::approachPosIsMarkerWhenMarkerIsOnTheFloor);
                    helper.register("approach_pos_finds_floor_when_marker_is_embedded_high_in_a_shaft",
                            com.sheepfromheaven.minenow.gametest.WorkplaceApproachTests::approachPosFindsFloorWhenMarkerIsEmbeddedHighInAShaft);
                    helper.register("cook_loads_raw_food_and_fuel_into_the_smoker",
                            com.sheepfromheaven.minenow.gametest.CookJobTests::cookLoadsRawFoodAndFuelIntoTheSmoker);
                    helper.register("missing_raw_food_reports_generic_label_instead_of_a_specific_item",
                            com.sheepfromheaven.minenow.gametest.CookJobTests::missingRawFoodReportsGenericLabelInsteadOfASpecificItem);
                }));

        if (FMLEnvironment.dist == Dist.CLIENT) {
            registerRenderers();
            com.sheepfromheaven.minenow.client.ConfigScreen.registerExtensionPoint(context);
            com.sheepfromheaven.minenow.client.WorldPickMode.register();
        }
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        ModNetwork.register();
        com.sheepfromheaven.minenow.dominion.StructureRequirementsConfig.load(
                net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get());
    }

    @Mod.EventBusSubscriber(modid = MOD_ID, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> net.minecraft.client.gui.screens.MenuScreens.register(
                    com.sheepfromheaven.minenow.inventory.ModMenus.INHABITANT_INVENTORY_TYPE.get(),
                    com.sheepfromheaven.minenow.client.VanillaRowScreen::new));
        }
    }

    public static void registerRenderers() {
        net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers.BUS.addListener(event -> {
            event.registerBlockEntityRenderer(ModBlocks.TOWN_BANNER_BE.get(),
                    com.sheepfromheaven.minenow.client.TownBannerRenderer::new);
            event.registerBlockEntityRenderer(ModBlocks.STRUCTURE_MARKER_BE.get(),
                    com.sheepfromheaven.minenow.client.StructureMarkerRenderer::new);
            event.registerEntityRenderer(com.sheepfromheaven.minenow.entity.ModEntities.INHABITANT.get(),
                    com.sheepfromheaven.minenow.client.InhabitantRenderer::new);
        });
    }
}
