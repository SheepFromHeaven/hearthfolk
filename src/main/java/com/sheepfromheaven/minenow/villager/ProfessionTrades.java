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


/** Offers of any villager whose profession has a {@link TradeTable}, rebuilt from its needs, goods, level and that table every time a player opens trading (it never pays below {@link Economy#reserve}); vanilla's fixed trade sets are dropped, and kept aside for while the profession is switched off ({@link VanillaTrades}). */
public final class ProfessionTrades {
    private static final int XP_PER_TRADE = 2;
    private static final float SELL_PRICE_MULTIPLIER = 0.05F;

    private ProfessionTrades() {}

    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || event.getEntity().isSecondaryUseActive()) return;
        if (!(event.getTarget() instanceof Villager villager) || !(villager.level() instanceof ServerLevel level)) return;
        if (villager.isBaby() || villager.isTrading()) return;
        prepareOffers(level, villager);
    }

    /**
     * Readies the offers a player opening trading sees: Hearthfolk's, rebuilt; or with the profession switched off, its
     * vanilla trades, rolled once ({@link VanillaTrades}). A profession without a trade file is left to vanilla.
     */
    private static void prepareOffers(ServerLevel level, Villager villager) {
        if (!TradeTable.hasTradeFile(villager)) return;
        if (TradeTable.forVillager(villager).isEmpty()) VanillaTrades.show(level, villager);
        else rebuildOffers(level, villager);
    }

    public static void onTrade(TradeWithVillagerEvent event) {
        if (!(event.getAbstractVillager() instanceof Villager villager) || !(villager.level() instanceof ServerLevel level)) return;
        if (TradeTable.forVillager(villager).isEmpty()) return;
        MerchantOffer offer = event.getMerchantOffer();
        ItemStack result = offer.getResult();
        boolean boughtSupply = result.is(Items.EMERALD) && TradeTable.forVillager(villager)
                .map(table -> !table.isTool(offer.getCostA().getItem())).orElse(false);
        boolean tracked = Economy.emeraldEconomy();
        if (boughtSupply) {
            if (tracked) Economy.take(level, villager, Items.EMERALD, result.getCount());
            Economy.give(level, villager, new ItemStack(offer.getCostA().getItem(), offer.getCostA().getCount()));
            capSupplyOffers(level, villager);
            resendOffers(event, villager);
        } else if (result.is(Items.EMERALD)) {
            if (tracked) Economy.take(level, villager, Items.EMERALD, result.getCount());
            Economy.give(level, villager, paidStack(event, offer));
            TradeTable table = TradeTable.forVillager(villager).orElse(null);
            villager.getOffers().stream()
                    .filter(other -> other.getResult().is(Items.EMERALD) && table != null && table.isTool(other.getCostA().getItem()))
                    .forEach(MerchantOffer::setToOutOfStock);
            resendOffers(event, villager);
        } else {
            if (result.getComponentsPatch().isEmpty()) Economy.take(level, villager, result.getItem(), result.getCount());
            else Economy.takeExact(level, villager, result);
            if (tracked) Economy.give(level, villager, new ItemStack(Items.EMERALD, offer.getCostA().getCount()));
        }
    }

    /** Every supply offer shares the one purse, so after a purchase none may offer more than what is left above the reserve. */
    private static void capSupplyOffers(ServerLevel level, Villager villager) {
        TradeTable table = TradeTable.forVillager(villager).orElse(null);
        if (table == null) return;
        for (MerchantOffer offer : villager.getOffers()) {
            if (!offer.getResult().is(Items.EMERALD) || table.isTool(offer.getCostA().getItem())) continue;
            int spendable = table.suppliesUpTo(villager.getVillagerData().level()).stream()
                    .filter(supply -> supply.item() == offer.getCostA().getItem()).findFirst()
                    .map(supply -> BuyFromVillagers.budgetFor(level, villager, supply)).orElse(Economy.villagerBudget(Economy.spendable(level, villager)));
            int affordable = spendable / offer.getResult().getCount();
            while (offer.getMaxUses() - offer.getUses() > affordable) offer.increaseUses();
        }
    }

    private static void resendOffers(TradeWithVillagerEvent event, Villager villager) {
        if (event.getEntity().containerMenu instanceof MerchantMenu menu) {
            event.getEntity().sendMerchantOffers(menu.containerId, villager.getOffers(), villager.getVillagerData().level(),
                    villager.getVillagerXp(), villager.showProgressBar(), villager.canRestock());
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

    /** The offers a player would see on opening this villager's trades, rebuilt from its trade file and goods. */
    public static java.util.List<MerchantOffer> currentOffers(ServerLevel level, Villager villager) {
        prepareOffers(level, villager);
        return villager.getOffers();
    }

    static void rebuildOffers(ServerLevel level, Villager villager) {
        VanillaTrades.putAway(villager);
        MerchantOffers offers = villager.getOffers();
        offers.clear();
        TradeTable table = TradeTable.forVillager(villager).orElse(null);
        if (table == null) return;
        int villagerLevel = villager.getVillagerData().level();
        if (!Unloading.ownsTool(level, villager)) {
            int wallet = Economy.villagerBudget(Economy.emeralds(level, villager) - Economy.FOOD_MONEY);
            for (TradeTable.Tool tool : table.toolsUpTo(villagerLevel)) {
                if (tool.price() > wallet) continue;
                ItemCost undamaged = new ItemCost(tool.item().builtInRegistryHolder(), 1, DataComponentExactPredicate.expect(DataComponents.DAMAGE, 0));
                offers.add(new MerchantOffer(undamaged, new ItemStack(Items.EMERALD, tool.price()), 1, XP_PER_TRADE, 0.0F));
            }
        }
        for (TradeTable.Craft craft : table.craftsUpTo(villagerLevel)) {
            if (table.hasProduceEntry(craft.item())) continue;
            Price price = table.craftPrice(craft);
            if (craft.enchants()) {
                for (ItemStack book : Economy.stacks(level, villager, craft.item())) {
                    offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, price.emeralds()), book, 1, XP_PER_TRADE, SELL_PRICE_MULTIPLIER));
                }
                continue;
            }
            int spare = Economy.count(level, villager, craft.item()) - (table.isToolItem(new ItemStack(craft.item())) ? 1 : 0);
            int batches = spare / price.count();
            if (batches > 0) {
                ItemStack sold = craft.item() == Items.FILLED_MAP ? Economy.peekOne(level, villager, craft.item()) : new ItemStack(craft.item(), price.count());
                offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, price.emeralds()), sold, batches, XP_PER_TRADE, SELL_PRICE_MULTIPLIER));
            }
        }
        for (TradeTable.Supply supply : table.suppliesWanted(level, villager)) {
            int purse = BuyFromVillagers.budgetFor(level, villager, supply);
            int room = (supply.max() - Economy.count(level, villager, supply.item())) / supply.price().count();
            int trades = Math.min(room, purse / supply.price().emeralds());
            if (trades > 0) {
                offers.add(new MerchantOffer(new ItemCost(supply.item(), supply.price().count()), new ItemStack(Items.EMERALD, supply.price().emeralds()), trades, XP_PER_TRADE, 0.0F));
            }
        }
        table.defaultProduce().filter(fallback -> fallback.level() <= villagerLevel).ifPresent(fallback -> {
            for (var item : Economy.items(level, villager)) {
                if (table.hasProduceEntry(item) || table.craft(item).isPresent() || !fallback.items().contains(item.builtInRegistryHolder())) continue;
                int trades = (Economy.count(level, villager, item) - Unloading.keepLimit(villager, new ItemStack(item))) / fallback.price().count();
                if (trades > 0) {
                    offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, fallback.price().emeralds()), new ItemStack(item, fallback.price().count()), trades, XP_PER_TRADE, SELL_PRICE_MULTIPLIER));
                }
            }
        });
        if (villager.getVillagerData().profession().is(net.minecraft.world.entity.npc.villager.VillagerProfession.CLERIC)) com.sheepfromheaven.minenow.cleric.ClericOffers.add(level, villager, offers);
        for (TradeTable.Produce crop : table.produceUpTo(villagerLevel)) {
            int sellable = Economy.count(level, villager, crop.item()) - Unloading.keepLimit(villager, new ItemStack(crop.item()));
            int trades = sellable / crop.price().count();
            if (trades > 0) {
                offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, crop.price().emeralds()), new ItemStack(crop.item(), crop.price().count()),
                        trades, XP_PER_TRADE, SELL_PRICE_MULTIPLIER));
            }
        }
    }
}
