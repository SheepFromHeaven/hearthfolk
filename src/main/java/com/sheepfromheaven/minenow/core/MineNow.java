///
package com.sheepfromheaven.minenow.core;

import com.sheepfromheaven.minenow.core.ModCreativeTabs;
import com.sheepfromheaven.minenow.dominion.townbanner.TownBannerBlocks;
import com.sheepfromheaven.minenow.dominion.townbanner.TownBannerItems;
import com.sheepfromheaven.minenow.structure.StructureBlocks;
import com.sheepfromheaven.minenow.structure.StructureItems;
import com.sheepfromheaven.minenow.inhabitant.InhabitantItems;
import com.sheepfromheaven.minenow.pathfinding.PathfindingItems;
import com.sheepfromheaven.minenow.jobs.lumberjack.LumberjackItems;
import com.sheepfromheaven.minenow.storage.StorageItems;
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
        TownBannerBlocks.register(modBus);
        TownBannerItems.register(modBus);
        StructureBlocks.register(modBus);
        StructureItems.register(modBus);
        InhabitantItems.register(modBus);
        PathfindingItems.register(modBus);
        LumberjackItems.register(modBus);
        StorageItems.register(modBus);
        ModConditions.register(modBus);
        com.sheepfromheaven.minenow.core.ModDataComponents.register(modBus);
        ModCreativeTabs.register(modBus);
        com.sheepfromheaven.minenow.inhabitant.ModEntities.register(modBus);
        com.sheepfromheaven.minenow.inhabitant.InhabitantMenus.register(modBus);
        com.sheepfromheaven.minenow.storage.inventory.ModMenus.register(modBus);
        ModCriteriaTriggers.register(modBus);

        modBus.addListener(this::commonSetup);

        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.RegisterCommandsEvent.class, event ->
                com.sheepfromheaven.minenow.command.DebugCommands.register(event.getDispatcher()));

        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent.class,
                com.sheepfromheaven.minenow.dev.DevStarterKit::onPlayerLoggedIn);

        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent.class,
                com.sheepfromheaven.minenow.dev.GametestPlaygroundRunner::onPlayerLoggedIn);

        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.tick.ServerTickEvent.Post.class, event -> {
            com.sheepfromheaven.minenow.jobs.lumberjack.TreeScannerDebugger.serverTick(event.getServer());
            var server = event.getServer();
            if (server.getTickCount() % 200 == 0) {
                var overworld = server.overworld();
                com.sheepfromheaven.minenow.dominion.DominionSavedData.get(overworld).tickStructureStock(overworld);
            }
            if (server.getTickCount() % 100 == 0) {
                var overworld = server.overworld();
                com.sheepfromheaven.minenow.dominion.DominionSavedData.get(overworld).tickGrowth(overworld);
                com.sheepfromheaven.minenow.dominion.DominionSavedData.get(overworld).tickFieldRescan(overworld);
            }
        });

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        modBus.addListener(net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent.class, event ->
                event.put(com.sheepfromheaven.minenow.inhabitant.ModEntities.INHABITANT.get(),
                        com.sheepfromheaven.minenow.inhabitant.InhabitantEntity.createAttributes().build()));

        modBus.addListener(net.neoforged.neoforge.registries.RegisterEvent.class, event ->
                event.register(net.minecraft.core.registries.Registries.TEST_FUNCTION, helper -> {
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "inhabitant_sleeps_in_assigned_bed_at_night"),
                            com.sheepfromheaven.minenow.inhabitant.gametest.GoHomeGoalTests::inhabitantSleepsInAssignedBedAtNight);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "inhabitant_walks_home_when_requested"),
                            com.sheepfromheaven.minenow.inhabitant.gametest.GoHomeGoalTests::inhabitantWalksHomeWhenRequested);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "rest_need_decays_while_inhabitant_is_awake"),
                            com.sheepfromheaven.minenow.inhabitant.gametest.RestNeedTests::restNeedDecaysWhileInhabitantIsAwake);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "rest_need_restores_while_inhabitant_is_sleeping"),
                            com.sheepfromheaven.minenow.inhabitant.gametest.RestNeedTests::restNeedRestoresWhileInhabitantIsSleeping);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "inhabitant_walks_around_house_to_bed"),
                            com.sheepfromheaven.minenow.inhabitant.gametest.GoHomeGoalTests::inhabitantWalksAroundHouseToBed);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "inhabitant_opens_door_to_reach_bed"),
                            com.sheepfromheaven.minenow.inhabitant.gametest.GoHomeGoalTests::inhabitantOpensDoorToReachBed);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "inhabitant_opens_gate_to_reach_bed"),
                            com.sheepfromheaven.minenow.inhabitant.gametest.GoHomeGoalTests::inhabitantOpensGateToReachBed);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "inhabitant_requested_home_opens_spruce_gate_to_leave_yard"),
                            com.sheepfromheaven.minenow.inhabitant.gametest.GoHomeGoalTests::inhabitantRequestedHomeOpensSpruceGateToLeaveYard);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "inhabitant_requested_home_opens_gate_flanked_by_walls_to_leave_yard"),
                            com.sheepfromheaven.minenow.inhabitant.gametest.GoHomeGoalTests::inhabitantRequestedHomeOpensGateFlankedByWallsToLeaveYard);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "inhabitant_with_active_job_opens_spruce_gate_when_told_home"),
                            com.sheepfromheaven.minenow.inhabitant.gametest.GoHomeGoalTests::inhabitantWithActiveJobOpensSpruceGateWhenToldHome);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "disbanding_dominion_converts_inhabitants_and_clears_data"),
                            com.sheepfromheaven.minenow.dominion.gametest.DominionTests::disbandingDominionConvertsInhabitantsAndClearsData);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "applying_banner_pattern_updates_placed_banner"),
                            com.sheepfromheaven.minenow.dominion.gametest.DominionTests::applyingBannerPatternUpdatesPlacedBanner);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "destroying_structure_removes_inhabitant_home_and_job"),
                            com.sheepfromheaven.minenow.dominion.gametest.DominionTests::destroyingStructureRemovesInhabitantHomeAndJob);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "assigning_new_home_unassigns_inhabitant_from_previous_home"),
                            com.sheepfromheaven.minenow.dominion.gametest.DominionTests::assigningNewHomeUnassignsInhabitantFromPreviousHome);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "assigning_new_job_unassigns_inhabitant_from_previous_job"),
                            com.sheepfromheaven.minenow.dominion.gametest.DominionTests::assigningNewJobUnassignsInhabitantFromPreviousJob);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "assigning_house_reserves_bed_from_vanilla_villagers"),
                            com.sheepfromheaven.minenow.dominion.gametest.DominionTests::assigningHouseReservesBedFromVanillaVillagers);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "assigning_inhabitant_to_house_without_bed_is_refused"),
                            com.sheepfromheaven.minenow.dominion.gametest.DominionTests::assigningInhabitantToHouseWithoutBedIsRefused);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "inhabitant_complains_when_assigned_bed_is_broken"),
                            com.sheepfromheaven.minenow.dominion.gametest.DominionTests::inhabitantComplainsWhenAssignedBedIsBroken);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "storage_containers_skips_workstations"),
                            com.sheepfromheaven.minenow.storage.gametest.FisheryStorageTests::storageContainersSkipsWorkstations);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "storage_containers_finds_extra_containers_not_just_required_barrel"),
                            com.sheepfromheaven.minenow.storage.gametest.FisheryStorageTests::storageContainersFindsExtraContainersNotJustRequiredBarrel);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "storehouse_with_filter_rejects_disallowed_item"),
                            com.sheepfromheaven.minenow.jobs.gametest.CourierJobTests::storehouseWithFilterRejectsDisallowedItem);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "storehouse_without_filter_accepts_any_item"),
                            com.sheepfromheaven.minenow.jobs.gametest.CourierJobTests::storehouseWithoutFilterAcceptsAnyItem);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "courier_prefers_nearest_storehouse_over_farther_one"),
                            com.sheepfromheaven.minenow.jobs.gametest.CourierJobTests::courierPrefersNearestStorehouseOverFartherOne);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "courier_skips_storehouse_that_filters_out_the_item"),
                            com.sheepfromheaven.minenow.jobs.gametest.CourierJobTests::courierSkipsStorehouseThatFiltersOutTheItem);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "log_on_soil_with_natural_leaf_is_recognized_as_tree"),
                            com.sheepfromheaven.minenow.jobs.gametest.TreeScannerTests::logOnSoilWithNaturalLeafIsRecognizedAsTree);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "log_pillar_without_leaves_is_not_a_tree"),
                            com.sheepfromheaven.minenow.jobs.gametest.TreeScannerTests::logPillarWithoutLeavesIsNotATree);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "log_with_only_persistent_leaf_is_not_a_tree"),
                            com.sheepfromheaven.minenow.jobs.gametest.TreeScannerTests::logWithOnlyPersistentLeafIsNotATree);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "log_not_on_soil_is_not_a_tree"),
                            com.sheepfromheaven.minenow.jobs.gametest.TreeScannerTests::logNotOnSoilIsNotATree);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "log_adjacent_to_planks_aborts_even_with_leaves_higher_up"),
                            com.sheepfromheaven.minenow.jobs.gametest.TreeScannerTests::logAdjacentToPlanksAbortsEvenWithLeavesHigherUp);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "beehive_neighbor_allows_climbing_to_leaves_above"),
                            com.sheepfromheaven.minenow.jobs.gametest.TreeScannerTests::beehiveNeighborAllowsClimbingToLeavesAbove);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "same_axis_log_neighbor_allows_climbing_to_leaves_above"),
                            com.sheepfromheaven.minenow.jobs.gametest.TreeScannerTests::sameAxisLogNeighborAllowsClimbingToLeavesAbove);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "different_axis_log_of_same_species_is_included_in_trunk"),
                            com.sheepfromheaven.minenow.jobs.gametest.TreeScannerTests::differentAxisLogOfSameSpeciesIsIncludedInTrunk);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "diagonally_offset_trunk_is_still_recognized_as_tree"),
                            com.sheepfromheaven.minenow.jobs.gametest.TreeScannerTests::diagonallyOffsetTrunkIsStillRecognizedAsTree);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "find_tree_shape_flood_fills_the_whole_canopy_from_every_trunk_touch_point"),
                            com.sheepfromheaven.minenow.jobs.gametest.TreeScannerTests::findTreeShapeFloodFillsTheWholeCanopyFromEveryTrunkTouchPoint);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "trunk_forking_into_two_diagonal_branches_recognizes_both_branches"),
                            com.sheepfromheaven.minenow.jobs.gametest.TreeScannerTests::trunkForkingIntoTwoDiagonalBranchesRecognizesBothBranches);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "vines_adjacent_to_trunk_do_not_abort_tree"),
                            com.sheepfromheaven.minenow.jobs.gametest.TreeScannerTests::vinesAdjacentToTrunkDoNotAbortTree);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "non_log_pillar_two_blocks_away_does_not_abort_tree"),
                            com.sheepfromheaven.minenow.jobs.gametest.TreeScannerTests::nonLogPillarTwoBlocksAwayDoesNotAbortTree);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "grass_and_flowers_adjacent_to_trunk_do_not_abort_tree"),
                            com.sheepfromheaven.minenow.jobs.gametest.TreeScannerTests::grassAndFlowersAdjacentToTrunkDoNotAbortTree);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "find_tree_in_range_finds_valid_tree"),
                            com.sheepfromheaven.minenow.jobs.gametest.LumberyardTests::findTreeInRangeFindsValidTree);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "find_tree_in_range_skips_log_pillar_without_leaves"),
                            com.sheepfromheaven.minenow.jobs.gametest.LumberyardTests::findTreeInRangeSkipsLogPillarWithoutLeaves);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "find_tree_in_range_ignores_nearby_log_house_and_still_finds_real_tree"),
                            com.sheepfromheaven.minenow.jobs.gametest.LumberyardTests::findTreeInRangeIgnoresNearbyLogHouseAndStillFindsRealTree);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "find_plantable_spot_in_range_finds_bare_grass"),
                            com.sheepfromheaven.minenow.jobs.forester.gametest.ForesterTests::findPlantableSpotInRangeFindsBareGrass);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "find_plantable_spot_in_range_skips_spot_already_holding_a_sapling"),
                            com.sheepfromheaven.minenow.jobs.forester.gametest.ForesterTests::findPlantableSpotInRangeSkipsSpotAlreadyHoldingASapling);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "planting_a_sapling_consumes_only_one_from_the_stack"),
                            com.sheepfromheaven.minenow.jobs.forester.gametest.ForesterTests::plantingASaplingConsumesOnlyOneFromTheStack);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "find_plantable_spot_in_range_skips_spots_within_min_spacing_of_existing_sapling"),
                            com.sheepfromheaven.minenow.jobs.forester.gametest.ForesterTests::findPlantableSpotInRangeSkipsSpotsWithinMinSpacingOfExistingSapling);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "forester_plants_multiple_saplings_on_the_same_trip_without_returning_to_the_hut"),
                            com.sheepfromheaven.minenow.jobs.forester.gametest.ForesterTests::foresterPlantsMultipleSaplingsOnTheSameTripWithoutReturningToTheHut);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "inventory_survives_a_save_and_load_round_trip"),
                            com.sheepfromheaven.minenow.inhabitant.gametest.InhabitantPersistenceTests::inventorySurvivesASaveAndLoadRoundTrip);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "felling_trunk_harvests_canopy_leaves_directly"),
                            com.sheepfromheaven.minenow.jobs.gametest.LumberjackLeafHarvestTests::fellingTrunkHarvestsCanopyLeavesDirectly);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "interrupting_leaf_harvest_still_harvests_canopy_leaves_on_resume"),
                            com.sheepfromheaven.minenow.jobs.gametest.LumberjackLeafHarvestTests::interruptingLeafHarvestStillHarvestsCanopyLeavesOnResume);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "find_shore_position_uses_rim_of_sunken_well_instead_of_the_water_itself"),
                            com.sheepfromheaven.minenow.jobs.fisher.gametest.FisherJobShoreTests::findShorePositionUsesRimOfSunkenWellInsteadOfTheWaterItself);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "find_shore_position_prefers_same_level_ring_over_rim"),
                            com.sheepfromheaven.minenow.jobs.fisher.gametest.FisherJobShoreTests::findShorePositionPrefersSameLevelRingOverRim);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "lumberjack_clears_decayable_leaves_blocking_trunk_access"),
                            com.sheepfromheaven.minenow.jobs.gametest.LumberjackLeafHarvestTests::lumberjackClearsDecayableLeavesBlockingTrunkAccess);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "find_smoking_recipe_returns_cooked_result_for_raw_beef"),
                            com.sheepfromheaven.minenow.jobs.gametest.CookingRecipesTests::findSmokingRecipeReturnsCookedResultForRawBeef);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "find_smoking_recipe_is_empty_for_cooked_beef"),
                            com.sheepfromheaven.minenow.jobs.gametest.CookingRecipesTests::findSmokingRecipeIsEmptyForCookedBeef);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "is_fuel_true_for_log"),
                            com.sheepfromheaven.minenow.jobs.gametest.CookingRecipesTests::isFuelTrueForLog);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "is_fuel_false_for_stone"),
                            com.sheepfromheaven.minenow.jobs.gametest.CookingRecipesTests::isFuelFalseForStone);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "is_ready_to_eat_true_for_cooked_beef"),
                            com.sheepfromheaven.minenow.jobs.gametest.CookingRecipesTests::isReadyToEatTrueForCookedBeef);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "is_ready_to_eat_false_for_raw_beef"),
                            com.sheepfromheaven.minenow.jobs.gametest.CookingRecipesTests::isReadyToEatFalseForRawBeef);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "is_ready_to_eat_false_for_non_food_item"),
                            com.sheepfromheaven.minenow.jobs.gametest.CookingRecipesTests::isReadyToEatFalseForNonFoodItem);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "is_ready_to_eat_true_for_bread_without_any_smoking_recipe"),
                            com.sheepfromheaven.minenow.jobs.gametest.CookingRecipesTests::isReadyToEatTrueForBreadWithoutAnySmokingRecipe);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "extract_one_removes_exactly_one_item"),
                            com.sheepfromheaven.minenow.jobs.gametest.ContainerUtilTests::extractOneRemovesExactlyOneItem);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "extract_one_returns_empty_when_no_match"),
                            com.sheepfromheaven.minenow.jobs.gametest.ContainerUtilTests::extractOneReturnsEmptyWhenNoMatch);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "find_nearest_structure_of_type_picks_closest_match"),
                            com.sheepfromheaven.minenow.dominion.gametest.DominionTests::findNearestStructureOfTypePicksClosestMatch);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "find_nearest_structure_of_type_skips_closest_when_filter_fails"),
                            com.sheepfromheaven.minenow.dominion.gametest.DominionTests::findNearestStructureOfTypeSkipsClosestWhenFilterFails);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "inhabitant_stores_tool_in_storage_at_night"),
                            com.sheepfromheaven.minenow.jobs.gametest.FinishWorkGoalTests::inhabitantStoresToolInStorageAtNight);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "interrupted_lumberjack_returns_to_hut_instead_of_resuming_chopping"),
                            com.sheepfromheaven.minenow.inhabitant.gametest.JobInterruptionTests::interruptedLumberjackReturnsToHutInsteadOfResumingChopping);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "unassigning_lumberjack_mid_chop_drops_stale_job"),
                            com.sheepfromheaven.minenow.inhabitant.gametest.JobInterruptionTests::unassigningLumberjackMidChopDropsStaleJob);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "job_does_not_start_when_structure_requirements_are_unmet"),
                            com.sheepfromheaven.minenow.inhabitant.gametest.JobInterruptionTests::jobDoesNotStartWhenStructureRequirementsAreUnmet);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "reassigning_lumberjack_mid_chop_drops_stale_job"),
                            com.sheepfromheaven.minenow.inhabitant.gametest.JobInterruptionTests::reassigningLumberjackMidChopDropsStaleJob);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "bed_in_real_detected_room_satisfies_house_requirement"),
                            com.sheepfromheaven.minenow.dominion.gametest.DominionTests::bedInRealDetectedRoomSatisfiesHouseRequirement);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "handing_lumberjack_an_axe_clears_the_missing_axe_issue"),
                            com.sheepfromheaven.minenow.jobs.gametest.LumberjackToolIssueTests::handingLumberjackAnAxeClearsTheMissingAxeIssue);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "fishery_requires_barrel_and_cauldron_not_smoker_or_crafting_table"),
                            com.sheepfromheaven.minenow.storage.gametest.FisheryStorageTests::fisheryRequiresBarrelAndCauldronNotSmokerOrCraftingTable);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "structure_wand_orients_door_to_player_facing"),
                            com.sheepfromheaven.minenow.structure.gametest.StructureWandTests::structureWandOrientsDoorToPlayerFacing);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "approach_pos_is_marker_when_marker_is_on_the_floor"),
                            com.sheepfromheaven.minenow.jobs.gametest.WorkplaceApproachTests::approachPosIsMarkerWhenMarkerIsOnTheFloor);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "approach_pos_finds_floor_when_marker_is_embedded_high_in_a_shaft"),
                            com.sheepfromheaven.minenow.jobs.gametest.WorkplaceApproachTests::approachPosFindsFloorWhenMarkerIsEmbeddedHighInAShaft);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "cook_loads_raw_food_and_fuel_into_the_smoker"),
                            com.sheepfromheaven.minenow.jobs.gametest.CookJobTests::cookLoadsRawFoodAndFuelIntoTheSmoker);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "missing_raw_food_reports_generic_label_instead_of_a_specific_item"),
                            com.sheepfromheaven.minenow.jobs.gametest.CookJobTests::missingRawFoodReportsGenericLabelInsteadOfASpecificItem);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "cooking_with_burned_out_fuel_goes_back_to_fetch_more_fuel"),
                            com.sheepfromheaven.minenow.jobs.gametest.CookJobTests::cookingWithBurnedOutFuelGoesBackToFetchMoreFuel);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "missing_both_raw_food_and_fuel_reports_both_requests_at_once"),
                            com.sheepfromheaven.minenow.jobs.gametest.CookJobTests::missingBothRawFoodAndFuelReportsBothRequestsAtOnce);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "charcoal_burner_loads_logs_into_the_furnace"),
                            com.sheepfromheaven.minenow.jobs.gametest.CharcoalBurnerJobTests::charcoalBurnerLoadsLogsIntoTheFurnace);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "missing_logs_reports_a_request"),
                            com.sheepfromheaven.minenow.jobs.gametest.CharcoalBurnerJobTests::missingLogsReportsARequest);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "farmer_harvests_mature_crop"),
                            com.sheepfromheaven.minenow.jobs.gametest.FarmerJobTests::farmerHarvestsMatureCrop);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "farmer_tills_bare_ground_in_field"),
                            com.sheepfromheaven.minenow.jobs.gametest.FarmerJobTests::farmerTillsBareGroundInField);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "farmer_clears_grass_before_tilling_grass_block"),
                            com.sheepfromheaven.minenow.jobs.gametest.FarmerJobTests::farmerClearsGrassBeforeTillingGrassBlock);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "farmer_fetches_hoe_from_storage_before_tilling"),
                            com.sheepfromheaven.minenow.jobs.gametest.FarmerJobTests::farmerFetchesHoeFromStorageBeforeTilling);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "farmer_irrigates_before_tilling_unwatered_field"),
                            com.sheepfromheaven.minenow.jobs.gametest.FarmerJobTests::farmerIrrigatesBeforeTillingUnwateredField);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "farmer_refills_empty_bucket_from_nearby_water_source"),
                            com.sheepfromheaven.minenow.jobs.gametest.FarmerJobTests::farmerRefillsEmptyBucketFromNearbyWaterSource);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "farmer_prefers_self_refilling_water_source_over_puddle"),
                            com.sheepfromheaven.minenow.jobs.gametest.FarmerJobTests::farmerPrefersSelfRefillingWaterSourceOverPuddle);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "farmer_plants_templated_seed_on_bare_farmland"),
                            com.sheepfromheaven.minenow.jobs.gametest.FarmerJobTests::farmerPlantsTemplatedSeedOnBareFarmland);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "farmer_fetches_seed_from_storage_before_planting"),
                            com.sheepfromheaven.minenow.jobs.gametest.FarmerJobTests::farmerFetchesSeedFromStorageBeforePlanting);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "field_area_detector_detects_flat_3x3_pit"),
                            com.sheepfromheaven.minenow.structure.field.gametest.FieldAreaDetectorTests::detectsFlat3x3Pit);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "field_area_detector_detects_flat_3x3_pit_with_deeper_center"),
                            com.sheepfromheaven.minenow.structure.field.gametest.FieldAreaDetectorTests::detectsFlat3x3PitWithDeeperCenter);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "periodic_rescan_grows_field_after_wall_removed"),
                            com.sheepfromheaven.minenow.structure.field.gametest.FieldRescanTests::periodicRescanGrowsFieldAfterWallRemoved);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "debug_lumberyard_barrel_is_stocked_with_an_axe"),
                            com.sheepfromheaven.minenow.command.gametest.DebugCommandsTests::debugLumberyardBarrelIsStockedWithAnAxe);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "debug_fishery_barrel_is_stocked_with_a_fishing_rod"),
                            com.sheepfromheaven.minenow.command.gametest.DebugCommandsTests::debugFisheryBarrelIsStockedWithAFishingRod);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "debug_tavern_barrel_is_stocked_with_food_and_fuel"),
                            com.sheepfromheaven.minenow.command.gametest.DebugCommandsTests::debugTavernBarrelIsStockedWithFoodAndFuel);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "path_node_wand_toggles_node_at_clicked_position"),
                            com.sheepfromheaven.minenow.pathfinding.gametest.PathNodeTests::pathNodeWandTogglesNodeAtClickedPosition);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "inhabitant_wanders_toward_nearby_street_node"),
                            com.sheepfromheaven.minenow.pathfinding.gametest.PathNodeTests::inhabitantWandersTowardNearbyStreetNode);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "inhabitant_climbs_ladder_to_reach_elevated_target"),
                            com.sheepfromheaven.minenow.pathfinding.gametest.PathNodeTests::inhabitantClimbsLadderToReachElevatedTarget);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "inhabitant_opens_fence_gate_to_reach_target"),
                            com.sheepfromheaven.minenow.pathfinding.gametest.PathNodeTests::inhabitantOpensFenceGateToReachTarget);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "inhabitant_opens_fence_gate_on_raised_ground_to_reach_target"),
                            com.sheepfromheaven.minenow.pathfinding.gametest.PathNodeTests::inhabitantOpensFenceGateOnRaisedGroundToReachTarget);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "add_inhabitant_rejects_when_at_capacity"),
                            com.sheepfromheaven.minenow.dominion.gametest.DominionGrowthTests::addInhabitantRejectsWhenAtCapacity);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "can_attempt_immigration_requires_attraction_room_and_cooldown"),
                            com.sheepfromheaven.minenow.dominion.gametest.DominionGrowthTests::canAttemptImmigrationRequiresAttractionRoomAndCooldown);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "check_and_apply_level_up_advances_only_when_all_thresholds_met"),
                            com.sheepfromheaven.minenow.dominion.gametest.DominionGrowthTests::checkAndApplyLevelUpAdvancesOnlyWhenAllThresholdsMet);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "walk_to_banner_goal_reaches_awaiting_decision_phase"),
                            com.sheepfromheaven.minenow.dominion.gametest.DominionGrowthTests::walkToBannerGoalReachesAwaitingDecisionPhase);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "higher_tier_dominion_has_larger_effective_radius_for_bell_attraction"),
                            com.sheepfromheaven.minenow.dominion.gametest.DominionGrowthTests::higherTierDominionHasLargerEffectiveRadiusForBellAttraction);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "spawn_egg_spawns_inhabitant_inside_dominion"),
                            com.sheepfromheaven.minenow.dominion.gametest.DominionGrowthTests::spawnEggSpawnsInhabitantInsideDominion);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "spawn_egg_fails_outside_any_dominion"),
                            com.sheepfromheaven.minenow.dominion.gametest.DominionGrowthTests::spawnEggFailsOutsideAnyDominion);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "spawn_egg_fails_when_dominion_at_capacity"),
                            com.sheepfromheaven.minenow.dominion.gametest.DominionGrowthTests::spawnEggFailsWhenDominionAtCapacity);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "structure_wand_builds_building_with_fixed_8x10x6_dimensions"),
                            com.sheepfromheaven.minenow.structure.gametest.StructureWandTests::structureWandBuildsBuildingWithFixed8x10x6Dimensions);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "shift_right_click_cycles_mode_and_builds_matching_furnished_structure"),
                            com.sheepfromheaven.minenow.structure.gametest.StructureWandTests::shiftRightClickCyclesModeAndBuildsMatchingFurnishedStructure);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "shift_clicking_tool_in_general_slots_equips_main_hand_first"),
                            com.sheepfromheaven.minenow.storage.gametest.VanillaRowMenuTests::shiftClickingToolInGeneralSlotsEquipsMainHandFirst);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "shift_clicking_tool_from_player_inventory_equips_main_hand_first"),
                            com.sheepfromheaven.minenow.storage.gametest.VanillaRowMenuTests::shiftClickingToolFromPlayerInventoryEquipsMainHandFirst);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "picking_up_a_tool_equips_main_hand_instead_of_general_inventory"),
                            com.sheepfromheaven.minenow.inhabitant.gametest.InhabitantPickupTests::pickingUpAToolEquipsMainHandInsteadOfGeneralInventory);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "picking_up_non_tool_item_goes_to_general_inventory"),
                            com.sheepfromheaven.minenow.inhabitant.gametest.InhabitantPickupTests::pickingUpNonToolItemGoesToGeneralInventory);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "barrier_bounds_keeps_idle_inhabitant_inside_test_space"),
                            com.sheepfromheaven.minenow.inhabitant.gametest.InhabitantConfinementTests::barrierBoundsKeepsIdleInhabitantInsideTestSpace);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "miner_digs_stairway_tunnels_through_ore_and_deposits_at_the_mine"),
                            com.sheepfromheaven.minenow.jobs.gametest.MinerJobTests::minerDigsStairwayTunnelsThroughOreAndDepositsAtTheMine);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "miner_places_support_beams_when_toggle_is_enabled"),
                            com.sheepfromheaven.minenow.jobs.gametest.MinerJobTests::minerPlacesSupportBeamsWhenToggleIsEnabled);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "miner_with_wooden_pickaxe_leaves_iron_ore_untouched"),
                            com.sheepfromheaven.minenow.jobs.gametest.MinerJobTests::minerWithWoodenPickaxeLeavesIronOreUntouched);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "miner_with_stone_pickaxe_mines_coal_and_iron_ore"),
                            com.sheepfromheaven.minenow.jobs.gametest.MinerJobTests::minerWithStonePickaxeMinesCoalAndIronOre);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "miner_tunnels_around_a_placed_mine_claim_outside_the_structures_own_work_area"),
                            com.sheepfromheaven.minenow.jobs.gametest.MinerJobTests::minerTunnelsAroundAPlacedMineClaimOutsideTheStructuresOwnWorkArea);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "level_up_fails_without_meeting_next_level_requirements"),
                            com.sheepfromheaven.minenow.structure.gametest.StructureLevelingTests::levelUpFailsWithoutMeetingNextLevelRequirements);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "level_up_succeeds_once_next_level_requirements_are_met"),
                            com.sheepfromheaven.minenow.structure.gametest.StructureLevelingTests::levelUpSucceedsOnceNextLevelRequirementsAreMet);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "level_up_is_capped_by_dominion_tier"),
                            com.sheepfromheaven.minenow.structure.gametest.StructureLevelingTests::levelUpIsCappedByDominionTier);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "losing_a_level_up_requirement_only_disables_the_slot_it_unlocked"),
                            com.sheepfromheaven.minenow.structure.gametest.StructureLevelingTests::losingALevelUpRequirementOnlyDisablesTheSlotItUnlocked);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "mine_claim_marker_is_a_level_one_work_condition"),
                            com.sheepfromheaven.minenow.structure.gametest.WorkAreaMarkerRequirementTests::mineClaimMarkerIsALevelOneWorkCondition);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "territory_placement_query_respects_territory_owner_and_bounds"),
                            com.sheepfromheaven.minenow.structure.gametest.WorkAreaMarkerRequirementTests::territoryPlacementQueryRespectsTerritoryOwnerAndBounds);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "even_territory_size_rounds_up_to_keep_center"),
                            com.sheepfromheaven.minenow.structure.gametest.WorkAreaMarkerRequirementTests::evenTerritorySizeRoundsUpToKeepCenter);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "static_work_area_effect_radius_bounds_foraging"),
                            com.sheepfromheaven.minenow.jobs.gametest.LumberyardTests::staticWorkAreaEffectRadiusBoundsForaging);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "miner_with_no_claim_does_not_mine"),
                            com.sheepfromheaven.minenow.jobs.gametest.MinerJobTests::minerWithNoClaimDoesNotMine);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "miner_without_pickaxe_leaves_stone_untouched"),
                            com.sheepfromheaven.minenow.jobs.gametest.MinerJobTests::minerWithoutPickaxeLeavesStoneUntouched);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "miner_never_breaks_bedrock_even_with_a_pickaxe"),
                            com.sheepfromheaven.minenow.jobs.gametest.MinerJobTests::minerNeverBreaksBedrockEvenWithAPickaxe);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "miner_without_pickaxe_reports_needs_pickaxe_then_mines_once_one_arrives"),
                            com.sheepfromheaven.minenow.jobs.gametest.MinerJobTests::minerWithoutPickaxeReportsNeedsPickaxeThenMinesOnceOneArrives);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "miner_digs_a_fully_walkable_stairway_through_dirt"),
                            com.sheepfromheaven.minenow.jobs.gametest.MinerJobTests::minerDigsAFullyWalkableStairwayThroughDirt);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "miner_handed_a_pickaxe_directly_resumes_mining_immediately"),
                            com.sheepfromheaven.minenow.jobs.gametest.MinerJobTests::minerHandedAPickaxeDirectlyResumesMiningImmediately);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "miner_strip_mines_ribs_off_the_south_branch"),
                            com.sheepfromheaven.minenow.jobs.gametest.MinerJobTests::minerStripMinesRibsOffTheSouthBranch);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "miner_bridges_a_ravine_in_branch_tunnel_instead_of_getting_stuck"),
                            com.sheepfromheaven.minenow.jobs.gametest.MinerJobTests::minerBridgesARavineInBranchTunnelInsteadOfGettingStuck);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "miner_places_torches_along_the_branch_tunnel"),
                            com.sheepfromheaven.minenow.jobs.gametest.MinerJobTests::minerPlacesTorchesAlongTheBranchTunnel);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "miner_mines_with_real_timing_and_spends_durability"),
                            com.sheepfromheaven.minenow.jobs.gametest.MinerJobTests::minerMinesWithRealTimingAndSpendsDurability);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "miner_resumes_digging_without_restarting_the_branch"),
                            com.sheepfromheaven.minenow.jobs.gametest.MinerJobTests::minerResumesDiggingWithoutRestartingTheBranch);
                    helper.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("minenow", "miner_never_branches_back_toward_the_stairway"),
                            com.sheepfromheaven.minenow.jobs.gametest.MinerJobTests::minerNeverBranchesBackTowardTheStairway);
                }));

        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            registerRenderers(modBus);
            com.sheepfromheaven.minenow.client.ConfigScreen.registerExtensionPoint(modContainer);
            com.sheepfromheaven.minenow.dev.client.DevWorldBootstrap.register();
            com.sheepfromheaven.minenow.dev.client.GametestPlaygroundBootstrap.register();
            NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.ClientTickEvent.Post.class, event -> {
                com.sheepfromheaven.minenow.jobs.lumberjack.client.TreeScanGizmoRenderer.onClientTick();
                com.sheepfromheaven.minenow.pathfinding.client.PathNodeGizmoRenderer.onClientTick();
            });
        }
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        com.sheepfromheaven.minenow.structure.StructureRequirementsConfig.load(
                net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get());
        com.sheepfromheaven.minenow.structure.StructureLevelJsonConfig.load(
                net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get());
    }

    @EventBusSubscriber(modid = MOD_ID, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onRegisterMenuScreens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {
            event.register(
                    com.sheepfromheaven.minenow.inhabitant.InhabitantMenus.INHABITANT_INVENTORY_TYPE.get(),
                    com.sheepfromheaven.minenow.inhabitant.client.InhabitantScreen::new);
            event.register(
                    com.sheepfromheaven.minenow.storage.inventory.ModMenus.FILTER_TYPE.get(),
                    com.sheepfromheaven.minenow.storage.client.FilterScreen::new);
            event.register(
                    com.sheepfromheaven.minenow.storage.inventory.ModMenus.STRUCTURE_FILTER_TYPE.get(),
                    com.sheepfromheaven.minenow.structure.client.StructureFilterScreen::new);
            event.register(
                    com.sheepfromheaven.minenow.storage.inventory.ModMenus.DOMINION_TYPE.get(),
                    com.sheepfromheaven.minenow.dominion.client.DominionScreen::new);
            event.register(
                    com.sheepfromheaven.minenow.storage.inventory.ModMenus.FIELD_MARKER_TYPE.get(),
                    com.sheepfromheaven.minenow.structure.field.client.FieldMarkerScreen::new);
        }

        @SubscribeEvent
        public static void onRegisterItemTintSources(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.ItemTintSources event) {
            event.register(
                    net.minecraft.resources.Identifier.fromNamespaceAndPath(MOD_ID, "path_node_wand"),
                    com.sheepfromheaven.minenow.pathfinding.client.PathNodeWandTintSource.MAP_CODEC);
        }
    }

    public static void registerRenderers(IEventBus modBus) {
        modBus.addListener(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers.class, event -> {
            event.registerBlockEntityRenderer(TownBannerBlocks.TOWN_BANNER_BE.get(),
                    com.sheepfromheaven.minenow.dominion.townbanner.client.TownBannerRenderer::new);
            event.registerBlockEntityRenderer(StructureBlocks.STRUCTURE_MARKER_BE.get(),
                    com.sheepfromheaven.minenow.structure.client.StructureMarkerRenderer::new);
            event.registerEntityRenderer(com.sheepfromheaven.minenow.inhabitant.ModEntities.INHABITANT.get(),
                    com.sheepfromheaven.minenow.inhabitant.client.InhabitantRenderer::new);
            event.registerEntityRenderer(com.sheepfromheaven.minenow.inhabitant.ModEntities.NPC_FISHING_BOBBER.get(),
                    com.sheepfromheaven.minenow.jobs.fisher.client.NpcFishingBobberRenderer::new);
        });
    }
}
