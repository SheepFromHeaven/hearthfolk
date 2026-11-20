package com.sheepfromheaven.minenow.gametest;

import com.sheepfromheaven.minenow.MineNowConfig;
import com.sheepfromheaven.minenow.MineNowConfig.Profession;
import com.sheepfromheaven.minenow.villager.ProfessionTrades;
import com.sheepfromheaven.minenow.villager.TradeTable;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Items;

/** What every switched-off profession's batch shows alike: vanilla's trades, which cost emeralds, and no Hearthfolk trade table. */
public final class SwitchedTrades {
    private SwitchedTrades() {}

    public static void assertVanillaTrades(GameTestHelper helper, Profession profession, Villager villager) {
        helper.assertTrue(!MineNowConfig.professionEnabled(profession), "The environment switches " + profession.getSerializedName() + " off");
        helper.assertTrue(TradeTable.forVillager(villager).isEmpty(), "A switched-off villager has no Hearthfolk trade table");
        var offers = ProfessionTrades.currentOffers(helper.getLevel(), villager);
        helper.assertTrue(!offers.isEmpty(), "Expected vanilla's trades");
        helper.assertTrue(offers.stream().anyMatch(offer -> offer.getBaseCostA().is(Items.EMERALD) || offer.getResult().is(Items.EMERALD)),
                "Expected vanilla trades paid in emeralds, got " + offers.stream().map(offer -> offer.getBaseCostA() + " -> " + offer.getResult()).toList());
    }
}
