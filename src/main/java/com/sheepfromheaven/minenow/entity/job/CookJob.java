package com.sheepfromheaven.minenow.entity.job;

import com.sheepfromheaven.minenow.dominion.DominionStructure;
import com.sheepfromheaven.minenow.entity.InhabitantEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.SingleRecipeInput;

import javax.annotation.Nullable;

/**
 * Goes to the tavern, finds raw food and fuel in storage, cooks the food into a meal using
 * recipe-manager-driven smoking recipes (see {@link CookingRecipes} - so modded food/fuel work
 * without any changes here), and deposits the cooked result back into storage for hungry
 * inhabitants to eat.
 */
public class CookJob implements Job {
    private static final int ARRIVE_DISTANCE_SQ = 4;
    private static final int REPATH_INTERVAL = 60;
    private static final double WALK_SPEED = 0.6;
    private static final int MISSING_INGREDIENTS_RETRY_TICKS = 100;

    private enum Phase { GOTO_HUT, FIND_INGREDIENTS, COOKING, DEPOSIT, MISSING_INGREDIENTS }

    private Phase phase = Phase.GOTO_HUT;
    private int waitTicks;
    private int repathCooldown;
    @Nullable
    private ItemStack cookingResult;
    @Nullable
    private Item missingItem;

    @Override
    public boolean canStart(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        return isDaytime(level) || phase != Phase.GOTO_HUT;
    }

    @Override
    public void start(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        // Resume wherever the last cycle left off; state persists across interruptions.
    }

    @Override
    public void stop(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        inhabitant.getNavigation().stop();
    }

    @Override
    public void onInterrupted(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        // Whatever pulled control away (going to sleep, eating, ...) may have moved the inhabitant away
        // from the hut - re-approach it rather than resuming cooking in place. Any ingredients already
        // pulled from storage for an in-progress cook are lost, same as on a save/reload (see load()).
        cookingResult = null;
        phase = Phase.GOTO_HUT;
    }

    @Override
    public boolean tick(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        return switch (phase) {
            case GOTO_HUT -> tickGotoHut(inhabitant, workplace);
            case FIND_INGREDIENTS -> tickFindIngredients(inhabitant, level, workplace);
            case COOKING -> tickCooking(inhabitant);
            case DEPOSIT -> tickDeposit(inhabitant, level, workplace);
            case MISSING_INGREDIENTS -> tickMissingIngredients();
        };
    }

    private boolean tickGotoHut(InhabitantEntity inhabitant, DominionStructure workplace) {
        if (moveTowardsAndCheckArrival(inhabitant, workplace.getMarkerPos())) {
            phase = Phase.FIND_INGREDIENTS;
        }
        return true;
    }

    private boolean tickFindIngredients(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        boolean hasFood = false;
        boolean hasFuel = false;
        for (var pos : workplace.findStorageContainers(level)) {
            if (!(level.getBlockEntity(pos) instanceof Container container)) continue;
            for (int i = 0; i < container.getContainerSize(); i++) {
                var stack = container.getItem(i);
                if (stack.isEmpty()) continue;
                if (!hasFood && CookingRecipes.findSmokingRecipe(level, stack).isPresent()) hasFood = true;
                if (!hasFuel && CookingRecipes.isFuel(level, stack)) hasFuel = true;
            }
            if (hasFood && hasFuel) break;
        }

        if (!hasFood || !hasFuel) {
            missingItem = !hasFood ? Items.BEEF : Items.OAK_LOG;
            phase = Phase.MISSING_INGREDIENTS;
            waitTicks = MISSING_INGREDIENTS_RETRY_TICKS;
            return true;
        }

        var rawFood = ItemStack.EMPTY;
        var fuel = ItemStack.EMPTY;
        for (var pos : workplace.findStorageContainers(level)) {
            if (!(level.getBlockEntity(pos) instanceof Container container)) continue;
            if (rawFood.isEmpty()) {
                rawFood = ContainerUtil.extractOne(container, stack -> CookingRecipes.findSmokingRecipe(level, stack).isPresent());
            }
            if (fuel.isEmpty()) {
                fuel = ContainerUtil.extractOne(container, stack -> CookingRecipes.isFuel(level, stack));
            }
            if (!rawFood.isEmpty() && !fuel.isEmpty()) break;
        }

        var recipe = CookingRecipes.findSmokingRecipe(level, rawFood);
        if (rawFood.isEmpty() || fuel.isEmpty() || recipe.isEmpty()) {
            // Storage changed between the peek above and this extraction pass - retry next cycle.
            phase = Phase.MISSING_INGREDIENTS;
            waitTicks = MISSING_INGREDIENTS_RETRY_TICKS;
            return true;
        }

        cookingResult = recipe.get().value().assemble(new SingleRecipeInput(rawFood), level.registryAccess());
        missingItem = null;
        waitTicks = Math.max(1, recipe.get().value().cookingTime());
        inhabitant.swing(InteractionHand.MAIN_HAND);
        phase = Phase.COOKING;
        return true;
    }

