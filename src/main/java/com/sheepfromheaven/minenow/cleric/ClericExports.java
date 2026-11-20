package com.sheepfromheaven.minenow.cleric;

import com.sheepfromheaven.minenow.MineNowConfig;
import com.sheepfromheaven.minenow.villager.Economy;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Potions a travelling merchant buys: {@code #minenow:exports} is a list of items and every potion is one item, so the
 * cleric's potions are exported here by brew instead, each at its own price. It keeps {@link #KEEP} of each kind to sell
 * to players and brews more.
 */
public final class ClericExports {
    static final int KEEP = 1;

    private ClericExports() {}

    private static boolean isCleric(Villager villager) {
        return villager.getVillagerData().profession().is(VillagerProfession.CLERIC) && MineNowConfig.professionEnabled(villager.getVillagerData().profession());
    }

    private static int sellable(ServerLevel level, Villager cleric, Brews.Brew brew) {
        if (brew.price().isEmpty()) return 0;
        return Math.max(0, PotionGoods.count(level, cleric, brew.output().form(), brew.output().potion()) - KEEP);
    }

    /** What the merchant would pay for the potions it could take, in emeralds. */
    public static int surplus(ServerLevel level, Villager villager) {
        if (!isCleric(villager)) return 0;
        int worth = 0;
        for (Brews.Brew brew : Brews.upTo(villager.getVillagerData().level())) {
            worth += sellable(level, villager, brew) * brew.price().orElse(0);
        }
        return worth;
    }

    /** Sells the merchant what it can spend of {@code budget} emeralds on, one exact potion at a time; returns the emeralds paid. */
    public static int sell(ServerLevel level, Villager villager, int budget) {
        if (!isCleric(villager)) return 0;
        int spent = 0;
        for (Brews.Brew brew : Brews.upTo(villager.getVillagerData().level())) {
            int price = brew.price().orElse(0);
            int potions = price == 0 ? 0 : Math.min(sellable(level, villager, brew), (budget - spent) / price);
            for (int i = 0; i < potions; i++) {
                if (PotionGoods.takeOne(level, villager, brew.output().form(), brew.output().potion()).isEmpty()) break;
                Economy.give(level, villager, new ItemStack(Items.EMERALD, price));
                spent += price;
            }
        }
        return spent;
    }
}
