package com.sheepfromheaven.minenow.villager;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.food.VillagerFood;

/**
 * Wealth pays for children: a villager with {@link #required} emeralds or more in its goods (the {@link WealthFormula}:
 * more the more villagers its village has) buys the bread, carrots,
 * potatoes or beetroot that make vanilla villagers willing to breed whenever it carries less than
 * {@link Villager#BREEDING_FOOD_THRESHOLD} food points in its goods (its chests count, or it buys again every time it
 * unloads). Vanilla does the rest (a partner, a free bed).
 */
public final class Wealth {
    private Wealth() {}

    public static int required(ServerLevel level, Villager villager) {
        int villagers = Villages.of(level, villager).map(village -> village.members().size()).orElse(1);
        return WealthFormula.current().required(villagers);
    }

    static boolean wantsFoodForAFamily(ServerLevel level, Villager villager) {
        return !villager.isBaby() && Economy.villagerBudget(Economy.emeralds(level, villager)) >= required(level, villager) && breedingFoodPoints(level, villager) < Villager.BREEDING_FOOD_THRESHOLD;
    }

    static boolean isBreedingFood(ItemStack stack) {
        return stack.has(DataComponents.VILLAGER_FOOD);
    }

    /** What counts towards vanilla's willingness: only what the villager carries. */
    static int pocketFoodPoints(Villager villager) {
        return points(villager.getInventory());
    }

    static int chestFoodPoints(ServerLevel level, Villager villager) {
        int points = 0;
        for (net.minecraft.world.Container chest : Economy.chests(level, villager)) points += points(chest);
        return points;
    }

    private static int breedingFoodPoints(ServerLevel level, Villager villager) {
        int points = points(villager.getInventory());
        for (net.minecraft.world.Container chest : Economy.chests(level, villager)) points += points(chest);
        return points;
    }

    private static int points(net.minecraft.world.Container container) {
        int points = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            VillagerFood food = container.getItem(i).get(DataComponents.VILLAGER_FOOD);
            if (food != null) points += container.getItem(i).getCount() * food.nutrition();
        }
        return points;
    }
}
