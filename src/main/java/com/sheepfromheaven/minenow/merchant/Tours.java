package com.sheepfromheaven.minenow.merchant;

import com.sheepfromheaven.minenow.MineNow;
import com.sheepfromheaven.minenow.MineNowConfig;
import com.sheepfromheaven.minenow.villager.BuyFromVillagers;
import com.sheepfromheaven.minenow.villager.Economy;
import com.sheepfromheaven.minenow.villager.Exports;
import com.sheepfromheaven.minenow.villager.Price;
import com.sheepfromheaven.minenow.villager.Villages;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.animal.equine.Mule;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Planning and running merchant tours ({@link Tour}): which villages are short of what the merchant's own village sells,
 * the walk between them, and the chunks kept ticking around a merchant on the road (at most
 * {@link MineNowConfig#maxMerchantTours()} tours at once, so the cost stays bounded).
 */
public final class Tours {
    static final int SHOP_TICKS = 1200;
    static final int MARKET_TICKS = 2400;
    static final int TRAVEL_TICKS = 6000;
    static final int MIN_REST = 2400;
    private static final double ARRIVE = 4.0;
    private static final int KEEP_RADIUS = 2;
    private static final long MORNING = 6000;
    private static final long DUSK = 12500;

    private static final Set<UUID> ACTIVE = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, ChunkPos> LAST = new ConcurrentHashMap<>();
    private static final TicketController TICKETS = new TicketController(Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "tours"),
            (level, helper) -> List.copyOf(helper.getEntityTickets().keySet()).forEach(helper::removeAllTickets));

    private Tours() {}

    public static void registerTickets(RegisterTicketControllersEvent event) {
        event.register(TICKETS);
    }

    record Plan(List<BlockPos> stops, List<Tour.Want> wanted) {}

    /** Whether it may set out now: in the morning, rested, and a tour slot free. */
    static boolean mayStart(ServerLevel level, Villager merchant) {
        return level.getGameTime() >= merchant.getData(Merchant.RESTED_AT) && level.getOverworldClockTime() % 24000 < MORNING && slotFree(merchant.getUUID());
    }

    static boolean slotFree(UUID merchant) {
        return ACTIVE.contains(merchant) || ACTIVE.size() < MineNowConfig.maxMerchantTours();
    }

    static List<Villager> markets(ServerLevel level) {
        List<Villager> found = new ArrayList<>();
        for (UUID id : ACTIVE) {
            if (level.getEntity(id) instanceof Villager merchant && merchant.isAlive() && Merchant.isSelling(merchant)) found.add(merchant);
        }
        return found;
    }

    static void resume(Villager merchant) {
        ACTIVE.add(merchant.getUUID());
    }

    static void forget(Villager merchant) {
        ACTIVE.remove(merchant.getUUID());
        if (merchant.level() instanceof ServerLevel level) release(level, merchant);
    }

    /** What villages near it are short of that its own village can sell it, within its purse and what its mules hold. */
    static Optional<Plan> plan(ServerLevel level, Villager merchant) {
        Optional<Villages.Village> home = Villages.of(level, merchant);
        if (home.isEmpty()) return Optional.empty();
        BlockPos here = merchant.blockPosition();
        int range = MerchantSkill.range(merchant.getVillagerData().level());
        record Candidate(BlockPos bell, Map<Item, Integer> demand) {}
        List<Candidate> candidates = new ArrayList<>();
        for (Villages.Village village : Villages.all(level)) {
            if (!java.util.Collections.disjoint(village.bells(), home.get().bells())) continue;
            Optional<BlockPos> bell = village.bells().stream().filter(pos -> pos.closerThan(here, range)).min(Comparator.comparingDouble(pos -> pos.distSqr(here)));
            if (bell.isEmpty()) continue;
            Map<Item, Integer> demand = demand(level, village);
            if (!demand.isEmpty()) candidates.add(new Candidate(bell.get(), demand));
        }
        candidates.sort(Comparator.comparingDouble(candidate -> candidate.bell().distSqr(here)));
        int budget = Economy.villagerBudget(Economy.spendable(level, merchant));
        int slots = freeSlots(level, merchant);
        Map<Item, Integer> wanted = new LinkedHashMap<>();
        List<BlockPos> stops = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (stops.size() >= MerchantSkill.stops(merchant.getVillagerData().level())) break;
            boolean added = false;
            for (Map.Entry<Item, Integer> need : candidate.demand().entrySet()) {
                Optional<Price> lot = BuyFromVillagers.homeOffer(level, merchant, need.getKey(), budget);
                if (lot.isEmpty()) continue;
                int lots = Math.min(Math.max(1, need.getValue() / lot.get().count()), budget / lot.get().emeralds());
                int amount = lots * lot.get().count();
                int stacks = (amount + need.getKey().getDefaultMaxStackSize() - 1) / need.getKey().getDefaultMaxStackSize();
                if (lots <= 0 || stacks > slots) continue;
                budget -= lots * lot.get().emeralds();
                slots -= stacks;
                wanted.merge(need.getKey(), amount, Integer::sum);
                added = true;
            }
            if (added) stops.add(candidate.bell());
        }
        if (stops.isEmpty()) return Optional.empty();
        return Optional.of(new Plan(stops, wanted.entrySet().stream().map(entry -> new Tour.Want(entry.getKey(), entry.getValue())).toList()));
    }

    /** What the villagers of {@code village} are short of and nobody there sells. */
    static Map<Item, Integer> demand(ServerLevel level, Villages.Village village) {
        Map<Item, Integer> demand = new LinkedHashMap<>();
        for (Villager villager : village.villagers(level)) {
            for (BuyFromVillagers.Need need : BuyFromVillagers.needs(level, villager)) demand.merge(need.item(), need.room(), Integer::sum);
        }
        return demand;
    }

    private static int freeSlots(ServerLevel level, Villager merchant) {
        int free = 0;
        for (var stack : merchant.getInventory().getItems()) if (stack.isEmpty()) free++;
        for (Container pack : Merchant.packs(level, merchant)) {
            for (int i = 0; i < pack.getContainerSize(); i++) if (pack.getItem(i).isEmpty()) free++;
        }
        return free;
    }

    /** One step of a tour, every tick while it lasts. */
    static void step(ServerLevel level, Villager merchant) {
        Optional<Tour> current = merchant.getData(Merchant.TOUR);
        if (current.isEmpty()) return;
        Tour tour = current.get();
        long now = level.getGameTime();
        if (tour.phase() != Tour.Phase.SHOP && Camp.step(level, merchant)) return;
        switch (tour.phase()) {
            case SHOP -> {
                if (!Merchant.shoppingList(level, merchant).isEmpty() && now - tour.since() <= SHOP_TICKS) return;
                if (Camp.fetchInvisibility(level, merchant, tour, now)) return;
                if (tour.wanted().stream().noneMatch(want -> Merchant.carried(level, merchant, want.item()) > 0)) {
                    finish(level, merchant, tour, MIN_REST);
                    return;
                }
                leash(level, merchant);
                merchant.setData(Merchant.TOUR, Optional.of(tour.in(Tour.Phase.TRAVEL, now)));
            }
            case TRAVEL -> {
                keepLoaded(level, merchant);
                BlockPos bell = tour.stops().get(tour.next());
                if (merchant.blockPosition().closerThan(bell, ARRIVE)) {
                    merchant.setData(Merchant.TOUR, Optional.of(tour.in(Tour.Phase.MARKET, now)));
                    Exports.announce(now);
                } else if (now - tour.since() > TRAVEL_TICKS) {
                    merchant.setData(Merchant.TOUR, Optional.of(tour.in(Tour.Phase.HOME, now)));
                } else if (level.getOverworldClockTime() % 24000 < DUSK) {
                    walk(merchant, bell, now);
                }
            }
            case MARKET -> {
                keepLoaded(level, merchant);
                BlockPos bell = tour.stops().get(tour.next());
                if (now % 100 == 0) Exports.announce(now);
                boolean soldOut = tour.wanted().stream().noneMatch(want -> Merchant.carried(level, merchant, want.item()) > 0);
                if (soldOut || now - tour.since() > MARKET_TICKS) {
                    int next = tour.next() + 1;
                    merchant.setData(Merchant.TOUR, Optional.of(next < tour.stops().size() && !soldOut ? tour.atStop(next, Tour.Phase.TRAVEL, now) : tour.in(Tour.Phase.HOME, now)));
                } else if (!merchant.blockPosition().closerThan(bell, ARRIVE)) {
                    walk(merchant, bell, now);
                }
            }
            case HOME -> {
                keepLoaded(level, merchant);
                if (merchant.blockPosition().closerThan(tour.home(), ARRIVE)) finish(level, merchant, tour, (int) Math.max(MIN_REST, now - tour.started()));
                else if (level.getOverworldClockTime() % 24000 < DUSK) walk(merchant, tour.home(), now);
            }
        }
    }

    private static void finish(ServerLevel level, Villager merchant, Tour tour, int rest) {
        for (Mule mule : Merchant.ownedMules(level, merchant, merchant.getBoundingBox().inflate(64))) mule.dropLeash();
        merchant.setData(Merchant.RESTED_AT, level.getGameTime() + rest);
        merchant.setData(Merchant.TOUR, Optional.empty());
        forget(merchant);
    }

    private static void leash(ServerLevel level, Villager merchant) {
        for (Mule mule : Merchant.ownedMules(level, merchant, merchant.getBoundingBox().inflate(32))) {
            mule.clearHome();
            mule.setLeashedTo(merchant, true);
        }
    }

    private static void walk(Villager merchant, BlockPos target, long now) {
        if (now % 10 == 0 || !com.sheepfromheaven.minenow.villager.Walking.towards(merchant, target)) {
            BehaviorUtils.setWalkAndLookTargetMemories(merchant, target, 0.5F, 2);
        }
    }

    private static void keepLoaded(ServerLevel level, Villager merchant) {
        ChunkPos now = merchant.chunkPosition();
        ChunkPos before = LAST.put(merchant.getUUID(), now);
        if (now.equals(before)) return;
        for (int dx = -KEEP_RADIUS; dx <= KEEP_RADIUS; dx++) {
            for (int dz = -KEEP_RADIUS; dz <= KEEP_RADIUS; dz++) TICKETS.forceChunk(level, merchant, now.x() + dx, now.z() + dz, true, true);
        }
        if (before != null) unforce(level, merchant, before, now);
    }

    private static void release(ServerLevel level, Villager merchant) {
        ChunkPos last = LAST.remove(merchant.getUUID());
        if (last != null) unforce(level, merchant, last, null);
    }

    private static void unforce(ServerLevel level, Villager merchant, ChunkPos old, ChunkPos keep) {
        for (int dx = -KEEP_RADIUS; dx <= KEEP_RADIUS; dx++) {
            for (int dz = -KEEP_RADIUS; dz <= KEEP_RADIUS; dz++) {
                int x = old.x() + dx;
                int z = old.z() + dz;
                if (keep != null && Math.abs(x - keep.x()) <= KEEP_RADIUS && Math.abs(z - keep.z()) <= KEEP_RADIUS) continue;
                TICKETS.forceChunk(level, merchant, x, z, false, true);
            }
        }
    }
}
