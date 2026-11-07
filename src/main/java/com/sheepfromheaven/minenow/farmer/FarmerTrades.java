package com.sheepfromheaven.minenow.farmer;

import com.sheepfromheaven.minenow.villager.Economy;
import com.sheepfromheaven.minenow.villager.TradeTable;
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


/** Farmer offers are rebuilt from its needs, goods, level and its {@link TradeTable} every time a player opens trading; vanilla's fixed trade sets are dropped. */
public final class FarmerTrades {
    private static final int XP_PER_TRADE = 2;
    private static final float SELL_PRICE_MULTIPLIER = 0.05F;

    private FarmerTrades() {}

    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || event.getEntity().isSecondaryUseActive()) return;
        if (!(event.getTarget() instanceof Villager farmer) || !(farmer.level() instanceof ServerLevel level)) return;
        if (!FarmerRules.isFarmer(farmer) || farmer.isBaby() || farmer.isTrading()) return;
        Economy.grantStartingPurseOnce(level, farmer);
        rebuildOffers(level, farmer);
    }

    public static void onTrade(TradeWithVillagerEvent event) {
        if (!(event.getAbstractVillager() instanceof Villager farmer) || !(farmer.level() instanceof ServerLevel level)) return;
        if (!FarmerRules.isFarmer(farmer)) return;
        MerchantOffer offer = event.getMerchantOffer();
        ItemStack result = offer.getResult();
        if (result.is(Items.EMERALD)) {
            Economy.take(level, farmer, Items.EMERALD, result.getCount());
            Economy.give(level, farmer, paidStack(event, offer));
            farmer.getOffers().stream().filter(other -> other.getResult().is(Items.EMERALD)).forEach(MerchantOffer::setToOutOfStock);
            if (event.getEntity().containerMenu instanceof MerchantMenu menu) {
                event.getEntity().sendMerchantOffers(menu.containerId, farmer.getOffers(), farmer.getVillagerData().level(),
                        farmer.getVillagerXp(), farmer.showProgressBar(), farmer.canRestock());
            }
        } else {
            Economy.take(level, farmer, result.getItem(), result.getCount());
            Economy.give(level, farmer, new ItemStack(Items.EMERALD, offer.getCostA().getCount()));
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

    static void rebuildOffers(ServerLevel level, Villager farmer) {
        MerchantOffers offers = farmer.getOffers();
        offers.clear();
        TradeTable table = TradeTable.forVillager(farmer).orElse(null);
        if (table == null) return;
        int villagerLevel = farmer.getVillagerData().level();
        if (!Hoes.has(farmer) && !TendFields.homeChestHasHoe(level, farmer)) {
            int wallet = Economy.emeralds(level, farmer);
            for (TradeTable.Tool tool : table.toolsUpTo(villagerLevel)) {
                if (tool.price() > wallet) continue;
                ItemCost undamaged = new ItemCost(tool.item().builtInRegistryHolder(), 1, DataComponentExactPredicate.expect(DataComponents.DAMAGE, 0));
                offers.add(new MerchantOffer(undamaged, new ItemStack(Items.EMERALD, tool.price()), 1, XP_PER_TRADE, 0.0F));
            }
        }
        for (TradeTable.Produce crop : table.produceUpTo(villagerLevel)) {
            int sellable = Economy.count(level, farmer, crop.item()) - TendFields.keepLimit(new ItemStack(crop.item()));
            int trades = sellable / crop.perEmerald();
            if (trades > 0) {
                offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(crop.item(), crop.perEmerald()), trades, XP_PER_TRADE, SELL_PRICE_MULTIPLIER));
            }
        }
    }
}
