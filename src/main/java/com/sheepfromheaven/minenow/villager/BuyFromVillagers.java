package com.sheepfromheaven.minenow.villager;

import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.Optional;

/**
 * A hungry villager without a meal ({@link Meals}), a wealthy one without food to breed ({@link Wealth}), or a working
 * one that has no tool of its trade (the best one a crafting villager has in stock that it can afford) or runs low on a
 * supply of its trade (fewer than one batch in its inventory), walks to the nearest villager that sells what it needs
 * and buys it at that villager's price, the same price a player pays. Supplies come in as many emeralds' worth as it
 * can afford, the seller can spare and still fit under the supply's {@code max}.
 */
public class BuyFromVillagers extends Behavior<Villager> {
    static final int SEARCH_RANGE = 48;
    private static final float SPEED = 0.5F;
    private static final double REACH = 2.5;
    private static final int GIVE_UP_AFTER_TICKS = 600;
    private static final int RETRY_DELAY = 400;

    /** Buys lots of the seller's {@code price} (so many items for so many emeralds), up to {@code room} items. */
    /** {@code budget}: the emeralds the buyer may spend on it (food may use its food money, supplies only what's above its reserve). */
    /** {@code wear}: gear it puts on (see {@link Gear}) rather than goods it keeps. */
    private record Deal(Villager seller, Item item, Price price, int room, int budget, boolean wear) {
        Deal(Villager seller, Item item, Price price, int room, int budget) {
            this(seller, item, price, room, budget, false);
        }
    }

    private @Nullable Deal deal;
    private long startedAt;
    private long nextStartAllowed;

