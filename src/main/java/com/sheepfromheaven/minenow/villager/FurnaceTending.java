package com.sheepfromheaven.minenow.villager;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

import java.util.function.Predicate;

/**
 * A villager working its furnace-like workstation: it takes out what's done, puts in what it carries that the furnace
 * can cook (and that {@code cooks} accepts), and adds coal it holds when the fuel slot is empty.
 */
public final class FurnaceTending {
    private static final int INPUT = 0;
    private static final int FUEL = 1;
    private static final int RESULT = 2;

    private FurnaceTending() {}

    public static boolean needsTending(ServerLevel level, Villager body, BlockPos pos, RecipeType<? extends AbstractCookingRecipe> type, Predicate<ItemStack> cooks) {
        if (!(level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace)) return false;
        if (!furnace.getItem(RESULT).isEmpty()) return true;
        boolean fuelled = !furnace.getItem(FUEL).isEmpty() || body.getInventory().hasAnyMatching(stack -> stack.is(ItemTags.COALS));
        if (!fuelled) return false;
        ItemStack input = furnace.getItem(INPUT);
        return body.getInventory().hasAnyMatching(stack -> cookable(level, stack, type, cooks)
                && (input.isEmpty() || (ItemStack.isSameItemSameComponents(input, stack) && input.getCount() < input.getMaxStackSize())));
    }

    public static void tend(ServerLevel level, Villager body, BlockPos pos, RecipeType<? extends AbstractCookingRecipe> type, Predicate<ItemStack> cooks) {
        if (!(level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace)) return;
        ItemStack result = furnace.getItem(RESULT);
        if (!result.isEmpty()) furnace.setItem(RESULT, body.getInventory().addItem(result.copy()));
        for (ItemStack stack : body.getInventory().getItems()) {
            if (!cookable(level, stack, type, cooks)) continue;
            ItemStack input = furnace.getItem(INPUT);
            if (input.isEmpty()) {
                furnace.setItem(INPUT, stack.split(stack.getCount()));
            } else if (ItemStack.isSameItemSameComponents(input, stack)) {
                int moved = Math.min(stack.getCount(), input.getMaxStackSize() - input.getCount());
                input.grow(moved);
                stack.shrink(moved);
            }
        }
        if (furnace.getItem(FUEL).isEmpty()) {
            for (ItemStack stack : body.getInventory().getItems()) {
                if (stack.is(ItemTags.COALS)) {
                    furnace.setItem(FUEL, stack.split(stack.getCount()));
                    break;
                }
            }
        }
        furnace.setChanged();
    }

    private static boolean cookable(ServerLevel level, ItemStack stack, RecipeType<? extends AbstractCookingRecipe> type, Predicate<ItemStack> cooks) {
        return !stack.isEmpty() && cooks.test(stack) && level.recipeAccess().getRecipeFor(type, new SingleRecipeInput(stack), level).isPresent();
    }
}
