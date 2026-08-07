package com.sheepfromheaven.minenow.jobs.charcoalburner;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Item checks for the charcoal burner: whether a stack is a log the furnace can smelt into
 * charcoal, and whether it's valid furnace fuel. Logs also burn as their own fuel, so the same
 * barrel can supply both the smelting input and the fuel.
 */
public final class CharcoalRecipes {
    private CharcoalRecipes() {}

    public static boolean isLog(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ItemTags.LOGS);
    }

    public static boolean isFuel(Level level, ItemStack stack) {
        return !stack.isEmpty() && level.fuelValues().isFuel(stack);
    }
}
