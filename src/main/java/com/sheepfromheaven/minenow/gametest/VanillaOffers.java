package com.sheepfromheaven.minenow.gametest;

import com.sheepfromheaven.minenow.villager.ProfessionTrades;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;

import java.util.List;

/** What a test of a switched-off profession checks about its trades: vanilla's, which cost emeralds, the same each time. */
public final class VanillaOffers {
    private VanillaOffers() {}

    public static List<String> assertVanilla(GameTestHelper helper, Villager villager) {
        List<MerchantOffer> offers = ProfessionTrades.currentOffers(helper.getLevel(), villager);
        helper.assertTrue(!offers.isEmpty(), "Expected vanilla's trades from a switched-off " + villager.getVillagerData().profession().getRegisteredName());
        for (MerchantOffer offer : offers) {
            helper.assertTrue(offer.getBaseCostA().is(Items.EMERALD) || offer.getResult().is(Items.EMERALD),
                    "Expected a vanilla trade that costs or pays emeralds, got " + offer.getBaseCostA().getItem() + " for " + offer.getResult().getItem());
        }
        return offers.stream().map(offer -> offer.getBaseCostA().getCount() + " " + offer.getBaseCostA().getItem() + " -> "
                + offer.getResult().getCount() + " " + offer.getResult().getItem()).toList();
    }
}
