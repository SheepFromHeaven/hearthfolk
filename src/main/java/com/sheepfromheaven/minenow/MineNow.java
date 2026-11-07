package com.sheepfromheaven.minenow;

import com.sheepfromheaven.minenow.farmer.FarmerRules;
import com.sheepfromheaven.minenow.farmer.FarmerTests;
import com.sheepfromheaven.minenow.farmer.FarmerTrades;
import com.sheepfromheaven.minenow.farmer.FarmerTradeTests;
import com.sheepfromheaven.minenow.villager.BrainHook;
import com.sheepfromheaven.minenow.villager.HomeStorage;
import com.sheepfromheaven.minenow.villager.HomeStorageTests;
import com.sheepfromheaven.minenow.villager.ModVillagers;
import com.sheepfromheaven.minenow.villager.StorageTheft;
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
        NeoForge.EVENT_BUS.addListener(BrainHook::onEntityTick);
        NeoForge.EVENT_BUS.addListener(FarmerRules::onMobGriefing);
        NeoForge.EVENT_BUS.addListener(FarmerTrades::onEntityInteract);
        NeoForge.EVENT_BUS.addListener(FarmerTrades::onTrade);
        NeoForge.EVENT_BUS.addListener(TradeTable::onAddReloadListeners);
        NeoForge.EVENT_BUS.addListener(VillagerNames::onAddReloadListeners);
        NeoForge.EVENT_BUS.addListener(VillagerNames::onEntityJoin);
        NeoForge.EVENT_BUS.addListener(HomeStorage::onEntityTick);
        NeoForge.EVENT_BUS.addListener(HomeStorage::onDeath);
        NeoForge.EVENT_BUS.addListener(HomeStorage::onConversion);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, HomeStorage::onBreak);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, StorageTheft::onPlace);
        NeoForge.EVENT_BUS.addListener(StorageTheft::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(StorageTheft::onEntityJoin);

        modBus.addListener(RegisterEvent.class, event -> event.register(Registries.TEST_FUNCTION, helper -> {
            test(helper, "farmer_harvests_and_replants_field_beyond_vanilla_reach", FarmerTests::farmerHarvestsAndReplantsFieldBeyondVanillaReach);
            test(helper, "farmer_keeps_tending_after_brain_is_rebuilt", FarmerTests::farmerKeepsTendingAfterBrainIsRebuilt);
            test(helper, "farmer_deposits_harvest_in_own_chest_at_home", FarmerTests::farmerDepositsHarvestInOwnChestAtHome);
            test(helper, "farmer_without_hoe_leaves_ripe_crops_alone", FarmerTests::farmerWithoutHoeLeavesRipeCropsAlone);
            test(helper, "harvesting_wears_out_the_hoe", FarmerTests::harvestingWearsOutTheHoe);
            test(helper, "farmer_picks_up_a_hoe_thrown_to_it", FarmerTests::farmerPicksUpAHoeThrownToIt);
            test(helper, "farmer_fetches_a_spare_hoe_from_its_home_chest", FarmerTests::farmerFetchesASpareHoeFromItsHomeChest);
            test(helper, "hoeless_farmer_offers_to_buy_an_undamaged_hoe", FarmerTradeTests::hoelessFarmerOffersToBuyAnUndamagedHoe);
            test(helper, "starting_purse_is_granted_only_once", FarmerTradeTests::startingPurseIsGrantedOnlyOnce);
            test(helper, "selling_a_hoe_to_a_farmer_moves_real_items", FarmerTradeTests::sellingAHoeToAFarmerMovesRealItems);
            test(helper, "farmer_sells_crops_from_its_inventory_and_chest", FarmerTradeTests::farmerSellsCropsFromItsInventoryAndChest);
            test(helper, "farmer_with_nothing_to_trade_shakes_its_head", FarmerTradeTests::farmerWithNothingToTradeShakesItsHead);
            test(helper, "novice_farmer_only_buys_wooden_hoes", FarmerTradeTests::noviceFarmerOnlyBuysWoodenHoes);
            test(helper, "journeyman_farmer_buys_up_to_iron_hoes", FarmerTradeTests::journeymanFarmerBuysUpToIronHoes);
            test(helper, "farmer_keeps_the_enchantments_of_a_hoe_it_buys", FarmerTradeTests::farmerKeepsTheEnchantmentsOfAHoeItBuys);
            test(helper, "villager_is_given_a_name_when_it_spawns", VillagerNamesTests::villagerIsGivenANameWhenItSpawns);
            test(helper, "a_name_from_a_name_tag_is_kept", VillagerNamesTests::aNameFromANameTagIsKept);
            test(helper, "villager_claims_and_locks_chest_near_its_bed", HomeStorageTests::villagerClaimsAndLocksChestNearItsBed);
            test(helper, "two_villagers_do_not_share_one_chest", HomeStorageTests::twoVillagersDoNotShareOneChest);
            test(helper, "breaking_a_claimed_chest_is_theft", HomeStorageTests::breakingAClaimedChestIsTheft);
            test(helper, "owner_dying_releases_and_unlocks_its_chest", HomeStorageTests::ownerDyingReleasesAndUnlocksItsChest);
            test(helper, "placing_a_chest_next_to_an_owned_chest_extends_the_claim", HomeStorageTests::placingAChestNextToAnOwnedChestExtendsTheClaim);
            test(helper, "breaking_one_half_of_an_owned_double_chest_keeps_the_other_half", HomeStorageTests::breakingOneHalfOfAnOwnedDoubleChestKeepsTheOtherHalf);
            test(helper, "hopper_draining_an_owned_chest_is_theft_by_its_placer", HomeStorageTests::hopperDrainingAnOwnedChestIsTheftByItsPlacer);
            test(helper, "hopper_minecart_draining_an_owned_chest_is_theft_by_its_placer", HomeStorageTests::hopperMinecartDrainingAnOwnedChestIsTheftByItsPlacer);
            test(helper, "villager_does_not_claim_a_chest_above_a_hopper", HomeStorageTests::villagerDoesNotClaimAChestAboveAHopper);
        }));
    }

    private static void test(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> helper, String name, Consumer<GameTestHelper> test) {
        helper.register(Identifier.fromNamespaceAndPath(MOD_ID, name), test);
    }
}
