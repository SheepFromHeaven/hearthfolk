package com.sheepfromheaven.minenow.fisherman;

import com.sheepfromheaven.minenow.MineNowConfig;
import com.sheepfromheaven.minenow.MineNowConfig.Profession;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** A fisherman switched off casts no bobber and shows vanilla's trades; run in the {@code profession_fisherman} environment. */
public final class SwitchedFishermanTests {
    private SwitchedFishermanTests() {}

    public static void aSwitchedOffFishermanCastsNoBobberAtTheWaterByHisBarrel(GameTestHelper helper) {
        helper.assertTrue(!MineNowConfig.professionEnabled(Profession.FISHERMAN), "The fisherman's environment switches the fisherman off");
        Villager fisherman = FishermanTests.spawnFisherman(helper);
        FishermanTests.digPool(helper, 7, 10);
        fisherman.getInventory().addItem(new ItemStack(Items.FISHING_ROD));
        boolean[] cast = {false};
        helper.onEachTick(() -> {
            cast[0] |= !helper.getLevel().getEntitiesOfClass(VillagerBobber.class, helper.getBounds()).isEmpty();
            cast[0] |= fisherman.getBrain().getRunningBehaviors().stream().anyMatch(GoFishing.class::isInstance);
        });
        helper.runAfterDelay(900, () -> {
            helper.assertTrue(!cast[0], "A switched-off fisherman casts no bobber and runs no GoFishing");
            helper.assertTrue(fisherman.getInventory().countItem(Items.FISHING_ROD) == 1 && fisherman.getInventory().countItem(Items.COD) == 0, "...and catches nothing");
            helper.succeed();
        });
    }

    public static void aSwitchedOffFishermanShowsVanillaTradesThatCostEmeralds(GameTestHelper helper) {
        helper.assertTrue(!MineNowConfig.professionEnabled(Profession.FISHERMAN), "The fisherman's environment switches the fisherman off");
        Villager fisherman = FishermanTests.spawnFisherman(helper);
        var offers = com.sheepfromheaven.minenow.villager.ProfessionTrades.currentOffers(helper.getLevel(), fisherman);
        helper.assertTrue(offers.stream().anyMatch(offer -> offer.getBaseCostA().is(Items.EMERALD) || offer.getResult().is(Items.EMERALD)), "Expected a vanilla trade in emeralds, got " + offers.size() + " offers");
        helper.assertTrue(offers.stream().noneMatch(offer -> offer.getBaseCostA().is(Items.FISHING_ROD)), "Hearthfolk's buying of a rod is gone");
        helper.succeed();
    }
}
