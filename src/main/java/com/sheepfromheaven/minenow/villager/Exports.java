package com.sheepfromheaven.minenow.villager;

import com.sheepfromheaven.minenow.MineNow;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.equine.Mule;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The village's exports: goods only players would otherwise buy ({@code #minenow:exports}: armor, swords, shields, bows,
 * saddles, bundles, ...). The more such goods a village holds near a player, the likelier a travelling merchant turns up
 * ({@link #visitChance}): a wandering trader by name and with two pack mules instead of llamas, selling goods from afar
 * the village can't make ({@code data/<namespace>/travelling_merchant/*.json}, all files merged) instead of vanilla's random trades. It walks to the villagers holding export
 * goods and buys them at their price with new emeralds, up to {@link #TRADER_BUDGET} per visit, so luxury crafters earn
 * without a player; with what's left it buys the village's bulk surplus at half price ({@link #bulk}), so a miner
 * buried in cobblestone or a fisherman in fish still earns. Vanilla's own wandering trader stays vanilla.
 */
public final class Exports {
    static final TagKey<Item> GOODS = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "exports"));
    static final int MIN_SURPLUS = 8;
    static final int BULK_KEEP = 32;
    static final double MAX_CHANCE = 0.5;
    private static final double FULL_CHANCE_AT = 128.0;
    private static final int TRADER_BUDGET = 32;
    private static final int VISIT_CHECK = 1200;
    private static final int VISIT_COOLDOWN = 6000;
    private static final int VILLAGE_RANGE = 48;
    private static final int TRADER_RANGE = 96;
    private static final int SHOP_RANGE = 24;
    private static final double REACH = 3.0;

    private static final int MULES = 2;
    private static final int STAY = 24000;

    private static final Map<UUID, Integer> SPENT = new HashMap<>();
    private static long lastVisit = Long.MIN_VALUE;

    private Exports() {}

    private record Ware(Identifier item, double price) {
        static final Codec<Ware> CODEC = RecordCodecBuilder.create(i -> i.group(
                Identifier.CODEC.fieldOf("item").forGetter(Ware::item),
                Codec.DOUBLE.fieldOf("price").forGetter(Ware::price)
        ).apply(i, Ware::new));
    }

    private record WareList(List<Ware> wares) {
        static final Codec<WareList> CODEC = RecordCodecBuilder.create(i -> i.group(
                Ware.CODEC.listOf().fieldOf("wares").forGetter(WareList::wares)
        ).apply(i, WareList::new));
    }

    private static volatile Map<Identifier, Double> wares = Map.of();

    private static MerchantOffer ware(Item item, double price) {
        Price deal = Price.of(price);
        return new MerchantOffer(new ItemCost(Items.EMERALD, deal.emeralds()), new ItemStack(item, deal.count()), 8, 1, 0.05F);
    }

    static boolean isMerchant(WanderingTrader trader) {
        return trader.getData(ModVillagers.TRAVELLING_MERCHANT);
    }

    /** How likely a check (once a minute) brings an extra trader, for goods worth {@code surplus} emeralds. */
    static double visitChance(int surplus) {
        if (surplus < MIN_SURPLUS) return 0.0;
        return Math.min(MAX_CHANCE, surplus / FULL_CHANCE_AT);
    }

    /**
     * What it would sell the merchant, in whole emeralds: its export goods at its own prices, and its bulk surplus (what
     * it sells beyond {@link #BULK_KEEP} and beyond what it keeps) at half price, sold in whole batches.
     */
    static int surplus(ServerLevel level, Villager villager) {
        int worth = 0;
        for (Item item : Economy.items(level, villager)) {
            Price price = price(villager, item);
            if (price == null) continue;
            if (new ItemStack(item).is(GOODS)) {
                worth += sellable(level, villager, item) / price.count() * price.emeralds();
            } else {
                int bulk = bulk(level, villager, item);
                Price half = BuyFromVillagers.lotFor(halfPrice(price), bulk);
                worth += bulk / half.count() * half.emeralds();
            }
        }
        return worth;
    }

    /**
     * Everyday goods beyond what the village can use (a miner's cobblestone, a fisherman's catch): what it holds of
     * something it sells beyond what it keeps and half a stack to sell at home. The merchant takes only that, so it can't
     * empty the village of anything.
     */
    static int bulk(ServerLevel level, Villager villager, Item item) {
        if (new ItemStack(item).is(GOODS)) return 0;
        return Math.max(0, Economy.count(level, villager, item) - Commissions.reserved(villager, item) - Math.max(BULK_KEEP, Unloading.keepLimit(villager, new ItemStack(item))));
    }

    private static Price halfPrice(Price price) {
        return new Price(price.count() * 2, price.emeralds());
    }

    /** Sends a travelling merchant to the village, onto open ground within 8 blocks of this villager and 3 of its height; null if there's no room. */
    static @Nullable WanderingTrader sendTrader(ServerLevel level, Villager villager) {
        for (int attempt = 0; attempt < 20; attempt++) {
            int x = villager.getBlockX() + level.getRandom().nextInt(17) - 8;
            int z = villager.getBlockZ() + level.getRandom().nextInt(17) - 8;
            BlockPos pos = null;
            for (int y = villager.getBlockY() + 3; y >= villager.getBlockY() - 3 && pos == null; y--) {
                BlockPos feet = new BlockPos(x, y, z);
                if (level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), net.minecraft.core.Direction.UP)
                        && level.noCollision(EntityTypes.WANDERING_TRADER.getSpawnAABB(x + 0.5, y, z + 0.5))) pos = feet;
            }
            if (pos == null) continue;
            WanderingTrader trader = EntityTypes.WANDERING_TRADER.spawn(level, pos, EntitySpawnReason.EVENT);
            if (trader == null) continue;
            trader.setData(ModVillagers.TRAVELLING_MERCHANT, true);
            trader.setCustomName(Component.literal("Travelling Merchant"));
            MerchantOffers offers = trader.getOffers();
            offers.clear();
            wares.forEach((id, price) -> BuiltInRegistries.ITEM.getOptional(id).ifPresent(item -> offers.add(ware(item, price))));
            trader.setDespawnDelay(STAY);
            trader.setWanderTarget(villager.blockPosition());
            for (int i = 0; i < MULES; i++) {
                Mule mule = EntityTypes.MULE.spawn(level, pos, EntitySpawnReason.EVENT);
                if (mule == null) continue;
                mule.setChest(true);
                mule.setData(ModVillagers.PACK_MULE, true);
                mule.setLeashedTo(trader, true);
            }
            return trader;
        }
        return null;
    }

    /** Once a minute, near each player: a village holding export goods may draw a wandering trader, the more goods the likelier. */
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.getGameTime() % VISIT_CHECK != 0) return;
        if (lastVisit != Long.MIN_VALUE && level.getGameTime() - lastVisit < VISIT_COOLDOWN && level.getGameTime() >= lastVisit) return;
        for (ServerPlayer player : level.players()) visitNear(level, player, level.getRandom()::nextDouble);
    }

    /** The village around this player may draw a merchant: {@code roll} below the visit chance for its export goods, unless merchants are switched off ({@code travellingMerchants}). */
    static void visitNear(ServerLevel level, ServerPlayer player, java.util.function.DoubleSupplier roll) {
        if (!com.sheepfromheaven.minenow.MineNowConfig.TRAVELLING_MERCHANTS.get()) return;
        if (!level.getEntitiesOfClass(WanderingTrader.class, player.getBoundingBox().inflate(TRADER_RANGE)).isEmpty()) return;
        var villagers = level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(VILLAGE_RANGE), Villager::isAlive);
        int total = villagers.stream().mapToInt(villager -> surplus(level, villager)).sum();
        if (roll.getAsDouble() >= visitChance(total)) return;
        villagers.stream().max(Comparator.comparingInt(villager -> surplus(level, villager))).ifPresent(villager -> {
            if (sendTrader(level, villager) != null) lastVisit = level.getGameTime();
        });
    }

    public static void onAddReloadListeners(AddServerReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "travelling_merchant"), new Loader());
    }

    private static final class Loader extends SimpleJsonResourceReloadListener<WareList> {
        Loader() {
            super(WareList.CODEC, FileToIdConverter.json("travelling_merchant"));
        }

        @Override
        protected void apply(Map<Identifier, WareList> lists, ResourceManager manager, ProfilerFiller profiler) {
            Map<Identifier, Double> merged = new LinkedHashMap<>();
            lists.values().forEach(list -> list.wares().forEach(ware -> merged.put(ware.item(), ware.price())));
            wares = Map.copyOf(merged);
        }
    }

    /** A travelling merchant's mules leave with it, rather than stay behind as free mules. */
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (!(event.getEntity() instanceof WanderingTrader trader) || !(event.getLevel() instanceof ServerLevel level) || !isMerchant(trader)) return;
        Entity.RemovalReason reason = trader.getRemovalReason();
        if (reason == null || !reason.shouldDestroy()) return;
        level.getEntitiesOfClass(Mule.class, trader.getBoundingBox().inflate(16), mule -> mule.getData(ModVillagers.PACK_MULE) && mule.getLeashHolder() == trader)
                .forEach(Entity::discard);
    }

    /** A travelling merchant walks to the nearest villager with export goods and buys them, within its budget for the visit. */
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof Mule mule && !mule.level().isClientSide() && mule.getData(ModVillagers.PACK_MULE) && !mule.mayBeLeashed() && !mule.isTamed()) {
            mule.discard();
            return;
        }
        if (!(event.getEntity() instanceof WanderingTrader trader) || !(trader.level() instanceof ServerLevel level) || trader.tickCount % 20 != 0 || !isMerchant(trader)) return;
        int budget = TRADER_BUDGET - SPENT.getOrDefault(trader.getUUID(), 0);
        if (budget <= 0 || trader.isTrading()) return;
        Villager seller = level.getEntitiesOfClass(Villager.class, new AABB(trader.blockPosition()).inflate(SHOP_RANGE), villager -> villager.isAlive() && !villager.isSleeping() && surplus(level, villager) > 0)
                .stream().min(Comparator.comparingDouble(trader::distanceToSqr)).orElse(null);
        if (seller == null) return;
        if (trader.distanceTo(seller) > REACH) {
            trader.getNavigation().moveTo(seller, 0.5);
            return;
        }
        int spent = buy(level, seller, budget);
        if (spent > 0) {
            SPENT.merge(trader.getUUID(), spent, Integer::sum);
            trader.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
            seller.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
            level.playSound(null, seller.blockPosition(), SoundEvents.VILLAGER_TRADE, SoundSource.NEUTRAL, 1.0F, seller.getVoicePitch());
        }
    }

    private static int buy(ServerLevel level, Villager seller, int budget) {
        int spent = 0;
        for (Item item : Economy.items(level, seller)) {
            if (!new ItemStack(item).is(GOODS)) continue;
            Price price = price(seller, item);
            if (price == null) continue;
            int lots = Math.min((budget - spent) / price.emeralds(), sellable(level, seller, item) / price.count());
            if (lots <= 0) continue;
            Economy.take(level, seller, item, lots * price.count());
            Economy.give(level, seller, new ItemStack(Items.EMERALD, lots * price.emeralds()));
            spent += lots * price.emeralds();
        }
        for (Item item : Economy.items(level, seller)) {
            Price full = price(seller, item);
            int bulk = bulk(level, seller, item);
            if (full == null || bulk <= 0) continue;
            Price price = BuyFromVillagers.lotFor(halfPrice(full), bulk);
            int lots = Math.min((budget - spent) / price.emeralds(), bulk / price.count());
            if (lots <= 0) continue;
            Economy.take(level, seller, item, lots * price.count());
            Economy.give(level, seller, new ItemStack(Items.EMERALD, lots * price.emeralds()));
            spent += lots * price.emeralds();
        }
        return spent;
    }

    private static @Nullable Price price(Villager villager, Item item) {
        return TradeTable.forVillager(villager).flatMap(table -> table.sellPrice(item, villager.getVillagerData().level())).orElse(null);
    }

    private static int sellable(ServerLevel level, Villager villager, Item item) {
        return Economy.count(level, villager, item) - Unloading.keepLimit(villager, new ItemStack(item)) - Commissions.reserved(villager, item);
    }
}
