package com.sheepfromheaven.minenow;

import com.sheepfromheaven.minenow.dev.GametestPlayground;
import com.sheepfromheaven.minenow.farmer.FarmerReport;
import com.sheepfromheaven.minenow.farmer.FarmerRules;
import com.sheepfromheaven.minenow.farmer.FarmerTests;
import com.sheepfromheaven.minenow.fisherman.FishermanEntities;
import com.sheepfromheaven.minenow.fisherman.FishermanTests;
import com.sheepfromheaven.minenow.gametest.TestGroup;
import com.sheepfromheaven.minenow.farmer.FarmerTradeTests;
import com.sheepfromheaven.minenow.villager.BrainHook;
import com.sheepfromheaven.minenow.villager.FollowTests;
import com.sheepfromheaven.minenow.villager.HomeStorage;
import com.sheepfromheaven.minenow.villager.MeetingTests;
import com.sheepfromheaven.minenow.villager.HomeStorageTests;
import com.sheepfromheaven.minenow.villager.ModVillagers;
import com.sheepfromheaven.minenow.villager.ProfessionTrades;
import com.sheepfromheaven.minenow.villager.StorageTheft;
import com.sheepfromheaven.minenow.villager.RoadTests;
import com.sheepfromheaven.minenow.villager.Roads;
import com.sheepfromheaven.minenow.villager.TradeTable;
import com.sheepfromheaven.minenow.villager.VillagerNames;
import com.sheepfromheaven.minenow.villager.VillagerNamesTests;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.function.Consumer;

@Mod(MineNow.MOD_ID)
public final class MineNow {
    public static final String MOD_ID = "minenow";

