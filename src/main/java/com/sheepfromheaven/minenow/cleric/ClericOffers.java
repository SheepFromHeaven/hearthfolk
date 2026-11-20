package com.sheepfromheaven.minenow.cleric;

import com.sheepfromheaven.minenow.MineNowConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;

import java.util.List;

/** What a cleric offers a player besides its trade file's goods: each potion it brews and has in stock, as that exact potion (the offer's result carries its components) at its own price. */
public final class ClericOffers {
    private static final int XP_PER_TRADE = 2;
    private static final float SELL_PRICE_MULTIPLIER = 0.05F;

    private ClericOffers() {}

    public static void add(ServerLevel level, Villager cleric, List<MerchantOffer> offers) {
        if (!MineNowConfig.professionEnabled(cleric.getVillagerData().profession())) return;
        for (Brews.Brew brew : Brews.upTo(cleric.getVillagerData().level())) {
            if (brew.price().isEmpty()) continue;
            int stock = PotionGoods.count(level, cleric, brew.output().form(), brew.output().potion());
            if (stock > 0) {
                offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, brew.price().get()), brew.output().stack(), stock, XP_PER_TRADE, SELL_PRICE_MULTIPLIER));
            }
        }
    }
}
