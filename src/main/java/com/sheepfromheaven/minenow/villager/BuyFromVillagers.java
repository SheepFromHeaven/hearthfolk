package com.sheepfromheaven.minenow.villager;

import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.item.trading.MerchantOffer;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.Optional;

/**
 * A hungry villager without a meal ({@link Meals}), a wealthy one without food to breed ({@link Wealth}), or a working
 * one that has no tool of its trade (the best one a crafting villager has in stock that it can afford) or runs low on a
 * supply of its trade (fewer than one batch in its inventory), walks to the nearest villager of its village that sells
 * what it needs, however far, and buys it at that villager's price, the same price a player pays. Supplies come in as many emeralds' worth as it
 * can afford, the seller can spare and still fit under the supply's {@code max}.
 */
public class BuyFromVillagers extends Behavior<Villager> {
    static final int SEARCH_RANGE = 48;
    private static final float SPEED = 0.5F;
    private static final double REACH = 2.5;
    private static final int GIVE_UP_AFTER_TICKS = 600;
    /** A seller across the village takes longer to reach: this many ticks more per block beyond {@link #NEARBY}, up to {@link #LONGEST_TRIP}. */
    private static final int TICKS_PER_BLOCK = 20;
    private static final int NEARBY = 24;
    private static final int LONGEST_TRIP = 4800;
    private static final int RETRY_DELAY = 400;

    /** Buys lots of the seller's {@code price} (so many items for so many emeralds), up to {@code room} items. */
    /** {@code budget}: the emeralds the buyer may spend on it (food may use its food money, supplies only what's above its reserve). */
    /** {@code wear}: gear it puts on (see {@link Gear}) rather than goods it keeps. */
    /** {@code credit}: one lot it takes now and pays for later (see {@link #CREDIT_LIMIT}). */
    /** {@code gift}: a meal it is given, {@code room} items of it, by a neighbour with far more than it could sell. */
    /** {@code upgrade}: a better tool of its trade, replacing its old one, which it throws away: nobody buys a worn tool. */
    private record Deal(Villager seller, Item item, Price price, int room, int budget, boolean wear, boolean credit, boolean gift, boolean upgrade) {
        Deal(Villager seller, Item item, Price price, int room, int budget) {
            this(seller, item, price, room, budget, false, false, false, false);
        }

        Deal(Villager seller, Item item, Price price, int room, int budget, boolean wear) {
            this(seller, item, price, room, budget, wear, false, false, false);
        }

        Deal(Villager seller, Item item, Price price, int room, int budget, boolean wear, boolean credit) {
            this(seller, item, price, room, budget, wear, credit, false, false);
        }
    }

    /**
     * A craftsman too broke to buy what its trade needs (a tool, a supply) may take one lot on credit from a seller with
     * a lot to spare, up to this many emeralds owed in all, and pays it back from its next earnings above its reserve. Without it a smelter with no ore or a butcher with no coal never earns the money to start.
     */
    static final int CREDIT_LIMIT = 3;

    /** A commission errand to {@code maker}: placing {@code order} (paying for it), collecting it, or asking for the money back. */
    private record Visit(Villager maker, Errand errand, Commissions.Order order) {}

    private enum Errand { ORDER, COLLECT, REFUND }

    /** Buys lots of {@code offer} (the merchant's own, so its stock goes down) up to {@code room} items for at most {@code budget} emeralds. */
    private record MerchantDeal(WanderingTrader merchant, Item item, MerchantOffer offer, int room, int budget) {}

    private @Nullable Deal deal;
    private @Nullable MerchantDeal merchantDeal;
    private @Nullable Visit visit;
    private long giveUpAt;
    private long nextStartAllowed;
    private long lastChecked = Long.MIN_VALUE;

