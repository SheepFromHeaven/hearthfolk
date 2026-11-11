package com.sheepfromheaven.minenow.villager;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Crafts whose result isn't just {@code count} of the item (a filled map has a picture of its own): a maker says when its villager can make one and what it makes. */
public final class CraftResults {
    public interface Maker {
        boolean canMake(ServerLevel level, Villager villager);

        ItemStack make(ServerLevel level, Villager villager, int count);
    }

    private record Entry(Predicate<Item> item, Maker maker) {}

    private static final List<Entry> MAKERS = new ArrayList<>();

    private CraftResults() {}

    public static void register(Predicate<Item> item, Maker maker) {
        MAKERS.add(new Entry(item, maker));
    }

    /** What a maker makes is one of a kind, so it is sold singly for whole emeralds like a tool, however well it stacks. */
    public static boolean isUnique(Item item) {
        return MAKERS.stream().anyMatch(entry -> entry.item().test(item));
    }

    public static boolean canMake(ServerLevel level, Villager villager, Item item) {
        return MAKERS.stream().filter(entry -> entry.item().test(item)).allMatch(entry -> entry.maker().canMake(level, villager));
    }

    public static ItemStack make(ServerLevel level, Villager villager, Item item, int count) {
        return MAKERS.stream().filter(entry -> entry.item().test(item)).findFirst()
                .map(entry -> entry.maker().make(level, villager, count)).orElseGet(() -> new ItemStack(item, count));
    }
}
