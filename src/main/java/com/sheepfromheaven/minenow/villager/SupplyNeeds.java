package com.sheepfromheaven.minenow.villager;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * When a supply of a trade is actually needed, beyond holding less than one batch of it: a farmer that just planted
 * all its seeds holds none, but its field is about to give it more, so seeds are only needed with nothing to plant and
 * ground waiting. A profession registers the check for the supplies it covers; supplies without one are needed as soon
 * as the villager is short of a batch. Buying (from villagers and players) and picking up ask this; unloading doesn't.
 */
public final class SupplyNeeds {
    private record Check(ResourceKey<VillagerProfession> profession, Predicate<ItemStack> covers, BiPredicate<ServerLevel, Villager> needed) {}

    private static final List<Check> CHECKS = new CopyOnWriteArrayList<>();

    private SupplyNeeds() {}

    public static void add(ResourceKey<VillagerProfession> profession, Predicate<ItemStack> covers, BiPredicate<ServerLevel, Villager> needed) {
        CHECKS.add(new Check(profession, covers, needed));
    }

    static boolean needed(ServerLevel level, Villager villager, Item item) {
        ItemStack stack = new ItemStack(item);
        for (Check check : CHECKS) {
            if (villager.getVillagerData().profession().is(check.profession()) && check.covers().test(stack)) return check.needed().test(level, villager);
        }
        return true;
    }
}