    public BuyFromVillagers() {
        super(ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT), GIVE_UP_AFTER_TICKS);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager buyer) {
        if (level.getGameTime() < nextStartAllowed || buyer.isBaby() || buyer.isTrading() || buyer.isSleeping()) return false;
        nextStartAllowed = level.getGameTime() + RETRY_DELAY;
        deal = findDeal(level, buyer).orElse(null);
        return deal != null;
    }

    @Override
    protected void start(ServerLevel level, Villager buyer, long timestamp) {
        startedAt = timestamp;
        walk(buyer);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager buyer, long timestamp) {
        return deal != null && deal.seller().isAlive() && !buyer.isTrading() && timestamp - startedAt < GIVE_UP_AFTER_TICKS;
    }

    @Override
    protected void tick(ServerLevel level, Villager buyer, long timestamp) {
        if (buyer.distanceTo(deal.seller()) <= REACH) {
            trade(level, buyer, deal);
            deal = null;
            nextStartAllowed = timestamp;
            return;
        }
        if (!walkingToSeller(buyer)) walk(buyer);
    }

    private boolean walkingToSeller(Villager buyer) {
        return buyer.getBrain().getMemory(MemoryModuleType.WALK_TARGET)
                .filter(walk -> walk.getTarget() instanceof EntityTracker tracker && tracker.getEntity() == deal.seller())
                .isPresent();
    }

    @Override
    protected void stop(ServerLevel level, Villager buyer, long timestamp) {
        deal = null;
        buyer.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        buyer.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
    }

    private static void trade(ServerLevel level, Villager buyer, Deal deal) {
        Villager seller = deal.seller();
        Price price = deal.price();
        int lots = Math.min(Math.min(deal.budget(), Economy.emeralds(level, buyer)) / price.emeralds(),
                Math.min(sellable(level, seller, deal.item()) / price.count(), Math.max(1, deal.room() / price.count())));
        if (lots <= 0) return;
        int paid = Economy.take(level, buyer, Items.EMERALD, lots * price.emeralds());
        Economy.give(level, seller, new ItemStack(Items.EMERALD, paid));
        if (deal.wear()) {
            if (deal.item() == Items.ARROW) Gear.wear(buyer, new ItemStack(Items.ARROW, Economy.take(level, seller, Items.ARROW, lots * price.count())));
            else Gear.wear(buyer, Economy.takeOne(level, seller, deal.item()));
        } else {
            int handed = Economy.take(level, seller, deal.item(), lots * price.count());
            Economy.give(level, buyer, new ItemStack(deal.item(), handed));
        }
        buyer.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(seller, true));
        seller.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(buyer, true));
        buyer.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
        seller.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
        level.playSound(null, seller.blockPosition(), SoundEvents.VILLAGER_TRADE, SoundSource.NEUTRAL, 1.0F, seller.getVoicePitch());
    }

    private static Optional<Deal> findDeal(ServerLevel level, Villager buyer) {
        if (Economy.emeralds(level, buyer) <= 0) return Optional.empty();
        if (Meals.isHungry(buyer) && !Meals.hasAMeal(level, buyer)) {
            Optional<Deal> meal = sellers(level, buyer).stream()
                    .flatMap(seller -> Economy.items(level, seller).stream()
                            .filter(item -> Meals.isMeal(new ItemStack(item)))
                            .flatMap(item -> offer(level, seller, item, 0, Economy.emeralds(level, buyer)).stream()))
                    .findFirst();
            if (meal.isPresent()) return meal;
        }
        if (Wealth.wantsFoodForAFamily(level, buyer)) {
            Optional<Deal> food = sellers(level, buyer).stream()
                    .flatMap(seller -> Economy.items(level, seller).stream()
                            .filter(item -> Wealth.isBreedingFood(new ItemStack(item)))
                            .flatMap(item -> offer(level, seller, item, 0, Economy.spendable(level, buyer)).stream()))
                    .findFirst();
            if (food.isPresent()) return food;
        }
        Optional<TradeTable> table = TradeTable.forVillager(buyer);
        if (table.isEmpty() || !buyer.getBrain().isActive(Activity.WORK)) return gear(level, buyer);
        if (!Unloading.ownsTool(level, buyer) && (table.get().toolTag().isPresent() || !table.get().tools().isEmpty())) {
            int wallet = Economy.emeralds(level, buyer) - Economy.FOOD_MONEY;
            Optional<Deal> tool = sellers(level, buyer).stream()
                    .flatMap(seller -> TradeTable.forVillager(seller).stream()
                            .flatMap(sellerTable -> sellerTable.craftsUpTo(seller.getVillagerData().level()).stream()
                                    .filter(craft -> sellerTable.craftPrice(craft).emeralds() <= wallet && table.get().isToolItem(new ItemStack(craft.item()))
                                            && Economy.count(level, seller, craft.item()) > 0)
                                    .max(Comparator.comparingInt(craft -> sellerTable.craftPrice(craft).emeralds()))
                                    .map(craft -> new Deal(seller, craft.item(), sellerTable.craftPrice(craft), 1, wallet))
                                    .stream()))
                    .findFirst();
            if (tool.isPresent()) return tool;
        }
        for (TradeTable.Supply supply : table.get().suppliesUpTo(buyer.getVillagerData().level())) {
            int held = buyer.getInventory().countItem(supply.item());
            if (held >= supply.price().count()) continue;
            Optional<Deal> deal = sellers(level, buyer).stream()
                    .flatMap(seller -> offer(level, seller, supply.item(), supply.max() - held, Economy.spendable(level, buyer)).stream())
                    .findFirst();
            if (deal.isPresent()) return deal;
        }
        return gear(level, buyer);
    }

    /** The most protective armor (or, once fully armored, the best sword, or a bow and arrows for an archer) the nearest seller has that it can afford. */
    private static Optional<Deal> gear(ServerLevel level, Villager buyer) {
        int budget = Gear.budget(level, buyer);
        if (budget <= 0) return Optional.empty();
        return sellers(level, buyer).stream()
                .flatMap(seller -> Economy.items(level, seller).stream()
                        .filter(item -> Gear.wants(buyer, new ItemStack(item)))
                        .flatMap(item -> offer(level, seller, item, Gear.room(buyer, new ItemStack(item)), budget).stream())
                        .max(Comparator.comparingDouble(deal -> Gear.worth(new ItemStack(deal.item()))))
                        .map(deal -> new Deal(deal.seller(), deal.item(), deal.price(), deal.room(), budget, true))
                        .stream())
                .findFirst();
    }

    private static java.util.List<Villager> sellers(ServerLevel level, Villager buyer) {
        return level.getEntitiesOfClass(Villager.class, buyer.getBoundingBox().inflate(SEARCH_RANGE),
                        seller -> seller != buyer && seller.isAlive() && !seller.isBaby() && !seller.isTrading() && !seller.isSleeping())
                .stream()
                .sorted(Comparator.comparingDouble(buyer::distanceToSqr))
                .toList();
    }

    private static Optional<Deal> offer(ServerLevel level, Villager seller, Item item, int room, int budget) {
        Optional<Price> price = TradeTable.forVillager(seller).flatMap(table -> table.sellPrice(item, seller.getVillagerData().level()));
        if (price.isEmpty() || sellable(level, seller, item) < price.get().count() || budget < price.get().emeralds()) return Optional.empty();
        return Optional.of(new Deal(seller, item, price.get(), room, budget));
    }

    private static int sellable(ServerLevel level, Villager seller, Item item) {
        return Economy.count(level, seller, item) - Unloading.keepLimit(seller, new ItemStack(item));
    }

    private void walk(Villager buyer) {
        buyer.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new EntityTracker(deal.seller(), false), SPEED, 1));
        buyer.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(deal.seller(), true));
    }
}
