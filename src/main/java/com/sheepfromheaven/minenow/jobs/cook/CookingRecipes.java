package com.sheepfromheaven.minenow.jobs.cook;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmokingRecipe;
import net.minecraft.world.level.Level;

import java.util.Optional;

/**
 * Recipe-manager-backed lookups for cooking: whether an item can be smoked into food, whether it's
 * valid furnace fuel, and whether a stack is ready to eat directly. Fully data-driven (vanilla
 * RecipeManager + FuelValues) so mods that add their own smoking recipes, fuels, or food items work
 * without any changes here.
 */
public final class CookingRecipes {
    private CookingRecipes() {}

    public static Optional<RecipeHolder<SmokingRecipe>> findSmokingRecipe(Level level, ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        return level.getServer().getRecipeManager().getRecipeFor(RecipeType.SMOKING, new SingleRecipeInput(stack), level);
    }

    public static boolean isFuel(Level level, ItemStack stack) {
        return !stack.isEmpty() && level.fuelValues().isFuel(stack);
    }

    /** Edible and not itself a smoking ingredient - i.e. already cooked (or never needed cooking), not a raw ingredient. */
    public static boolean isReadyToEat(Level level, ItemStack stack) {
        return !stack.isEmpty() && stack.has(DataComponents.FOOD) && findSmokingRecipe(level, stack).isEmpty();
    }
}
