package com.sheepfromheaven.minenow.villager;

import com.sheepfromheaven.minenow.MineNowConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Two awake villagers within {@link #DISTANCE} blocks of each other in the MEET or IDLE activity start a chat, which goes on while they stay within {@link #EARSHOT} in speech bubbles:
 * one shows an icon, the other answers {@link #TURN_TICKS} later, {@link #EXCHANGES} exchanges, then neither chats for
 * {@link #COOLDOWN_TICKS}. A villager talks about what is new today (a death or a birth in its village, its new job, what
 * it just bought), else about its trade (a product it sells that it holds), one topic per turn.
 */
public final class ChatBubbles {
    static final int DISTANCE = 3;
    static final int EARSHOT = 8;
    static final int TURN_TICKS = 40;
    static final int SHOWN_TICKS = 40;
    static final int EXCHANGES = 3;
    static final int COOLDOWN_TICKS = 1200;
    static final long TODAY = 24000L;
    private static final int SCAN_INTERVAL = 20;

    private record News(Set<BlockPos> bells, Item icon, long at) {}

    private record Bought(Item item, long at) {}

    private record Job(Holder<VillagerProfession> profession, long since) {}

    private static final class Chat {
        final Villager first;
        final Villager second;
        final long start;
        int turn;

        Chat(Villager first, Villager second, long start) {
            this.first = first;
            this.second = second;
            this.start = start;
        }
    }

    private static final Map<ServerLevel, List<News>> NEWS = new WeakHashMap<>();
    private static final Map<ServerLevel, List<Chat>> CHATS = new WeakHashMap<>();
    private static final Map<Villager, Chat> BUSY = new WeakHashMap<>();
    private static final Map<Villager, Bought> BOUGHT = new WeakHashMap<>();
    private static final Map<Villager, Job> JOBS = new WeakHashMap<>();
    private static final Map<Villager, Long> QUIET_UNTIL = new WeakHashMap<>();

    private ChatBubbles() {}

    public static void noteDeath(ServerLevel level, Villager villager) {
        note(level, villager, Items.POPPY);
    }

    public static void noteBirth(ServerLevel level, Villager villager) {
        note(level, villager, Items.CAKE);
    }

    private static void note(ServerLevel level, Villager villager, Item icon) {
        Villages.of(level, villager).or(() -> Villages.at(level, villager.blockPosition())).ifPresent(village -> {
            List<News> news = NEWS.computeIfAbsent(level, l -> new ArrayList<>());
            news.removeIf(item -> level.getGameTime() - item.at() >= TODAY);
            news.add(new News(village.bells(), icon, level.getGameTime()));
        });
    }

    public static void bought(Villager buyer, Item item) {
        BOUGHT.put(buyer, new Bought(item, buyer.level().getGameTime()));
    }

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!MineNowConfig.enabled(MineNowConfig.Feature.SPEECH_BUBBLES)) {
            end(level);
            return;
        }
        long now = level.getGameTime();
        List<Chat> chats = CHATS.get(level);
        if (chats != null) chats.removeIf(chat -> advance(level, chat, now));
        if (now % SCAN_INTERVAL == 0) scan(level, now);
    }

    private static void end(ServerLevel level) {
        List<Chat> chats = CHATS.remove(level);
        if (chats != null) chats.forEach(chat -> {
            BUSY.remove(chat.first);
            BUSY.remove(chat.second);
        });
    }

    /** Returns true when the chat is over. */
    private static boolean advance(ServerLevel level, Chat chat, long now) {
        if (!chatting(chat.first) || !chatting(chat.second) || chat.first.distanceToSqr(chat.second) > EARSHOT * EARSHOT) return finish(chat, now);
        if (now < chat.start + (long) chat.turn * TURN_TICKS) return false;
        Villager speaker = chat.turn % 2 == 0 ? chat.first : chat.second;
        List<Item> topics = topics(level, speaker);
        if (topics.isEmpty()) return finish(chat, now);
        SpeechBubbles.show(speaker, new ItemStack(topics.get((chat.turn / 2) % topics.size())), SHOWN_TICKS);
        return ++chat.turn >= 2 * EXCHANGES && finish(chat, now);
    }

    private static boolean finish(Chat chat, long now) {
        BUSY.remove(chat.first);
        BUSY.remove(chat.second);
        long until = now + TURN_TICKS + COOLDOWN_TICKS;
        QUIET_UNTIL.put(chat.first, until);
        QUIET_UNTIL.put(chat.second, until);
        return true;
    }

    private static boolean chatting(Villager villager) {
        return villager.isAlive() && !villager.isBaby() && !villager.isSleeping() && !villager.isTrading() && inChatActivity(villager);
    }

    private static boolean inChatActivity(Villager villager) {
        return villager.getBrain().isActive(Activity.MEET) || villager.getBrain().isActive(Activity.IDLE);
    }

    private static void scan(ServerLevel level, long now) {
        List<Villager> all = new ArrayList<>(level.getEntities(EntityTypeTest.<net.minecraft.world.entity.Entity, Villager>forClass(Villager.class), Villager::isAlive));
        List<Villager> ready = new ArrayList<>();
        for (Villager villager : all) {
            Holder<VillagerProfession> profession = villager.getVillagerData().profession();
            Job job = JOBS.get(villager);
            if (job == null) JOBS.put(villager, new Job(profession, Long.MIN_VALUE));
            else if (!job.profession().equals(profession)) JOBS.put(villager, new Job(profession, profession.is(VillagerProfession.NONE) ? Long.MIN_VALUE : now));
            if (villager.entityTags().contains(SpeechBubbles.SHOWCASE_TAG) || BUSY.containsKey(villager) || now < QUIET_UNTIL.getOrDefault(villager, 0L) || !chatting(villager)) continue;
            if (level.getNearestPlayer(villager, SpeechBubbles.RANGE) == null || topics(level, villager).isEmpty()) continue;
            ready.add(villager);
        }
        for (Villager villager : ready) {
            if (BUSY.containsKey(villager)) continue;
            Villager partner = null;
            double best = DISTANCE * DISTANCE + 1e-6;
            for (Villager other : ready) {
                if (other == villager || BUSY.containsKey(other)) continue;
                double distance = villager.distanceToSqr(other);
                if (distance <= best) {
                    best = distance;
                    partner = other;
                }
            }
            if (partner == null) continue;
            Chat chat = new Chat(villager, partner, now);
            CHATS.computeIfAbsent(level, l -> new ArrayList<>()).add(chat);
            BUSY.put(villager, chat);
            BUSY.put(partner, chat);
        }
    }

    /** What the villager has to say: what is new today, else the products of its trade that it holds. */
    static List<Item> topics(ServerLevel level, Villager villager) {
        long now = level.getGameTime();
        List<Item> topics = new ArrayList<>();
        Villages.of(level, villager).ifPresent(village -> {
            for (News news : NEWS.getOrDefault(level, List.of())) {
                if (now - news.at() < TODAY && village.bells().stream().anyMatch(news.bells()::contains) && !topics.contains(news.icon())) topics.add(news.icon());
            }
        });
        Job job = JOBS.get(villager);
        if (job != null && job.since() != Long.MIN_VALUE && now - job.since() < TODAY) {
            Item block = NeedBubbles.jobBlockIcon(villager);
            if (block != null) topics.add(block);
        }
        Bought bought = BOUGHT.get(villager);
        if (bought != null && now - bought.at() < TODAY) topics.add(bought.item());
        if (!topics.isEmpty()) return topics;
        TradeTable.forVillager(villager).ifPresent(table -> {
            for (TradeTable.Produce produce : table.produceUpTo(villager.getVillagerData().level())) {
                if (!topics.contains(produce.item()) && Economy.count(level, villager, produce.item()) > 0) topics.add(produce.item());
            }
        });
        return topics;
    }
}
