package com.sheepfromheaven.minenow.villager;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

import java.util.function.Predicate;

/**
 * A villager working its furnace-like workstation: it takes out what's done, puts in what it carries that the furnace
 * can cook (and that {@code cooks} accepts), and adds just the fuel that load needs (coal or charcoal, else logs or planks), so the rest can fuel other furnaces.
 */
public final class FurnaceTending {
    private static final int INPUT = 0;
    private static final int FUEL = 1;
    private static final int RESULT = 2;

    private FurnaceTending() {}

    public static boolean needsTending(ServerLevel level, Villager body, BlockPos pos, RecipeType<? extends AbstractCookingRecipe> type, Predicate<ItemStack> cooks) {
        return needsTending(level, body, pos, type, cooks, Integer.MAX_VALUE);
    }

    /** Like the plain version, but only puts in up to {@code share} items, so a villager can spread its goods over several furnaces. */
    public static boolean needsTending(ServerLevel level, Villager body, BlockPos pos, RecipeType<? extends AbstractCookingRecipe> type, Predicate<ItemStack> cooks, int share) {
        if (!(level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace)) return false;
        if (!furnace.getItem(RESULT).isEmpty()) return true;
        boolean carriesFuel = body.getInventory().hasAnyMatching(FurnaceTending::isFuel);
        ItemStack input = furnace.getItem(INPUT);
        if (!input.isEmpty() && furnace.getItem(FUEL).isEmpty() && !lit(level, pos) && carriesFuel) return true;
        boolean fuelled = !furnace.getItem(FUEL).isEmpty() || carriesFuel;
        if (!fuelled) return false;
        return body.getInventory().hasAnyMatching(stack -> cookable(level, stack, type, cooks)
                && (input.isEmpty() || (ItemStack.isSameItemSameComponents(input, stack) && input.getCount() < Math.min(share, input.getMaxStackSize()))));
    }

    public static void tend(ServerLevel level, Villager body, BlockPos pos, RecipeType<? extends AbstractCookingRecipe> type, Predicate<ItemStack> cooks) {
        tend(level, body, pos, type, cooks, Integer.MAX_VALUE);
    }

    public static void tend(ServerLevel level, Villager body, BlockPos pos, RecipeType<? extends AbstractCookingRecipe> type, Predicate<ItemStack> cooks, int share) {
        if (!(level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace)) return;
        ItemStack result = furnace.getItem(RESULT);
        if (!result.isEmpty()) {
            furnace.setItem(RESULT, body.getInventory().addItem(result.copy()));
            WorkXp.award(body, WorkXp.FURNACE_OUTPUT);
        }
        for (ItemStack stack : body.getInventory().getItems()) {
            if (!cookable(level, stack, type, cooks)) continue;
            ItemStack input = furnace.getItem(INPUT);
            if (input.isEmpty()) {
                furnace.setItem(INPUT, stack.split(Math.min(share, stack.getCount())));
            } else if (ItemStack.isSameItemSameComponents(input, stack)) {
                int moved = Math.min(stack.getCount(), Math.min(share, input.getMaxStackSize()) - input.getCount());
                if (moved <= 0) continue;
                input.grow(moved);
                stack.shrink(moved);
            }
        }
        boolean burningOn = furnace.getItem(FUEL).isEmpty() && lit(level, pos);
        if (!burningOn && (furnace.getItem(FUEL).isEmpty() || isFuel(furnace.getItem(FUEL)))) {
            ItemStack fuel = furnace.getItem(FUEL).isEmpty() ? bestFuel(body) : furnace.getItem(FUEL);
            if (!fuel.isEmpty()) {
                double itemsPerFuel = fuel.is(ItemTags.COALS) ? 8.0 : 1.5;
                int needed = (int) Math.ceil(furnace.getItem(INPUT).getCount() / itemsPerFuel) - furnace.getItem(FUEL).getCount();
                for (ItemStack stack : body.getInventory().getItems()) {
                    if (needed <= 0) break;
                    if (!ItemStack.isSameItemSameComponents(stack, fuel)) continue;
                    ItemStack added = stack.split(Math.min(needed, stack.getCount()));
                    if (furnace.getItem(FUEL).isEmpty()) furnace.setItem(FUEL, added);
                    else furnace.getItem(FUEL).grow(added.getCount());
                    needed -= added.getCount();
                }
            }
        }
        furnace.setChanged();
    }

    /** Coal or charcoal, or failing that logs or planks, which burn like a player's furnace burns them: a smoker needs no coal mine. */
    static boolean isFuel(ItemStack stack) {
        return stack.is(ItemTags.COALS) || stack.is(ItemTags.LOGS_THAT_BURN) || stack.is(ItemTags.PLANKS);
    }

    private static ItemStack bestFuel(Villager body) {
        ItemStack wood = ItemStack.EMPTY;
        for (ItemStack stack : body.getInventory().getItems()) {
            if (stack.is(ItemTags.COALS)) return stack;
            if (stack.is(ItemTags.LOGS_THAT_BURN) && (wood.isEmpty() || wood.is(ItemTags.PLANKS))) wood = stack;
            else if (wood.isEmpty() && stack.is(ItemTags.PLANKS)) wood = stack;
        }
        return wood;
    }

    private static boolean lit(ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        return state.hasProperty(AbstractFurnaceBlock.LIT) && state.getValue(AbstractFurnaceBlock.LIT);
    }

    private static boolean cookable(ServerLevel level, ItemStack stack, RecipeType<? extends AbstractCookingRecipe> type, Predicate<ItemStack> cooks) {
        return !stack.isEmpty() && cooks.test(stack) && level.recipeAccess().getRecipeFor(type, new SingleRecipeInput(stack), level).isPresent();
    }
}
