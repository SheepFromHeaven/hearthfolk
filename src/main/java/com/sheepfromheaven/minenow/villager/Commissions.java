package com.sheepfromheaven.minenow.villager;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Work paid for up front: a villager with money to spare that wants something nobody has in stock pays its maker, so
 * the maker can buy what it needs to make it (a farmer pays the armorer for a chestplate, the armorer the smelter for
 * iron, the smelter the miner for ore), then comes back for it; or pays a mason to pave the path to its home. The order
 * is kept on the maker ({@link ModVillagers#COMMISSIONS}) and remembered by the buyer ({@link ModVillagers#COMMISSIONED}),
 * both saved with the villagers, so a restart loses nothing. Undelivered after {@link #EXPIRES} the buyer asks for its
 * money back; an order whose buyer never came back is dropped after twice that.
 */
public final class Commissions {
    public static final long EXPIRES = 3 * 24000L;

    /** {@code count} of {@code item} for {@code buyer}, or the dirt path joined to {@code path} paved, paid {@code paid} emeralds at {@code placed}. */
    public record Order(UUID buyer, Optional<Item> item, int count, Optional<BlockPos> path, int paid, long placed) {
        static final Codec<Order> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.STRING_CODEC.fieldOf("buyer").forGetter(Order::buyer),
                BuiltInRegistries.ITEM.byNameCodec().optionalFieldOf("item").forGetter(Order::item),
                Codec.INT.fieldOf("count").forGetter(Order::count),
                BlockPos.CODEC.optionalFieldOf("path").forGetter(Order::path),
                Codec.INT.fieldOf("paid").forGetter(Order::paid),
                Codec.LONG.fieldOf("placed").forGetter(Order::placed)
        ).apply(i, Order::new));

        public boolean paving() {
            return path.isPresent();
        }
    }

    /** The buyer's side: which villager it paid, and when. */
    public record Placed(UUID maker, long placed) {
        static final Codec<Placed> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.STRING_CODEC.fieldOf("maker").forGetter(Placed::maker),
                Codec.LONG.fieldOf("placed").forGetter(Placed::placed)
        ).apply(i, Placed::new));
    }

    /** Orders delivered and paid back since the server started, for the soak report. */
    public static final java.util.concurrent.atomic.AtomicInteger DELIVERED = new java.util.concurrent.atomic.AtomicInteger();
    public static final java.util.concurrent.atomic.AtomicInteger REFUNDED = new java.util.concurrent.atomic.AtomicInteger();

    private Commissions() {}

    /** A maker takes as many open orders as its level (a novice one), so it never owes more work than it can get done. */
    public static boolean takesOrders(Villager maker) {
        return maker.getData(ModVillagers.COMMISSIONS).size() < maker.getVillagerData().level();
    }

    /** The orders a maker is working on, without those whose buyer never came back. */
    public static List<Order> open(Villager maker, long now) {
        List<Order> orders = maker.getData(ModVillagers.COMMISSIONS);
        if (orders.stream().noneMatch(order -> now - order.placed() > 2 * EXPIRES)) return orders;
        List<Order> kept = orders.stream().filter(order -> now - order.placed() <= 2 * EXPIRES).toList();
        maker.setData(ModVillagers.COMMISSIONS, kept);
        return kept;
    }

    /** How many of {@code item} the maker holds back for the villagers that paid for them. */
    public static int reserved(Villager maker, Item item) {
        return maker.getData(ModVillagers.COMMISSIONS).stream()
                .filter(order -> order.item().filter(ordered -> ordered == item).isPresent())
                .mapToInt(Order::count).sum();
    }

    public static Optional<Placed> placed(Villager buyer) {
        return buyer.getData(ModVillagers.COMMISSIONED).stream().findFirst();
    }

    public static Optional<Order> orderFor(Villager maker, Villager buyer) {
        return maker.getData(ModVillagers.COMMISSIONS).stream().filter(order -> order.buyer().equals(buyer.getUUID())).findFirst();
    }

    static void place(Villager buyer, Villager maker, Order order) {
        List<Order> orders = new ArrayList<>(maker.getData(ModVillagers.COMMISSIONS));
        orders.add(order);
        maker.setData(ModVillagers.COMMISSIONS, List.copyOf(orders));
        buyer.setData(ModVillagers.COMMISSIONED, List.of(new Placed(maker.getUUID(), order.placed())));
    }

    /** The order's done (delivered, paved or refunded): off the maker's list. The buyer forgets it once it sees that. */
    public static void finish(Villager maker, UUID buyer) {
        maker.setData(ModVillagers.COMMISSIONS, maker.getData(ModVillagers.COMMISSIONS).stream().filter(order -> !order.buyer().equals(buyer)).toList());
    }

    static void forget(Villager buyer) {
        buyer.setData(ModVillagers.COMMISSIONED, List.of());
    }
}