    public BuyFromVillagers() {
        super(ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT), LONGEST_TRIP);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager buyer) {
        boolean announced = Exports.lastAnnounced > lastChecked;
        if ((level.getGameTime() < nextStartAllowed && !announced) || buyer.isBaby() || buyer.isTrading() || buyer.isSleeping() || pickingUp(buyer)) return false;
        lastChecked = level.getGameTime();
        nextStartAllowed = level.getGameTime() + RETRY_DELAY;
        repayDebts(level, buyer);
        merchantDeal = null;
        visit = commissionErrand(level, buyer).orElse(null);
        deal = visit != null ? null : findDeal(level, buyer).or(() -> creditDeal(level, buyer)).or(() -> mealGift(level, buyer)).orElse(null);
        if (visit == null && deal == null) merchantDeal = merchantDeal(level, buyer).orElse(null);
        if (visit == null && deal == null && merchantDeal == null) visit = newCommission(level, buyer).orElse(null);
        return deal != null || visit != null || merchantDeal != null;
    }

    private Entity partner() {
        return deal != null ? deal.seller() : visit != null ? visit.maker() : merchantDeal.merchant();
    }

    @Override
    protected void start(ServerLevel level, Villager buyer, long timestamp) {
        double distance = buyer.distanceTo(partner());
        giveUpAt = timestamp + Math.min(LONGEST_TRIP, GIVE_UP_AFTER_TICKS + (int) Math.max(0, distance - NEARBY) * TICKS_PER_BLOCK);
        walk(buyer);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager buyer, long timestamp) {
        return (deal != null || visit != null || merchantDeal != null) && partner().isAlive() && !buyer.isTrading() && timestamp < giveUpAt && !pickingUp(buyer);
    }

    /** Something it needs lies right here ({@link PickUpGifts}); that comes before a walk to a seller. */
    private static boolean pickingUp(Villager buyer) {
        return buyer.getBrain().getRunningBehaviors().stream().anyMatch(behavior -> behavior instanceof PickUpGifts);
    }

    @Override
    protected void tick(ServerLevel level, Villager buyer, long timestamp) {
        if (buyer.distanceTo(partner()) <= REACH && buyer.hasLineOfSight(partner())) {
            if (deal != null) trade(level, buyer, deal);
            else if (merchantDeal != null) tradeWithMerchant(level, buyer, merchantDeal);
            else run(level, buyer, visit);
            deal = null;
            visit = null;
            merchantDeal = null;
            nextStartAllowed = timestamp;
            return;
        }
        if (!walkingToSeller(buyer)) walk(buyer);
    }

    private boolean walkingToSeller(Villager buyer) {
        return buyer.getBrain().getMemory(MemoryModuleType.WALK_TARGET)
                .filter(walk -> walk.getTarget() instanceof EntityTracker tracker && tracker.getEntity() == partner())
                .isPresent();
    }

    @Override
    protected void stop(ServerLevel level, Villager buyer, long timestamp) {
        deal = null;
        visit = null;
        merchantDeal = null;
        buyer.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        buyer.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
    }

    private static void trade(ServerLevel level, Villager buyer, Deal deal) {
        Villager seller = deal.seller();
        if (deal.gift()) {
            if (Exports.bulk(level, seller, deal.item()) < deal.room()) return;
            Economy.give(level, buyer, new ItemStack(deal.item(), Economy.take(level, seller, deal.item(), deal.room())));
            buyer.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(seller, true));
            seller.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
            return;
        }
        Price price = deal.price();
        int lots = deal.credit() ? (sellable(level, seller, deal.item()) >= price.count() ? 1 : 0)
                : Math.min(Math.min(deal.budget(), Economy.emeralds(level, buyer)) / price.emeralds(),
                Math.min(sellable(level, seller, deal.item()) / price.count(), Math.max(1, deal.room() / price.count())));
        if (lots <= 0) return;
        if (deal.credit()) {
            java.util.Map<java.util.UUID, Integer> debts = new java.util.HashMap<>(buyer.getData(ModVillagers.DEBTS));
            debts.merge(seller.getUUID(), price.emeralds(), Integer::sum);
            buyer.setData(ModVillagers.DEBTS, java.util.Map.copyOf(debts));
        } else {
            int paid = Economy.take(level, buyer, Items.EMERALD, lots * price.emeralds());
            Economy.give(level, seller, new ItemStack(Items.EMERALD, paid));
        }
        if (deal.wear()) {
            if (deal.item() == Items.ARROW) Gear.wear(buyer, new ItemStack(Items.ARROW, Economy.take(level, seller, Items.ARROW, lots * price.count())));
            else Gear.wear(buyer, Economy.takeOne(level, seller, deal.item()));
        } else {
            int old = deal.upgrade() ? Tools.slot(buyer) : -1;
            if (old >= 0) buyer.getInventory().removeItemNoUpdate(old);
            int handed = Economy.take(level, seller, deal.item(), lots * price.count());
            Economy.give(level, buyer, new ItemStack(deal.item(), handed));
            if (old >= 0 && !buyer.getMainHandItem().isEmpty()) Tools.showInHand(buyer, true);
        }
        buyer.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(seller, true));
        seller.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(buyer, true));
        buyer.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
        seller.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
        level.playSound(null, seller.blockPosition(), SoundEvents.VILLAGER_TRADE, SoundSource.NEUTRAL, 1.0F, seller.getVoicePitch());
    }

    /**
     * A supply of its trade that no villager sells, from a travelling merchant ({@link Exports}) within {@link #SEARCH_RANGE},
     * at the merchant's own price and out of what's left of its stock, paid for out of what's above its reserve (a supply
     * of fuel it has none of may use everything above its food money). The merchant has no village: any villager near it may buy.
     */
    private static Optional<MerchantDeal> merchantDeal(ServerLevel level, Villager buyer) {
        Optional<TradeTable> table = TradeTable.forVillager(buyer);
        if (table.isEmpty() || !shopsForWork(buyer)) return Optional.empty();
        java.util.List<WanderingTrader> merchants = java.util.stream.Stream.concat(Exports.marketsOf(level, buyer).stream(), Exports.merchantsNear(level, buyer, SEARCH_RANGE).stream())
                .distinct().toList();
        if (merchants.isEmpty()) return Optional.empty();
        for (TradeTable.Supply supply : table.get().suppliesWanted(level, buyer)) {
            int held = Economy.count(level, buyer, supply.item());
            if (held >= supply.price().count() || waitsForFuel(level, buyer, table.get(), supply)) continue;
            int budget = outOfFuel(level, buyer, supply) ? Economy.emeralds(level, buyer) - Economy.FOOD_MONEY : Economy.spendable(level, buyer);
            for (WanderingTrader merchant : merchants) {
                Optional<MerchantOffer> offer = Exports.wareFor(merchant, supply.item());
                if (offer.isPresent() && offer.get().getCostA().getCount() <= budget && supply.max() - held > 0) {
                    return Optional.of(new MerchantDeal(merchant, supply.item(), offer.get(), supply.max() - held, budget));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * What it would spend at a travelling merchant right now, in whole emeralds: for each supply of its trade it is short of
     * that the merchant carries ({@link Exports#waresPrice}) and no villager in its village sells, as many lots as it can
     * afford above its reserve and fit under the supply's {@code max}. Needs like this draw a merchant to the village, as export goods do ({@link Exports}).
     */
    static int demand(ServerLevel level, Villager buyer) {
        Optional<TradeTable> table = TradeTable.forVillager(buyer);
        if (table.isEmpty() || buyer.isBaby()) return 0;
        java.util.List<Villager> sellers = null;
        int worth = 0;
        int spent = 0;
        for (TradeTable.Supply supply : table.get().suppliesWanted(level, buyer)) {
            int held = Economy.count(level, buyer, supply.item());
            Price price = Exports.waresPrice(supply.item());
            if (price == null || held >= supply.price().count() || waitsForFuel(level, buyer, table.get(), supply)) continue;
            int budget = (outOfFuel(level, buyer, supply) ? Economy.emeralds(level, buyer) - Economy.FOOD_MONEY : Economy.spendable(level, buyer)) - spent;
            int lots = Math.min(budget / price.emeralds(), Math.max(1, (supply.max() - held) / price.count()));
            if (lots <= 0) continue;
            if (sellers == null) sellers = sellers(level, buyer);
            if (sellers.stream().anyMatch(seller -> offer(level, seller, supply.item(), supply.max() - held, Economy.emeralds(level, buyer)).isPresent())) continue;
            worth += lots * price.emeralds();
            spent += lots * price.emeralds();
        }
        return worth;
    }

    private static void tradeWithMerchant(ServerLevel level, Villager buyer, MerchantDeal deal) {
        MerchantOffer offer = deal.offer();
        int emeralds = offer.getCostA().getCount();
        int each = offer.getResult().getCount();
        int lots = Math.min(Math.min(deal.budget(), Economy.emeralds(level, buyer)) / emeralds,
                Math.min(Math.max(1, deal.room() / each), offer.getMaxUses() - offer.getUses()));
        if (lots <= 0 || offer.isOutOfStock()) return;
        Economy.take(level, buyer, Items.EMERALD, lots * emeralds);
        Economy.give(level, buyer, new ItemStack(deal.item(), lots * each));
        for (int i = 0; i < lots; i++) offer.increaseUses();
        buyer.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(deal.merchant(), true));
        buyer.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
        deal.merchant().swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
        level.playSound(null, deal.merchant().blockPosition(), SoundEvents.VILLAGER_TRADE, SoundSource.NEUTRAL, 1.0F, deal.merchant().getVoicePitch());
    }

    private static Optional<Deal> findDeal(ServerLevel level, Villager buyer) {
        if (Economy.emeralds(level, buyer) <= 0) return Optional.empty();
        if (Meals.hasAMeal(level, buyer) && Meals.wantsAChange(level, buyer) && Economy.spendable(level, buyer) > 0) {
            boolean meat = Meals.wantsMeat(buyer);
            Optional<Deal> change = sellers(level, buyer).stream()
                    .flatMap(seller -> Economy.items(level, seller).stream()
                            .filter(item -> Meals.isMeal(new ItemStack(item)) && Meals.isMeat(item) == meat)
                            .flatMap(item -> offer(level, seller, item, 0, Economy.spendable(level, buyer)).stream()))
                    .max(Comparator.comparingDouble((Deal deal) -> (double) deal.price().count() * Meals.nutrition(deal.item()) / deal.price().emeralds()));
            if (change.isPresent()) return change;
        }
        if (Meals.isHungry(buyer) && !Meals.hasAMeal(level, buyer)) {
            // The most meals for its money: an emerald buys far more carrots than bread. Sellers come nearest first, so a tie goes to the nearer one.
            Optional<Deal> meal = sellers(level, buyer).stream()
                    .flatMap(seller -> Economy.items(level, seller).stream()
                            .filter(item -> Meals.isMeal(new ItemStack(item)))
                            .flatMap(item -> offer(level, seller, item, 0, Economy.emeralds(level, buyer)).stream()))
                    .max(Comparator.comparingDouble((Deal deal) -> (double) deal.price().count() * Meals.nutrition(deal.item()) / deal.price().emeralds())
                            .thenComparing(Comparator.comparingDouble((Deal deal) -> buyer.distanceToSqr(deal.seller())).reversed()));
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
        if (table.isEmpty() || !shopsForWork(buyer)) return gear(level, buyer);
        Optional<Deal> tool = toolDeal(level, buyer, table.get());
        if (tool.isPresent()) return tool;
        for (TradeTable.Supply supply : table.get().suppliesWanted(level, buyer)) {
            int held = Economy.count(level, buyer, supply.item());
            if (held >= supply.price().count() || waitsForFuel(level, buyer, table.get(), supply)) continue;
            int budget = outOfFuel(level, buyer, supply) ? Economy.emeralds(level, buyer) - Economy.FOOD_MONEY : Economy.spendable(level, buyer);
            Optional<Deal> deal = sellers(level, buyer).stream()
                    .flatMap(seller -> offer(level, seller, supply.item(), supply.max() - held, budget).stream())
                    .findFirst();
            if (deal.isPresent()) return deal;
        }
        for (com.sheepfromheaven.minenow.merchant.Tour.Want want : com.sheepfromheaven.minenow.merchant.Merchant.shoppingList(level, buyer)) {
            Optional<Deal> deal = sellers(level, buyer).stream()
                    .flatMap(seller -> offer(level, seller, want.item(), want.amount(), Economy.emeralds(level, buyer)).stream())
                    .findFirst();
            if (deal.isPresent()) return deal;
        }
        return upgradeDeal(level, buyer, table.get(), Economy.spendable(level, buyer), false).or(() -> gear(level, buyer));
    }

    /** A supply of its trade it is short of that nobody in its village sells: what a merchant could bring it. */
    public record Need(Villager buyer, Item item, int room) {}

    /** The supplies of {@code buyer}'s trade it is short of, can pay an import price for, and no villager of its village offers. */
    public static java.util.List<Need> needs(ServerLevel level, Villager buyer) {
        Optional<TradeTable> table = TradeTable.forVillager(buyer);
        if (table.isEmpty() || buyer.isBaby()) return java.util.List.of();
        java.util.List<Villager> sellers = null;
        java.util.List<Need> found = new java.util.ArrayList<>();
        for (TradeTable.Supply supply : table.get().suppliesWanted(level, buyer)) {
            int held = Economy.count(level, buyer, supply.item());
            if (held >= supply.price().count() || waitsForFuel(level, buyer, table.get(), supply)) continue;
            int budget = outOfFuel(level, buyer, supply) ? Economy.emeralds(level, buyer) - Economy.FOOD_MONEY : Economy.spendable(level, buyer);
            Optional<Price> imported = com.sheepfromheaven.minenow.merchant.Merchant.importPrice(supply.item());
            if (imported.isEmpty() || budget < imported.get().emeralds()) continue;
            if (sellers == null) sellers = sellers(level, buyer);
            if (sellers.stream().anyMatch(seller -> offer(level, seller, supply.item(), supply.max() - held, budget).isPresent())) continue;
            found.add(new Need(buyer, supply.item(), supply.max() - held));
        }
        return found;
    }

    /** The lot the nearest villager of {@code merchant}'s village would sell it for within {@code budget} emeralds. */
    public static Optional<Price> homeOffer(ServerLevel level, Villager merchant, Item item, int budget) {
        return sellers(level, merchant).stream().flatMap(seller -> offer(level, seller, item, 0, budget).stream()).findFirst().map(Deal::price);
    }

    /**
     * A better tool of its trade than the one it holds (further down its trade file's {@code tools}, unlocked at its
     * level), the old one thrown away: the best it can afford, or on credit the cheapest one.
     */
    private static Optional<Deal> upgradeDeal(ServerLevel level, Villager buyer, TradeTable table, int budget, boolean credit) {
        int slot = Tools.slot(buyer);
        if (budget <= 0 || slot < 0) return Optional.empty();
        int rank = table.toolRank(buyer.getInventory().getItem(slot).getItem());
        if (rank < 0) return Optional.empty();
        int buyerLevel = buyer.getVillagerData().level();
        return sellers(level, buyer).stream()
                .flatMap(seller -> TradeTable.forVillager(seller).stream()
                        .flatMap(sellerTable -> {
                            Comparator<TradeTable.Craft> better = Comparator.comparingInt(craft -> table.toolRank(craft.item()));
                            return sellerTable.craftsUpTo(seller.getVillagerData().level()).stream()
                                    .filter(craft -> table.toolRank(craft.item()) > rank && table.tools().get(table.toolRank(craft.item())).level() <= buyerLevel
                                            && sellerTable.craftPrice(craft).emeralds() <= budget && Economy.count(level, seller, craft.item()) > 0)
                                    .max(credit ? better.reversed() : better)
                                    .map(craft -> new Deal(seller, craft.item(), sellerTable.craftPrice(craft), 1, budget, false, credit, false, true))
                                    .stream();
                        }))
                .findFirst();
    }

    /**
     * A trade that buys fuel (the butcher, the smelter) buys what it cooks only once it holds some: a butcher that
     * spent its purse on fish before finding coal could never smoke it, sell it, or afford the coal.
     */
    private static boolean waitsForFuel(ServerLevel level, Villager buyer, TradeTable table, TradeTable.Supply supply) {
        ItemStack stack = new ItemStack(supply.item());
        if (FurnaceTending.isFuel(stack) || table.supplies().stream().noneMatch(other -> FurnaceTending.isFuel(new ItemStack(other.item())))) return false;
        if (!FurnaceTending.cookable(level, stack)) return false;
        return Economy.items(level, buyer).stream().noneMatch(item -> FurnaceTending.isFuel(new ItemStack(item)));
    }

    /**
     * Tools and supplies of its trade are bought while it works, meets or idles: a miner is down its mine all working
     * day, so its coal and ore could only ever be bought after work.
     */
    private static boolean shopsForWork(Villager buyer) {
        var brain = buyer.getBrain();
        return brain.isActive(Activity.WORK) || brain.isActive(Activity.MEET) || brain.isActive(Activity.IDLE);
    }

    /**
     * Fuel it has none of may come out of everything above its food money, like a first tool: a butcher whose purse
     * was down to its reserve could never buy coal, so never smoked, sold or earned again.
     */
    static boolean outOfFuel(ServerLevel level, Villager buyer, TradeTable.Supply supply) {
        return FurnaceTending.isFuel(new ItemStack(supply.item())) && Economy.items(level, buyer).stream().noneMatch(item -> FurnaceTending.isFuel(new ItemStack(item)));
    }

    /** Whether a villager nearby has a tool of its trade that {@code fits} in stock, unlocked at its level, that it could pay for above its reserve or on credit. */
    public static boolean couldBuyTool(ServerLevel level, Villager buyer, java.util.function.Predicate<ItemStack> fits) {
        Optional<TradeTable> table = TradeTable.forVillager(buyer);
        if (table.isEmpty()) return false;
        int budget = Economy.spendable(level, buyer) + Math.max(0, CREDIT_LIMIT - debt(buyer));
        int buyerLevel = buyer.getVillagerData().level();
        return sellers(level, buyer).stream().anyMatch(seller -> TradeTable.forVillager(seller).stream()
                .flatMap(sellerTable -> sellerTable.craftsUpTo(seller.getVillagerData().level()).stream()
                        .filter(craft -> {
                            ItemStack tool = new ItemStack(craft.item());
                            int rank = table.get().toolRank(craft.item());
                            return table.get().isToolItem(tool) && (rank < 0 || table.get().tools().get(rank).level() <= buyerLevel) && fits.test(tool)
                                    && sellerTable.craftPrice(craft).emeralds() <= budget && Economy.count(level, seller, craft.item()) > 0;
                        }))
                .findAny().isPresent());
    }

    public static int debt(Villager villager) {
        return villager.getData(ModVillagers.DEBTS).values().stream().mapToInt(Integer::intValue).sum();
    }

    /** Pays what it owes from anything above its reserve, so a debt never eats its food or tool money; a creditor that is gone is never paid. */
    private static void repayDebts(ServerLevel level, Villager buyer) {
        java.util.Map<java.util.UUID, Integer> debts = buyer.getData(ModVillagers.DEBTS);
        if (debts.isEmpty()) return;
        java.util.Map<java.util.UUID, Integer> left = new java.util.HashMap<>();
        for (var entry : debts.entrySet()) {
            if (!(level.getEntity(entry.getKey()) instanceof Villager creditor) || !creditor.isAlive()) continue;
            int pay = Math.min(entry.getValue(), Math.max(0, Economy.spendable(level, buyer)));
            if (pay > 0) Economy.give(level, creditor, new ItemStack(Items.EMERALD, Economy.take(level, buyer, Items.EMERALD, pay)));
            if (entry.getValue() > pay) left.put(entry.getKey(), entry.getValue() - pay);
        }
        buyer.setData(ModVillagers.DEBTS, java.util.Map.copyOf(left));
    }

    /**
     * A hungry villager with no meal and no money is given one by a neighbour holding far more of some food than it
     * could ever sell (more than a stack beyond what it keeps), the way vanilla villagers share food, but only with
     * those who can't pay, so goods otherwise still move by trade.
     */
    private static Optional<Deal> mealGift(ServerLevel level, Villager buyer) {
        if (!Meals.isHungry(buyer) || Meals.hasAMeal(level, buyer)) return Optional.empty();
        return sellers(level, buyer).stream()
                .flatMap(seller -> Economy.items(level, seller).stream()
                        .filter(item -> Meals.isMeal(new ItemStack(item)) && Meals.nutrition(item) > 0)
                        .map(item -> new Deal(seller, item, new Price(1, 1), (Meals.MEAL_NUTRITION + Meals.nutrition(item) - 1) / Meals.nutrition(item), 0, false, false, true, false))
                        .filter(gift -> Exports.bulk(level, seller, gift.item()) >= gift.room()))
                .findFirst();
    }

    /** A tool or one lot of a supply of its trade, on credit, when it can't pay for them and still owes little enough. */
    private static Optional<Deal> creditDeal(ServerLevel level, Villager buyer) {
        Optional<TradeTable> table = TradeTable.forVillager(buyer);
        if (table.isEmpty() || !shopsForWork(buyer)) return Optional.empty();
        int room = CREDIT_LIMIT - debt(buyer);
        if (room <= 0) return Optional.empty();
        if (IdleReason.lacksTool(level, buyer)) {
            Optional<Deal> tool = sellers(level, buyer).stream()
                    .flatMap(seller -> TradeTable.forVillager(seller).stream()
                            .flatMap(sellerTable -> sellerTable.craftsUpTo(seller.getVillagerData().level()).stream()
                                    .filter(craft -> sellerTable.craftPrice(craft).emeralds() <= room && table.get().isToolItem(new ItemStack(craft.item()))
                                            && Economy.count(level, seller, craft.item()) > 0)
                                    .min(Comparator.comparingInt(craft -> sellerTable.craftPrice(craft).emeralds()))
                                    .map(craft -> new Deal(seller, craft.item(), sellerTable.craftPrice(craft), 1, 0, false, true))
                                    .stream()))
                    .findFirst();
            if (tool.isPresent()) return tool;
        }
        if (com.sheepfromheaven.minenow.miner.Miner.pickaxeHoldsItBack(level, buyer)) {
            Optional<Deal> upgrade = upgradeDeal(level, buyer, table.get(), room, true);
            if (upgrade.isPresent()) return upgrade;
        }
        for (TradeTable.Supply supply : table.get().suppliesWanted(level, buyer)) {
            if (Economy.count(level, buyer, supply.item()) >= supply.price().count() || waitsForFuel(level, buyer, table.get(), supply)) continue;
            Optional<Deal> lot = sellers(level, buyer).stream()
                    .flatMap(seller -> TradeTable.forVillager(seller).flatMap(sellerTable -> sellerTable.sellPrice(supply.item(), seller.getVillagerData().level()))
                            .map(full -> lotFor(full, sellable(level, seller, supply.item())))
                            .filter(price -> price.emeralds() <= room && sellable(level, seller, supply.item()) >= price.count())
                            .map(price -> new Deal(seller, supply.item(), price, price.count(), 0, false, true)).stream())
                    .findFirst();
            if (lot.isPresent()) return lot;
        }
        return Optional.empty();
    }

    /** Whether a villager of its village would sell {@code supply} to {@code buyer}, for money above what it must keep or on credit, asleep or down a mine included since they sell again; when none does, a player is the only place left (see {@link SeekGoods}). */
    static boolean canBuySupplyFromAVillager(ServerLevel level, Villager buyer, TradeTable.Supply supply) {
        int budget = outOfFuel(level, buyer, supply) ? Economy.emeralds(level, buyer) - Economy.FOOD_MONEY : Economy.spendable(level, buyer);
        int room = CREDIT_LIMIT - debt(buyer);
        return sellers(level, buyer, false).stream().anyMatch(seller -> offer(level, seller, supply.item(), supply.max(), budget).isPresent()
                || room > 0 && TradeTable.forVillager(seller).flatMap(table -> table.sellPrice(supply.item(), seller.getVillagerData().level()))
                .map(full -> lotFor(full, sellable(level, seller, supply.item())))
                .filter(price -> price.emeralds() <= room && sellable(level, seller, supply.item()) >= price.count()).isPresent());
    }

    /** Whether a villager nearby has a tool of its trade it can afford, so it goes to buy it (see {@link SeekGoods}). */
    static boolean canBuyToolFromAVillager(ServerLevel level, Villager buyer) {
        return TradeTable.forVillager(buyer).flatMap(table -> toolDeal(level, buyer, table)).isPresent();
    }

    private static Optional<Deal> toolDeal(ServerLevel level, Villager buyer, TradeTable table) {
        if (!IdleReason.lacksTool(level, buyer)) return Optional.empty();
        int wallet = Economy.emeralds(level, buyer) - Economy.FOOD_MONEY;
        return sellers(level, buyer).stream()
                .flatMap(seller -> TradeTable.forVillager(seller).stream()
                        .flatMap(sellerTable -> sellerTable.craftsUpTo(seller.getVillagerData().level()).stream()
                                .filter(craft -> sellerTable.craftPrice(craft).emeralds() <= wallet && table.isToolItem(new ItemStack(craft.item()))
                                        && Economy.count(level, seller, craft.item()) > 0)
                                .max(Comparator.comparingInt((TradeTable.Craft craft) -> sellerTable.craftPrice(craft).emeralds()).thenComparingInt(craft -> table.toolRank(craft.item())))
                                .map(craft -> new Deal(seller, craft.item(), sellerTable.craftPrice(craft), 1, wallet))
                                .stream()))
                .findFirst();
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

    /**
     * The villagers of its own village ({@link Villages}), however far; a villager of no village trades with those
     * within {@link #SEARCH_RANGE} that belong to none either, so two villages never trade.
     */
    private static java.util.List<Villager> sellers(ServerLevel level, Villager buyer) {
        return sellers(level, buyer, true);
    }

    /** {@code awakeAndUp} false also counts the ones asleep or down a mine, who sell again when they wake or come up. */
    private static java.util.List<Villager> sellers(ServerLevel level, Villager buyer, boolean awakeAndUp) {
        Optional<Villages.Village> village = Villages.of(level, buyer);
        java.util.List<Villager> candidates = village.isPresent() ? new java.util.ArrayList<>(village.get().villagers(level))
                : level.getEntitiesOfClass(Villager.class, buyer.getBoundingBox().inflate(SEARCH_RANGE), seller -> Villages.of(level, seller).isEmpty());
        village.ifPresent(own -> com.sheepfromheaven.minenow.merchant.Merchant.marketsIn(level, own).stream().filter(merchant -> !candidates.contains(merchant)).forEach(candidates::add));
        return candidates.stream()
                .filter(seller -> seller != buyer && seller.isAlive() && !seller.isBaby() && !seller.isTrading()
                        && (!awakeAndUp || !seller.isSleeping() && !com.sheepfromheaven.minenow.miner.Miner.isDownAMine(level, seller)))
                .sorted(Comparator.comparingDouble(buyer::distanceToSqr))
                .toList();
    }

    private static Optional<Deal> offer(ServerLevel level, Villager seller, Item item, int room, int budget) {
        Optional<Price> price = (com.sheepfromheaven.minenow.merchant.Merchant.isSelling(seller) ? com.sheepfromheaven.minenow.merchant.Merchant.importPrice(item)
                : TradeTable.forVillager(seller).flatMap(table -> table.sellPrice(item, seller.getVillagerData().level())))
                .map(lot -> lotFor(lot, sellable(level, seller, item)));
        if (price.isEmpty() || sellable(level, seller, item) < price.get().count() || budget < price.get().emeralds()) return Optional.empty();
        return Optional.of(new Deal(seller, item, price.get(), room, budget));
    }

    /**
     * Prices are the smallest whole-emerald deal, so a cheap item comes in big lots (raw copper 50 for 3 emeralds); a
     * seller holding less than a whole lot sells an emerald's share of it instead, rounded down in its favour.
     */
    static Price lotFor(Price lot, int sellable) {
        if (sellable >= lot.count() || lot.emeralds() <= 1 || lot.count() / lot.emeralds() < 1) return lot;
        return new Price(lot.count() / lot.emeralds(), 1);
    }

    private static int sellable(ServerLevel level, Villager seller, Item item) {
        return Economy.count(level, seller, item) - Unloading.keepLimit(seller, new ItemStack(item)) - Commissions.reserved(seller, item);
    }

    /** Its open commission, if there's something to do about it: collect what's made, or ask for its money back once it's waited too long. */
    private static Optional<Visit> commissionErrand(ServerLevel level, Villager buyer) {
        Optional<Commissions.Placed> placed = Commissions.placed(buyer);
        if (placed.isEmpty()) return Optional.empty();
        long now = level.getGameTime();
        if (!(level.getEntity(placed.get().maker()) instanceof Villager maker) || !maker.isAlive()) {
            if (now - placed.get().placed() > 2 * Commissions.EXPIRES) Commissions.forget(buyer);
            return Optional.empty();
        }
        Optional<Commissions.Order> order = Commissions.orderFor(maker, buyer);
        if (order.isEmpty()) {
            Commissions.forget(buyer);
            return Optional.empty();
        }
        if (maker.isSleeping() || com.sheepfromheaven.minenow.miner.Miner.isDownAMine(level, maker)) return Optional.empty();
        if (order.get().item().filter(item -> Economy.count(level, maker, item) >= order.get().count()).isPresent()) {
            return Optional.of(new Visit(maker, Errand.COLLECT, order.get()));
        }
        boolean stillPaving = order.get().paving() && com.sheepfromheaven.minenow.mason.PaveRoads.takesWork(level, maker);
        if (now - order.get().placed() > Commissions.EXPIRES && !stillPaving) return Optional.of(new Visit(maker, Errand.REFUND, order.get()));
        return Optional.empty();
    }

    /**
     * Something it wants that nobody has, paid for up front so its maker can buy what it takes: a piece of gear, a lot
     * of a supply of its trade, or (from a mason) the path to its home paved. One open commission at a time.
     */
    private static Optional<Visit> newCommission(ServerLevel level, Villager buyer) {
        if (Commissions.placed(buyer).isPresent()) return Optional.empty();
        long now = level.getGameTime();
        int gearBudget = Gear.budget(level, buyer);
        java.util.List<Villager> makers = sellers(level, buyer).stream().filter(Commissions::takesOrders).toList();
        if (gearBudget > 0) {
            Optional<Visit> gear = makers.stream()
                    .flatMap(maker -> TradeTable.forVillager(maker).stream()
                            .flatMap(table -> table.craftsUpTo(maker.getVillagerData().level()).stream()
                                    .filter(craft -> new ItemStack(craft.item()).getMaxStackSize() == 1 && Gear.wants(buyer, new ItemStack(craft.item()))
                                            && table.craftPrice(craft).emeralds() <= gearBudget && sellable(level, maker, craft.item()) < 1)
                                    .max(Comparator.comparingDouble(craft -> Gear.worth(new ItemStack(craft.item()))))
                                    .map(craft -> new Visit(maker, Errand.ORDER, new Commissions.Order(buyer.getUUID(), Optional.of(craft.item()), 1, Optional.empty(),
                                            table.craftPrice(craft).emeralds(), now)))
                                    .stream()))
                    .findFirst();
            if (gear.isPresent()) return gear;
        }
        Optional<TradeTable> table = TradeTable.forVillager(buyer);
        if (table.isPresent() && shopsForWork(buyer)) {
            int budget = Economy.spendable(level, buyer);
            for (TradeTable.Supply supply : table.get().suppliesWanted(level, buyer)) {
                if (Economy.count(level, buyer, supply.item()) >= supply.price().count() || waitsForFuel(level, buyer, table.get(), supply)) continue;
                Optional<Visit> order = makers.stream()
                        .flatMap(maker -> TradeTable.forVillager(maker).flatMap(makerTable -> makerTable.sellPrice(supply.item(), maker.getVillagerData().level()))
                                .filter(price -> price.emeralds() <= budget && sellable(level, maker, supply.item()) < price.count() && makes(level, maker, supply.item()))
                                .map(price -> new Visit(maker, Errand.ORDER, new Commissions.Order(buyer.getUUID(), Optional.of(supply.item()), price.count(), Optional.empty(), price.emeralds(), now)))
                                .stream())
                        .findFirst();
                if (order.isPresent()) return order;
            }
        }
        if (gearBudget > 0) {
            for (Villager maker : makers) {
                Optional<Commissions.Order> paving = com.sheepfromheaven.minenow.mason.PaveRoads.quote(level, buyer, maker, gearBudget);
                if (paving.isPresent()) return Optional.of(new Visit(maker, Errand.ORDER, paving.get()));
            }
        }
        return Optional.empty();
    }

    /**
     * Whether the maker has some of the item or what its trade makes it from, so an order to it will come: a forester
     * among oaks sells spruce planks too, but would never have any.
     */
    private static boolean makes(ServerLevel level, Villager maker, Item item) {
        if (Economy.count(level, maker, item) > 0) return true;
        return TradeTable.forVillager(maker).flatMap(table -> table.craft(item, maker.getVillagerData().level()))
                .filter(craft -> craft.materials().stream().allMatch(material -> Economy.count(level, maker, material.item()) >= material.count())).isPresent();
    }

    private static void run(ServerLevel level, Villager buyer, Visit visit) {
        Villager maker = visit.maker();
        Commissions.Order order = visit.order();
        switch (visit.errand()) {
            case ORDER -> {
                if (Commissions.placed(buyer).isPresent() || !Commissions.takesOrders(maker) || Economy.emeralds(level, buyer) < order.paid()) return;
                int paid = Economy.take(level, buyer, Items.EMERALD, order.paid());
                Economy.give(level, maker, new ItemStack(Items.EMERALD, paid));
                Commissions.place(buyer, maker, new Commissions.Order(order.buyer(), order.item(), order.count(), order.path(), paid, level.getGameTime()));
            }
            case COLLECT -> {
                Item item = order.item().orElseThrow();
                if (Economy.count(level, maker, item) < order.count()) return;
                if (order.count() == 1 && Gear.wants(buyer, new ItemStack(item))) Gear.wear(buyer, Economy.takeOne(level, maker, item));
                else Economy.give(level, buyer, new ItemStack(item, Economy.take(level, maker, item, order.count())));
                Commissions.finish(maker, buyer.getUUID());
                Commissions.forget(buyer);
                Commissions.DELIVERED.incrementAndGet();
            }
            case REFUND -> {
                int back = Economy.take(level, maker, Items.EMERALD, Math.min(order.paid(), Economy.emeralds(level, maker)));
                Economy.give(level, buyer, new ItemStack(Items.EMERALD, back));
                Commissions.finish(maker, buyer.getUUID());
                Commissions.forget(buyer);
                Commissions.REFUNDED.incrementAndGet();
            }
        }
        buyer.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(maker, true));
        maker.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(buyer, true));
        buyer.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
        maker.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
        level.playSound(null, maker.blockPosition(), SoundEvents.VILLAGER_TRADE, SoundSource.NEUTRAL, 1.0F, maker.getVoicePitch());
    }

    private void walk(Villager buyer) {
        buyer.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new EntityTracker(partner(), false), SPEED, 1));
        buyer.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(partner(), true));
    }

    /** What a villager may pay for a supply: fuel it has none of may use everything above its food money, the rest only what is above its reserve. */
    static int budgetFor(ServerLevel level, Villager buyer, TradeTable.Supply supply) {
        return outOfFuel(level, buyer, supply) ? Math.max(0, Economy.emeralds(level, buyer) - Economy.FOOD_MONEY) : Economy.spendable(level, buyer);
    }
}
