package com.sheepfromheaven.minenow.villager;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.food.VillagerFood;

/**
 * Wealth pays for children: a villager with {@link #WEALTHY} emeralds or more in its goods buys the bread, carrots,
 * potatoes or beetroot that make vanilla villagers willing to breed whenever it carries less than
 * {@link Villager#BREEDING_FOOD_THRESHOLD} food points. Vanilla does the rest (a partner, a free bed).
 */
public final class Wealth {
    public static final int WEALTHY = 10;

    private Wealth() {}

    static boolean wantsFoodForAFamily(ServerLevel level, Villager villager) {
        return !villager.isBaby() && Economy.emeralds(level, villager) >= WEALTHY && breedingFoodPoints(villager) < Villager.BREEDING_FOOD_THRESHOLD;
    }

    static boolean isBreedingFood(ItemStack stack) {
        return stack.has(DataComponents.VILLAGER_FOOD);
    }

    private static int breedingFoodPoints(Villager villager) {
        int points = 0;
        for (ItemStack stack : villager.getInventory().getItems()) {
            VillagerFood food = stack.get(DataComponents.VILLAGER_FOOD);
            if (food != null) points += stack.getCount() * food.nutrition();
        }
        return points;
    }
}
