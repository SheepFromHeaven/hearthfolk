package com.sheepfromheaven.minenow.villager;

import net.minecraft.core.component.DataComponentExactPredicate;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;


/** Offers of any villager whose profession has a {@link TradeTable}, rebuilt from its needs, goods, level and that table every time a player opens trading; vanilla's fixed trade sets are dropped. */
public final class ProfessionTrades {
    private static final int XP_PER_TRADE = 2;
    private static final float SELL_PRICE_MULTIPLIER = 0.05F;

    private ProfessionTrades() {}

    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || event.getEntity().isSecondaryUseActive()) return;
        if (!(event.getTarget() instanceof Villager villager) || !(villager.level() instanceof ServerLevel level)) return;
        if (TradeTable.forVillager(villager).isEmpty() || villager.isBaby() || villager.isTrading()) return;
        Economy.grantStartingPurseOnce(level, villager);
        rebuildOffers(level, villager);
    }

    public static void onTrade(TradeWithVillagerEvent event) {
        if (!(event.getAbstractVillager() instanceof Villager villager) || !(villager.level() instanceof ServerLevel level)) return;
        if (TradeTable.forVillager(villager).isEmpty()) return;
        MerchantOffer offer = event.getMerchantOffer();
        ItemStack result = offer.getResult();
        boolean boughtSupply = result.is(Items.EMERALD) && TradeTable.forVillager(villager)
                .map(table -> !table.isTool(offer.getCostA().getItem())).orElse(false);
        if (boughtSupply) {
            Economy.take(level, villager, Items.EMERALD, result.getCount());
            Economy.give(level, villager, new ItemStack(offer.getCostA().getItem(), offer.getCostA().getCount()));
        } else if (result.is(Items.EMERALD)) {
            Economy.take(level, villager, Items.EMERALD, result.getCount());
            Economy.give(level, villager, paidStack(event, offer));
            TradeTable table = TradeTable.forVillager(villager).orElse(null);
            villager.getOffers().stream()
                    .filter(other -> other.getResult().is(Items.EMERALD) && table != null && table.isTool(other.getCostA().getItem()))
                    .forEach(MerchantOffer::setToOutOfStock);
            if (event.getEntity().containerMenu instanceof MerchantMenu menu) {
                event.getEntity().sendMerchantOffers(menu.containerId, villager.getOffers(), villager.getVillagerData().level(),
                        villager.getVillagerXp(), villager.showProgressBar(), villager.canRestock());
            }
        } else {
            Economy.take(level, villager, result.getItem(), result.getCount());
            Economy.give(level, villager, new ItemStack(Items.EMERALD, offer.getCostA().getCount()));
        }
    }

    /**
     * The payment slot is shrunk to 0 before the trade event fires, which hides its components but keeps the
     * object, so briefly restoring the count recovers the exact item (enchantments included).
     */
    private static ItemStack paidStack(TradeWithVillagerEvent event, MerchantOffer offer) {
        if (event.getEntity().containerMenu instanceof MerchantMenu menu) {
            for (int slot = 0; slot < 2; slot++) {
                ItemStack payment = menu.getSlot(slot).getItem();
                if (payment == ItemStack.EMPTY || payment.getCount() != 0) continue;
                payment.setCount(1);
                ItemStack exact = payment.is(offer.getCostA().getItem()) ? payment.copy() : ItemStack.EMPTY;
                payment.setCount(0);
                if (!exact.isEmpty()) return exact;
            }
        }
        return new ItemStack(offer.getCostA().getItem());
    }

    static void rebuildOffers(ServerLevel level, Villager villager) {
        MerchantOffers offers = villager.getOffers();
        offers.clear();
        TradeTable table = TradeTable.forVillager(villager).orElse(null);
        if (table == null) return;
        int villagerLevel = villager.getVillagerData().level();
        if (!Unloading.ownsTool(level, villager)) {
            int wallet = Economy.emeralds(level, villager);
            for (TradeTable.Tool tool : table.toolsUpTo(villagerLevel)) {
                if (tool.price() > wallet) continue;
                ItemCost undamaged = new ItemCost(tool.item().builtInRegistryHolder(), 1, DataComponentExactPredicate.expect(DataComponents.DAMAGE, 0));
                offers.add(new MerchantOffer(undamaged, new ItemStack(Items.EMERALD, tool.price()), 1, XP_PER_TRADE, 0.0F));
            }
        }
        for (TradeTable.Craft craft : table.craftsUpTo(villagerLevel)) {
            int inStock = Economy.count(level, villager, craft.item());
            if (inStock > 0) {
                offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, craft.price()), new ItemStack(craft.item()), inStock, XP_PER_TRADE, SELL_PRICE_MULTIPLIER));
            }
        }
        int purse = Economy.emeralds(level, villager);
        for (TradeTable.Supply supply : table.suppliesUpTo(villagerLevel)) {
            int room = (supply.max() - Economy.count(level, villager, supply.item())) / supply.count();
            int trades = Math.min(room, purse / supply.price());
            if (trades > 0) {
                offers.add(new MerchantOffer(new ItemCost(supply.item(), supply.count()), new ItemStack(Items.EMERALD, supply.price()), trades, XP_PER_TRADE, 0.0F));
            }
        }
        table.defaultProduce().filter(fallback -> fallback.level() <= villagerLevel).ifPresent(fallback -> {
            for (var item : Economy.items(level, villager)) {
                if (table.hasProduceEntry(item) || !fallback.items().contains(item.builtInRegistryHolder())) continue;
                int trades = (Economy.count(level, villager, item) - Unloading.keepLimit(villager, new ItemStack(item))) / fallback.perEmerald();
                if (trades > 0) {
                    offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(item, fallback.perEmerald()), trades, XP_PER_TRADE, SELL_PRICE_MULTIPLIER));
                }
            }
        });
        for (TradeTable.Produce crop : table.produceUpTo(villagerLevel)) {
            int sellable = Economy.count(level, villager, crop.item()) - Unloading.keepLimit(villager, new ItemStack(crop.item()));
            int trades = sellable / crop.perEmerald();
            if (trades > 0) {
                offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(crop.item(), crop.perEmerald()), trades, XP_PER_TRADE, SELL_PRICE_MULTIPLIER));
            }
        }
    }
}
