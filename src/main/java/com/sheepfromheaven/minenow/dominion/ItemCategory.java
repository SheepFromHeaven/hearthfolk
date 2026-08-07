package com.sheepfromheaven.minenow.dominion;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;

/**
 * Named item categories used in category-style {@link StructureRequest}s. Each variant knows how
 * to test whether a given stack satisfies the category so the courier can scan storehouses and pick
 * a concrete item to deliver without the issuing job having to pre-identify one.
 */
public enum ItemCategory {
    RAW_FOOD {
        @Override
        public boolean matches(ItemStack stack, ServerLevel level) {
            if (stack.isEmpty()) return false;
            return level.getServer().getRecipeManager()
                    .getRecipeFor(RecipeType.SMOKING, new SingleRecipeInput(stack), level)
                    .isPresent();
        }
    },
    FUEL {
        @Override
        public boolean matches(ItemStack stack, ServerLevel level) {
            return !stack.isEmpty() && level.fuelValues().isFuel(stack);
        }
    },
    LOGS {
        @Override
        public boolean matches(ItemStack stack, ServerLevel level) {
            return !stack.isEmpty() && stack.is(ItemTags.LOGS);
        }
    };

    public abstract boolean matches(ItemStack stack, ServerLevel level);
}