    public MineNow(IEventBus modBus) {
        ModVillagers.register(modBus);
        FishermanEntities.register(modBus);
        NeoForge.EVENT_BUS.addListener(GametestPlayground::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(GametestPlayground::onServerTick);
        NeoForge.EVENT_BUS.addListener(GametestPlayground::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(BrainHook::onEntityTick);
        NeoForge.EVENT_BUS.addListener(Roads::onEntityJoin);
        NeoForge.EVENT_BUS.addListener(Roads::onAddReloadListeners);
        NeoForge.EVENT_BUS.addListener(FarmerReport::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(FarmerRules::onFarmlandTrample);
        NeoForge.EVENT_BUS.addListener(FarmerRules::onEntityJoin);
        NeoForge.EVENT_BUS.addListener(ProfessionTrades::onEntityInteract);
        NeoForge.EVENT_BUS.addListener(ProfessionTrades::onTrade);
        NeoForge.EVENT_BUS.addListener(TradeTable::onAddReloadListeners);
        NeoForge.EVENT_BUS.addListener(VillagerNames::onAddReloadListeners);
        NeoForge.EVENT_BUS.addListener(VillagerNames::onEntityJoin);
        NeoForge.EVENT_BUS.addListener(HomeStorage::onEntityTick);
        NeoForge.EVENT_BUS.addListener(HomeStorage::onDeath);
        NeoForge.EVENT_BUS.addListener(HomeStorage::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(HomeStorage::onConversion);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, HomeStorage::onBreak);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, StorageTheft::onPlace);
        NeoForge.EVENT_BUS.addListener(StorageTheft::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(StorageTheft::onEntityJoin);

        modBus.addListener(RegisterEvent.class, event -> event.register(Registries.TEST_FUNCTION, helper -> {
            test(helper, TestGroup.FARMER, "farmer_harvests_and_replants_field_beyond_vanilla_reach", FarmerTests::farmerHarvestsAndReplantsFieldBeyondVanillaReach);
            test(helper, TestGroup.FARMER, "farmer_keeps_tending_after_brain_is_rebuilt", FarmerTests::farmerKeepsTendingAfterBrainIsRebuilt);
            test(helper, TestGroup.FARMER, "farmer_deposits_harvest_in_own_chest_at_home", FarmerTests::farmerDepositsHarvestInOwnChestAtHome);
            test(helper, TestGroup.FARMER, "farmer_without_hoe_leaves_ripe_crops_alone", FarmerTests::farmerWithoutHoeLeavesRipeCropsAlone);
            test(helper, TestGroup.FARMER, "only_the_field_connected_to_the_composter_is_farmed", FarmerTests::onlyTheFieldConnectedToTheComposterIsFarmed);
            test(helper, TestGroup.FARMER, "farmer_tills_connected_ground_near_water", FarmerTests::farmerTillsConnectedGroundNearWater);
            test(helper, TestGroup.FARMER, "composter_walled_in_on_four_sides_has_no_field", FarmerTests::composterWalledInOnFourSidesHasNoField);
            test(helper, TestGroup.FARMER, "a_line_of_string_ends_the_field", FarmerTests::aLineOfStringEndsTheField);
            test(helper, TestGroup.FARMER, "crops_and_seeds_are_recognized_beyond_vanilla_wheat", FarmerTests::cropsAndSeedsAreRecognizedBeyondVanillaWheat);
            test(helper, TestGroup.FARMER, "farmer_harvests_by_right_click_when_a_mod_provides_it", FarmerTests::farmerHarvestsByRightClickWhenAModProvidesIt);
            test(helper, TestGroup.TRADING, "crops_without_a_price_sell_at_the_default_rate", FarmerTradeTests::cropsWithoutAPriceSellAtTheDefaultRate);
            test(helper, TestGroup.FARMER, "farmer_retills_farmland_that_was_trampled", FarmerTests::farmerRetillsFarmlandThatWasTrampled);
            test(helper, TestGroup.FARMER, "farmer_with_a_full_inventory_still_harvests", FarmerTests::farmerWithAFullInventoryStillHarvests);
            test(helper, TestGroup.FARMER, "farmer_report_explains_what_the_farmer_is_missing", FarmerTests::farmerReportExplainsWhatTheFarmerIsMissing);
            test(helper, TestGroup.FARMER, "farmer_does_not_bake_bread_or_compost_seeds_at_the_composter", FarmerTests::farmerDoesNotBakeBreadOrCompostSeedsAtTheComposter);
            test(helper, TestGroup.FARMER, "farmer_uses_bone_meal_on_growing_crops", FarmerTests::farmerUsesBoneMealOnGrowingCrops);
            test(helper, TestGroup.TRADING, "farmer_buys_bone_meal_in_batches_of_eight_up_to_thirty_two", FarmerTradeTests::farmerBuysBoneMealInBatchesOfEightUpToThirtyTwo);
            test(helper, TestGroup.FARMER, "novice_farmer_only_works_ten_blocks_out", FarmerTests::noviceFarmerOnlyWorksTenBlocksOut);
            test(helper, TestGroup.FARMER, "master_farmer_works_further_out", FarmerTests::masterFarmerWorksFurtherOut);
            test(helper, TestGroup.FARMER, "harvesting_takes_time_and_swings_the_hoe", FarmerTests::harvestingTakesTimeAndSwingsTheHoe);
            test(helper, TestGroup.FARMER, "better_hoes_reap_faster", FarmerTests::betterHoesReapFaster);
            test(helper, TestGroup.FARMER, "farmer_holds_its_hoe_while_working_with_a_player_watching", FarmerTests::farmerHoldsItsHoeWhileWorkingWithAPlayerWatching);
            test(helper, TestGroup.FARMER, "harvesting_wears_out_the_hoe", FarmerTests::harvestingWearsOutTheHoe);
            test(helper, TestGroup.FARMER, "farmer_picks_up_a_hoe_thrown_to_it", FarmerTests::farmerPicksUpAHoeThrownToIt);
            test(helper, TestGroup.FARMER, "farmer_fetches_a_spare_hoe_from_its_home_chest", FarmerTests::farmerFetchesASpareHoeFromItsHomeChest);
            test(helper, TestGroup.TRADING, "hoeless_farmer_offers_to_buy_an_undamaged_hoe", FarmerTradeTests::hoelessFarmerOffersToBuyAnUndamagedHoe);
            test(helper, TestGroup.TRADING, "starting_purse_is_granted_only_once", FarmerTradeTests::startingPurseIsGrantedOnlyOnce);
            test(helper, TestGroup.TRADING, "selling_a_hoe_to_a_farmer_moves_real_items", FarmerTradeTests::sellingAHoeToAFarmerMovesRealItems);
            test(helper, TestGroup.TRADING, "farmer_sells_crops_from_its_inventory_and_chest", FarmerTradeTests::farmerSellsCropsFromItsInventoryAndChest);
            test(helper, TestGroup.TRADING, "farmer_with_nothing_to_trade_shakes_its_head", FarmerTradeTests::farmerWithNothingToTradeShakesItsHead);
            test(helper, TestGroup.TRADING, "novice_farmer_only_buys_wooden_hoes", FarmerTradeTests::noviceFarmerOnlyBuysWoodenHoes);
            test(helper, TestGroup.TRADING, "journeyman_farmer_buys_up_to_iron_hoes", FarmerTradeTests::journeymanFarmerBuysUpToIronHoes);
            test(helper, TestGroup.TRADING, "farmer_keeps_the_enchantments_of_a_hoe_it_buys", FarmerTradeTests::farmerKeepsTheEnchantmentsOfAHoeItBuys);
            test(helper, TestGroup.FISHERMAN, "fisherman_without_a_rod_offers_to_buy_one", FishermanTests::fishermanWithoutARodOffersToBuyOne);
            test(helper, TestGroup.FISHERMAN, "fisherman_fishes_water_near_its_barrel", FishermanTests::fishermanFishesWaterNearItsBarrel);
            test(helper, TestGroup.FISHERMAN, "fisherman_without_a_rod_catches_nothing", FishermanTests::fishermanWithoutARodCatchesNothing);
            test(helper, TestGroup.FISHERMAN, "fisherman_casts_a_bobber_and_a_fish_bites", FishermanTests::fishermanCastsABobberAndAFishBites);
            test(helper, TestGroup.FISHERMAN, "fisherman_ignores_water_beyond_its_range", FishermanTests::fishermanIgnoresWaterBeyondItsRange);
            test(helper, TestGroup.FISHERMAN, "fisherman_unloads_its_catch_at_home", FishermanTests::fishermanUnloadsItsCatchAtHome);
            test(helper, TestGroup.VILLAGERS, "villager_is_given_a_name_when_it_spawns", VillagerNamesTests::villagerIsGivenANameWhenItSpawns);
            test(helper, TestGroup.ROADS, "villager_takes_the_road_instead_of_cutting_across_grass", RoadTests::villagerTakesTheRoadInsteadOfCuttingAcrossGrass);
            test(helper, TestGroup.ROADS, "villager_still_cuts_across_when_the_road_is_a_long_detour", RoadTests::villagerStillCutsAcrossWhenTheRoadIsALongDetour);
            test(helper, TestGroup.ROADS, "idle_villagers_stroll_along_roads", RoadTests::idleVillagersStrollAlongRoads);
            test(helper, TestGroup.ROADS, "a_paved_street_beats_a_straight_dirt_path", RoadTests::aPavedStreetBeatsAStraightDirtPath);
            test(helper, TestGroup.VILLAGERS, "unemployed_villager_follows_a_player_holding_an_emerald", FollowTests::unemployedVillagerFollowsAPlayerHoldingAnEmerald);
            test(helper, TestGroup.VILLAGERS, "employed_villager_ignores_the_emerald", FollowTests::employedVillagerIgnoresTheEmerald);
            test(helper, TestGroup.VILLAGERS, "following_far_from_home_abandons_the_old_bed", FollowTests::followingFarFromHomeAbandonsTheOldBed);
            test(helper, TestGroup.VILLAGERS, "villager_picks_the_nearest_bell_when_the_afternoon_starts", MeetingTests::villagerPicksTheNearestBellWhenTheAfternoonStarts);
            test(helper, TestGroup.VILLAGERS, "villagers_do_not_hand_items_to_each_other", MeetingTests::villagersDoNotHandItemsToEachOther);
            test(helper, TestGroup.VILLAGERS, "a_name_from_a_name_tag_is_kept", VillagerNamesTests::aNameFromANameTagIsKept);
            test(helper, TestGroup.STORAGE, "villager_claims_and_locks_chest_near_its_bed", HomeStorageTests::villagerClaimsAndLocksChestNearItsBed);
            test(helper, TestGroup.STORAGE, "two_villagers_do_not_share_one_chest", HomeStorageTests::twoVillagersDoNotShareOneChest);
            test(helper, TestGroup.STORAGE, "right_clicking_an_owned_chest_says_who_owns_it", HomeStorageTests::rightClickingAnOwnedChestSaysWhoOwnsIt);
            test(helper, TestGroup.STORAGE, "breaking_a_claimed_chest_is_theft", HomeStorageTests::breakingAClaimedChestIsTheft);
            test(helper, TestGroup.STORAGE, "owner_dying_releases_and_unlocks_its_chest", HomeStorageTests::ownerDyingReleasesAndUnlocksItsChest);
            test(helper, TestGroup.STORAGE, "placing_a_chest_next_to_an_owned_chest_extends_the_claim", HomeStorageTests::placingAChestNextToAnOwnedChestExtendsTheClaim);
            test(helper, TestGroup.STORAGE, "breaking_one_half_of_an_owned_double_chest_keeps_the_other_half", HomeStorageTests::breakingOneHalfOfAnOwnedDoubleChestKeepsTheOtherHalf);
            test(helper, TestGroup.STORAGE, "hopper_draining_an_owned_chest_is_theft_by_its_placer", HomeStorageTests::hopperDrainingAnOwnedChestIsTheftByItsPlacer);
            test(helper, TestGroup.STORAGE, "hopper_minecart_draining_an_owned_chest_is_theft_by_its_placer", HomeStorageTests::hopperMinecartDrainingAnOwnedChestIsTheftByItsPlacer);
            test(helper, TestGroup.STORAGE, "villager_does_not_claim_a_chest_above_a_hopper", HomeStorageTests::villagerDoesNotClaimAChestAboveAHopper);
        }));
    }

    private static void test(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> helper, TestGroup group, String name, Consumer<GameTestHelper> test) {
        TestGroup.assign(name, group);
        helper.register(Identifier.fromNamespaceAndPath(MOD_ID, name), test);
    }
}