    private boolean tickCooking(InhabitantEntity inhabitant) {
        if (--waitTicks > 0) {
            return true;
        }
        if (cookingResult != null) {
            inhabitant.getInventory().addItem(cookingResult.copy());
        }
        cookingResult = null;
        phase = Phase.DEPOSIT;
        return true;
    }

    private boolean tickDeposit(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        var carried = inhabitant.getInventory();
        for (var pos : workplace.findStorageContainers(level)) {
            if (level.getBlockEntity(pos) instanceof Container container) {
                for (int i = 0; i < carried.getContainerSize(); i++) {
                    var stack = carried.getItem(i);
                    if (!stack.isEmpty()) {
                        carried.setItem(i, ContainerUtil.insertItem(container, stack));
                    }
                }
            }
        }
        phase = Phase.GOTO_HUT;
        return false;
    }

    private boolean tickMissingIngredients() {
        if (--waitTicks <= 0) {
            phase = Phase.FIND_INGREDIENTS;
        }
        return true;
    }

    private boolean moveTowardsAndCheckArrival(InhabitantEntity inhabitant, net.minecraft.core.BlockPos target) {
        boolean arrived = inhabitant.distanceToSqr(target.getX() + 0.5, target.getY(), target.getZ() + 0.5) <= ARRIVE_DISTANCE_SQ;
        if (arrived) {
            inhabitant.getNavigation().stop();
            return true;
        }
        if (inhabitant.getNavigation().isDone() || --repathCooldown <= 0) {
            inhabitant.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, WALK_SPEED);
            repathCooldown = REPATH_INTERVAL;
        }
        return false;
    }

    private boolean isDaytime(ServerLevel level) {
        long dayTime = level.getDayTime() % 24000;
        return dayTime < 12542 || dayTime >= 23460;
    }

    @Override
    public String getStatusDescription() {
        return switch (phase) {
            case GOTO_HUT -> "Heading to work";
            case FIND_INGREDIENTS -> "Gathering ingredients";
            case COOKING -> "Cooking a meal";
            case DEPOSIT -> "Storing meal";
            case MISSING_INGREDIENTS -> "Needs raw food and fuel";
        };
    }

    @Override
    @Nullable
    public Item getMissingItem() {
        return missingItem;
    }

    @Override
    public void save(CompoundTag tag) {
        tag.putString("Phase", phase.name());
        tag.putInt("WaitTicks", waitTicks);
        if (missingItem != null) tag.putString("MissingItem", BuiltInRegistries.ITEM.getKey(missingItem).toString());
    }

    @Override
    public void load(CompoundTag tag) {
        try {
            phase = Phase.valueOf(tag.getStringOr("Phase", Phase.GOTO_HUT.name()));
        } catch (IllegalArgumentException e) {
            phase = Phase.GOTO_HUT;
        }
        if (phase == Phase.COOKING) {
            // The in-progress cooking result can't be persisted across a reload; restart the cycle.
            phase = Phase.FIND_INGREDIENTS;
        }
        waitTicks = tag.getIntOr("WaitTicks", 0);
        missingItem = tag.contains("MissingItem")
                ? BuiltInRegistries.ITEM.getValue(Identifier.parse(tag.getStringOr("MissingItem", "")))
                : null;
    }
}
