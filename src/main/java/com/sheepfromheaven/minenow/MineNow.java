///
package com.sheepfromheaven.minenow;

import com.sheepfromheaven.minenow.block.ModBlocks;
import com.sheepfromheaven.minenow.item.ModCreativeTabs;
import com.sheepfromheaven.minenow.item.ModItems;
import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(MineNow.MOD_ID)
public final class MineNow {
    public static final String MOD_ID = "minenow";
    private static final Logger LOGGER = LogUtils.getLogger();

    public MineNow(IEventBus modBus, ModContainer modContainer) {
        ModBlocks.register(modBus);
        ModItems.register(modBus);
        ModCreativeTabs.register(modBus);
        com.sheepfromheaven.minenow.entity.ModEntities.register(modBus);
        com.sheepfromheaven.minenow.inventory.ModMenus.register(modBus);

        modBus.addListener(this::commonSetup);

        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.RegisterCommandsEvent.class, event ->
                com.sheepfromheaven.minenow.command.DebugCommands.register(event.getDispatcher()));

        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.tick.ServerTickEvent.Post.class, event -> {
            com.sheepfromheaven.minenow.entity.job.TreeScannerDebugger.serverTick(event.getServer());
            var server = event.getServer();
            if (server.getTickCount() % 200 == 0) {
                var overworld = server.overworld();
                com.sheepfromheaven.minenow.dominion.DominionSavedData.get(overworld).tickStructureStock(overworld);
            }
        });

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        modBus.addListener(net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent.class, event ->
                event.put(com.sheepfromheaven.minenow.entity.ModEntities.INHABITANT.get(),
                        com.sheepfromheaven.minenow.entity.InhabitantEntity.createAttributes().build()));

        modBus.addListener(net.neoforged.neoforge.registries.RegisterEvent.class, event ->
                event.register(net.minecraft.core.registries.Registries.TEST_FUNCTION, helper -> {
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "inhabitant_sleeps_in_assigned_bed_at_night"),
                            com.sheepfromheaven.minenow.gametest.GoHomeGoalTests::inhabitantSleepsInAssignedBedAtNight);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "inhabitant_walks_home_when_requested"),
                            com.sheepfromheaven.minenow.gametest.GoHomeGoalTests::inhabitantWalksHomeWhenRequested);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "rest_need_decays_while_inhabitant_is_awake"),
                            com.sheepfromheaven.minenow.gametest.RestNeedTests::restNeedDecaysWhileInhabitantIsAwake);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "rest_need_restores_while_inhabitant_is_sleeping"),
                            com.sheepfromheaven.minenow.gametest.RestNeedTests::restNeedRestoresWhileInhabitantIsSleeping);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "inhabitant_walks_around_house_to_bed"),
                            com.sheepfromheaven.minenow.gametest.GoHomeGoalTests::inhabitantWalksAroundHouseToBed);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "inhabitant_opens_door_to_reach_bed"),
                            com.sheepfromheaven.minenow.gametest.GoHomeGoalTests::inhabitantOpensDoorToReachBed);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "inhabitant_opens_gate_to_reach_bed"),
                            com.sheepfromheaven.minenow.gametest.GoHomeGoalTests::inhabitantOpensGateToReachBed);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "disbanding_dominion_converts_inhabitants_and_clears_data"),
                            com.sheepfromheaven.minenow.gametest.DominionTests::disbandingDominionConvertsInhabitantsAndClearsData);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "destroying_structure_removes_inhabitant_home_and_job"),
                            com.sheepfromheaven.minenow.gametest.DominionTests::destroyingStructureRemovesInhabitantHomeAndJob);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "assigning_new_home_unassigns_inhabitant_from_previous_home"),
                            com.sheepfromheaven.minenow.gametest.DominionTests::assigningNewHomeUnassignsInhabitantFromPreviousHome);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "assigning_new_job_unassigns_inhabitant_from_previous_job"),
                            com.sheepfromheaven.minenow.gametest.DominionTests::assigningNewJobUnassignsInhabitantFromPreviousJob);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "assigning_house_reserves_bed_from_vanilla_villagers"),
                            com.sheepfromheaven.minenow.gametest.DominionTests::assigningHouseReservesBedFromVanillaVillagers);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "assigning_inhabitant_to_house_without_bed_is_refused"),
                            com.sheepfromheaven.minenow.gametest.DominionTests::assigningInhabitantToHouseWithoutBedIsRefused);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "inhabitant_complains_when_assigned_bed_is_broken"),
                            com.sheepfromheaven.minenow.gametest.DominionTests::inhabitantComplainsWhenAssignedBedIsBroken);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "storage_containers_skips_workstations"),
                            com.sheepfromheaven.minenow.gametest.FisheryStorageTests::storageContainersSkipsWorkstations);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "storage_containers_finds_extra_containers_not_just_required_barrel"),
                            com.sheepfromheaven.minenow.gametest.FisheryStorageTests::storageContainersFindsExtraContainersNotJustRequiredBarrel);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "storehouse_with_filter_rejects_disallowed_item"),
                            com.sheepfromheaven.minenow.gametest.CourierJobTests::storehouseWithFilterRejectsDisallowedItem);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "storehouse_without_filter_accepts_any_item"),
                            com.sheepfromheaven.minenow.gametest.CourierJobTests::storehouseWithoutFilterAcceptsAnyItem);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "courier_prefers_nearest_storehouse_over_farther_one"),
                            com.sheepfromheaven.minenow.gametest.CourierJobTests::courierPrefersNearestStorehouseOverFartherOne);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "courier_skips_storehouse_that_filters_out_the_item"),
                            com.sheepfromheaven.minenow.gametest.CourierJobTests::courierSkipsStorehouseThatFiltersOutTheItem);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "log_on_soil_with_natural_leaf_is_recognized_as_tree"),
                            com.sheepfromheaven.minenow.gametest.TreeScannerTests::logOnSoilWithNaturalLeafIsRecognizedAsTree);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "log_pillar_without_leaves_is_not_a_tree"),
                            com.sheepfromheaven.minenow.gametest.TreeScannerTests::logPillarWithoutLeavesIsNotATree);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "log_with_only_persistent_leaf_is_not_a_tree"),
                            com.sheepfromheaven.minenow.gametest.TreeScannerTests::logWithOnlyPersistentLeafIsNotATree);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "log_not_on_soil_is_not_a_tree"),
                            com.sheepfromheaven.minenow.gametest.TreeScannerTests::logNotOnSoilIsNotATree);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "log_adjacent_to_planks_aborts_even_with_leaves_higher_up"),
                            com.sheepfromheaven.minenow.gametest.TreeScannerTests::logAdjacentToPlanksAbortsEvenWithLeavesHigherUp);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "beehive_neighbor_allows_climbing_to_leaves_above"),
                            com.sheepfromheaven.minenow.gametest.TreeScannerTests::beehiveNeighborAllowsClimbingToLeavesAbove);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "same_axis_log_neighbor_allows_climbing_to_leaves_above"),
                            com.sheepfromheaven.minenow.gametest.TreeScannerTests::sameAxisLogNeighborAllowsClimbingToLeavesAbove);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "different_axis_log_of_same_species_is_included_in_trunk"),
                            com.sheepfromheaven.minenow.gametest.TreeScannerTests::differentAxisLogOfSameSpeciesIsIncludedInTrunk);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "diagonally_offset_trunk_is_still_recognized_as_tree"),
                            com.sheepfromheaven.minenow.gametest.TreeScannerTests::diagonallyOffsetTrunkIsStillRecognizedAsTree);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "find_tree_shape_flood_fills_the_whole_canopy_from_every_trunk_touch_point"),
                            com.sheepfromheaven.minenow.gametest.TreeScannerTests::findTreeShapeFloodFillsTheWholeCanopyFromEveryTrunkTouchPoint);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "trunk_forking_into_two_diagonal_branches_recognizes_both_branches"),
                            com.sheepfromheaven.minenow.gametest.TreeScannerTests::trunkForkingIntoTwoDiagonalBranchesRecognizesBothBranches);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "vines_adjacent_to_trunk_do_not_abort_tree"),
                            com.sheepfromheaven.minenow.gametest.TreeScannerTests::vinesAdjacentToTrunkDoNotAbortTree);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "non_log_pillar_two_blocks_away_does_not_abort_tree"),
                            com.sheepfromheaven.minenow.gametest.TreeScannerTests::nonLogPillarTwoBlocksAwayDoesNotAbortTree);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "grass_and_flowers_adjacent_to_trunk_do_not_abort_tree"),
                            com.sheepfromheaven.minenow.gametest.TreeScannerTests::grassAndFlowersAdjacentToTrunkDoNotAbortTree);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "find_tree_in_range_finds_valid_tree"),
                            com.sheepfromheaven.minenow.gametest.LumberyardTests::findTreeInRangeFindsValidTree);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "find_tree_in_range_skips_log_pillar_without_leaves"),
                            com.sheepfromheaven.minenow.gametest.LumberyardTests::findTreeInRangeSkipsLogPillarWithoutLeaves);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "find_tree_in_range_ignores_nearby_log_house_and_still_finds_real_tree"),
                            com.sheepfromheaven.minenow.gametest.LumberyardTests::findTreeInRangeIgnoresNearbyLogHouseAndStillFindsRealTree);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "felling_trunk_harvests_canopy_leaves_directly"),
                            com.sheepfromheaven.minenow.gametest.LumberjackLeafHarvestTests::fellingTrunkHarvestsCanopyLeavesDirectly);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "find_shore_position_uses_rim_of_sunken_well_instead_of_the_water_itself"),
                            com.sheepfromheaven.minenow.gametest.FisherJobShoreTests::findShorePositionUsesRimOfSunkenWellInsteadOfTheWaterItself);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "find_shore_position_prefers_same_level_ring_over_rim"),
                            com.sheepfromheaven.minenow.gametest.FisherJobShoreTests::findShorePositionPrefersSameLevelRingOverRim);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "lumberjack_clears_decayable_leaves_blocking_trunk_access"),
                            com.sheepfromheaven.minenow.gametest.LumberjackLeafHarvestTests::lumberjackClearsDecayableLeavesBlockingTrunkAccess);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "default_work_area_is_centered_on_marker_with_default_radius"),
                            com.sheepfromheaven.minenow.gametest.WorkAreaTests::defaultWorkAreaIsCenteredOnMarkerWithDefaultRadius);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "setting_work_area_center_and_radius_is_respected"),
                            com.sheepfromheaven.minenow.gametest.WorkAreaTests::settingWorkAreaCenterAndRadiusIsRespected);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "work_area_radius_is_clamped_to_valid_range"),
                            com.sheepfromheaven.minenow.gametest.WorkAreaTests::workAreaRadiusIsClampedToValidRange);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "find_tree_in_range_ignores_tree_outside_moved_work_area"),
                            com.sheepfromheaven.minenow.gametest.WorkAreaTests::findTreeInRangeIgnoresTreeOutsideMovedWorkArea);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "find_tree_in_range_finds_tree_after_moving_work_area_center"),
                            com.sheepfromheaven.minenow.gametest.WorkAreaTests::findTreeInRangeFindsTreeAfterMovingWorkAreaCenter);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "find_smoking_recipe_returns_cooked_result_for_raw_beef"),
                            com.sheepfromheaven.minenow.gametest.CookingRecipesTests::findSmokingRecipeReturnsCookedResultForRawBeef);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "find_smoking_recipe_is_empty_for_cooked_beef"),
                            com.sheepfromheaven.minenow.gametest.CookingRecipesTests::findSmokingRecipeIsEmptyForCookedBeef);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "is_fuel_true_for_log"),
                            com.sheepfromheaven.minenow.gametest.CookingRecipesTests::isFuelTrueForLog);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "is_fuel_false_for_stone"),
                            com.sheepfromheaven.minenow.gametest.CookingRecipesTests::isFuelFalseForStone);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "is_ready_to_eat_true_for_cooked_beef"),
                            com.sheepfromheaven.minenow.gametest.CookingRecipesTests::isReadyToEatTrueForCookedBeef);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "is_ready_to_eat_false_for_raw_beef"),
                            com.sheepfromheaven.minenow.gametest.CookingRecipesTests::isReadyToEatFalseForRawBeef);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "is_ready_to_eat_false_for_non_food_item"),
                            com.sheepfromheaven.minenow.gametest.CookingRecipesTests::isReadyToEatFalseForNonFoodItem);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "is_ready_to_eat_true_for_bread_without_any_smoking_recipe"),
                            com.sheepfromheaven.minenow.gametest.CookingRecipesTests::isReadyToEatTrueForBreadWithoutAnySmokingRecipe);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "extract_one_removes_exactly_one_item"),
                            com.sheepfromheaven.minenow.gametest.ContainerUtilTests::extractOneRemovesExactlyOneItem);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "extract_one_returns_empty_when_no_match"),
                            com.sheepfromheaven.minenow.gametest.ContainerUtilTests::extractOneReturnsEmptyWhenNoMatch);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "find_nearest_structure_of_type_picks_closest_match"),
                            com.sheepfromheaven.minenow.gametest.DominionTests::findNearestStructureOfTypePicksClosestMatch);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "find_nearest_structure_of_type_skips_closest_when_filter_fails"),
                            com.sheepfromheaven.minenow.gametest.DominionTests::findNearestStructureOfTypeSkipsClosestWhenFilterFails);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "inhabitant_stores_tool_in_storage_at_night"),
                            com.sheepfromheaven.minenow.gametest.FinishWorkGoalTests::inhabitantStoresToolInStorageAtNight);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "interrupted_lumberjack_returns_to_hut_instead_of_resuming_chopping"),
                            com.sheepfromheaven.minenow.gametest.JobInterruptionTests::interruptedLumberjackReturnsToHutInsteadOfResumingChopping);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "unassigning_lumberjack_mid_chop_drops_stale_job"),
                            com.sheepfromheaven.minenow.gametest.JobInterruptionTests::unassigningLumberjackMidChopDropsStaleJob);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "approach_pos_is_marker_when_marker_is_on_the_floor"),
                            com.sheepfromheaven.minenow.gametest.WorkplaceApproachTests::approachPosIsMarkerWhenMarkerIsOnTheFloor);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "approach_pos_finds_floor_when_marker_is_embedded_high_in_a_shaft"),
                            com.sheepfromheaven.minenow.gametest.WorkplaceApproachTests::approachPosFindsFloorWhenMarkerIsEmbeddedHighInAShaft);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "cook_loads_raw_food_and_fuel_into_the_smoker"),
                            com.sheepfromheaven.minenow.gametest.CookJobTests::cookLoadsRawFoodAndFuelIntoTheSmoker);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "missing_raw_food_reports_generic_label_instead_of_a_specific_item"),
                            com.sheepfromheaven.minenow.gametest.CookJobTests::missingRawFoodReportsGenericLabelInsteadOfASpecificItem);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "cooking_with_burned_out_fuel_goes_back_to_fetch_more_fuel"),
                            com.sheepfromheaven.minenow.gametest.CookJobTests::cookingWithBurnedOutFuelGoesBackToFetchMoreFuel);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "debug_lumberyard_barrel_is_stocked_with_an_axe"),
                            com.sheepfromheaven.minenow.gametest.DebugCommandsTests::debugLumberyardBarrelIsStockedWithAnAxe);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "debug_fishery_barrel_is_stocked_with_a_fishing_rod"),
                            com.sheepfromheaven.minenow.gametest.DebugCommandsTests::debugFisheryBarrelIsStockedWithAFishingRod);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "debug_tavern_barrel_is_stocked_with_food_and_fuel"),
                            com.sheepfromheaven.minenow.gametest.DebugCommandsTests::debugTavernBarrelIsStockedWithFoodAndFuel);
                }));

        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            registerRenderers(modBus);
            com.sheepfromheaven.minenow.client.ConfigScreen.registerExtensionPoint(modContainer);
            com.sheepfromheaven.minenow.client.WorldPickMode.register(modBus);
            NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.ClientTickEvent.Post.class, event ->
                    com.sheepfromheaven.minenow.client.TreeScanGizmoRenderer.onClientTick());
        }
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        com.sheepfromheaven.minenow.dominion.StructureRequirementsConfig.load(
                net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get());
    }

    @EventBusSubscriber(modid = MOD_ID, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onRegisterMenuScreens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {
            event.register(
                    com.sheepfromheaven.minenow.inventory.ModMenus.INHABITANT_INVENTORY_TYPE.get(),
                    com.sheepfromheaven.minenow.client.VanillaRowScreen::new);
        }
    }

    public static void registerRenderers(IEventBus modBus) {
        modBus.addListener(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers.class, event -> {
            event.registerBlockEntityRenderer(ModBlocks.TOWN_BANNER_BE.get(),
                    com.sheepfromheaven.minenow.client.TownBannerRenderer::new);
            event.registerBlockEntityRenderer(ModBlocks.STRUCTURE_MARKER_BE.get(),
                    com.sheepfromheaven.minenow.client.StructureMarkerRenderer::new);
            event.registerEntityRenderer(com.sheepfromheaven.minenow.entity.ModEntities.INHABITANT.get(),
                    com.sheepfromheaven.minenow.client.InhabitantRenderer::new);
            event.registerEntityRenderer(com.sheepfromheaven.minenow.entity.ModEntities.NPC_FISHING_BOBBER.get(),
                    com.sheepfromheaven.minenow.client.NpcFishingBobberRenderer::new);
        });
    }
}
