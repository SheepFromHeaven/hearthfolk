package com.sheepfromheaven.minenow.entity.job;

import com.sheepfromheaven.minenow.dominion.DominionStructure;
import com.sheepfromheaven.minenow.entity.InhabitantEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;

import javax.annotation.Nullable;

/**
 * Goes to the tavern, finds raw food and fuel in storage, loads them into the tavern's actual
 * Smoker block (so vanilla furnace mechanics do the real cooking - see {@link CookingRecipes} for
 * the recipe-manager-driven checks that make modded food/fuel work without any changes here), and
 * deposits the cooked result back into storage once the smoker produces it.
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
    private String missingItemLabel;

    @Override
    public boolean canStart(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        return isDaytime(level) || phase != Phase.GOTO_HUT;
    }

    @Override
    public void start(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        // Resume wherever the last cycle left off; state persists across interruptions. Any food/fuel
        // already loaded into the smoker keeps cooking there regardless, since it's a real block entity.
    }

    @Override
    public void stop(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        inhabitant.getNavigation().stop();
    }

    @Override
    public void onInterrupted(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        // Whatever pulled control away (going to sleep, eating, ...) may have moved the inhabitant away
        // from the hut - re-approach it rather than resuming in place. Nothing is lost: any food/fuel
        // already loaded stays in the smoker, and the result (if any) stays in its output slot.
        phase = Phase.GOTO_HUT;
    }

    @Override
    public boolean tick(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        return switch (phase) {
            case GOTO_HUT -> tickGotoHut(inhabitant, level, workplace);
            case FIND_INGREDIENTS -> tickFindIngredients(inhabitant, level, workplace);
            case COOKING -> tickCooking(inhabitant, level, workplace);
            case DEPOSIT -> tickDeposit(inhabitant, level, workplace);
            case MISSING_INGREDIENTS -> tickMissingIngredients();
        };
    }

    private boolean tickGotoHut(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        if (moveTowardsAndCheckArrival(inhabitant, workplace.findApproachPos(level))) {
            phase = Phase.FIND_INGREDIENTS;
        }
        return true;
    }

    private boolean tickFindIngredients(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        var smoker = findSmoker(level, workplace);
        if (smoker == null) {
            missingItemLabel = "a Smoker";
            phase = Phase.MISSING_INGREDIENTS;
            waitTicks = MISSING_INGREDIENTS_RETRY_TICKS;
            return true;
        }

        if (smoker.getItem(0).isEmpty()) {
            var rawFood = extractFromStorage(level, workplace, stack -> CookingRecipes.findSmokingRecipe(level, stack).isPresent());
            if (rawFood.isEmpty()) {
                missingItemLabel = "raw food";
                phase = Phase.MISSING_INGREDIENTS;
                waitTicks = MISSING_INGREDIENTS_RETRY_TICKS;
                return true;
            }
            smoker.setItem(0, rawFood);
        }

        if (smoker.getItem(1).isEmpty()) {
            var fuel = extractFromStorage(level, workplace, stack -> CookingRecipes.isFuel(level, stack));
            if (fuel.isEmpty()) {
                missingItemLabel = "fuel";
                phase = Phase.MISSING_INGREDIENTS;
                waitTicks = MISSING_INGREDIENTS_RETRY_TICKS;
                return true;
            }
            smoker.setItem(1, fuel);
        }

        missingItemLabel = null;
        inhabitant.swing(InteractionHand.MAIN_HAND);
        phase = Phase.COOKING;
        return true;
    }

    private boolean tickCooking(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        var smokerPos = workplace.findBlock(level, state -> state.is(Blocks.SMOKER));
        var smoker = smokerPos.isPresent() && level.getBlockEntity(smokerPos.get()) instanceof Container container ? container : null;
        if (smoker == null) {
            // Smoker was destroyed mid-cook; give up on this cycle and re-check requirements.
            phase = Phase.FIND_INGREDIENTS;
            return true;
        }
        var result = smoker.getItem(2);
        if (result.isEmpty()) {
            boolean lit = level.getBlockState(smokerPos.get()).getValue(AbstractFurnaceBlock.LIT);
            if (!lit && smoker.getItem(1).isEmpty()) {
                // Ran out of fuel before the food finished cooking; go fetch more instead of waiting forever.
                phase = Phase.FIND_INGREDIENTS;
            }
            return true;
        }
        inhabitant.getInventory().addItem(smoker.removeItem(2, result.getCount()));
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

    @Nullable
    private Container findSmoker(ServerLevel level, DominionStructure workplace) {
        var pos = workplace.findBlock(level, state -> state.is(Blocks.SMOKER));
        if (pos.isEmpty()) return null;
        return level.getBlockEntity(pos.get()) instanceof Container container ? container : null;
    }

    private net.minecraft.world.item.ItemStack extractFromStorage(ServerLevel level, DominionStructure workplace,
                                                                    java.util.function.Predicate<net.minecraft.world.item.ItemStack> matcher) {
        for (var pos : workplace.findStorageContainers(level)) {
            if (!(level.getBlockEntity(pos) instanceof Container container)) continue;
            var stack = ContainerUtil.extractOne(container, matcher);
            if (!stack.isEmpty()) return stack;
        }
        return net.minecraft.world.item.ItemStack.EMPTY;
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
        return null;
    }

    @Override
    @Nullable
    public String getMissingItemLabel() {
        return missingItemLabel;
    }

    @Override
    public void save(CompoundTag tag) {
        tag.putString("Phase", phase.name());
        tag.putInt("WaitTicks", waitTicks);
        if (missingItemLabel != null) tag.putString("MissingItemLabel", missingItemLabel);
    }

    @Override
    public void load(CompoundTag tag) {
        try {
            phase = Phase.valueOf(tag.getStringOr("Phase", Phase.GOTO_HUT.name()));
        } catch (IllegalArgumentException e) {
            phase = Phase.GOTO_HUT;
        }
        waitTicks = tag.getIntOr("WaitTicks", 0);
        missingItemLabel = tag.contains("MissingItemLabel") ? tag.getStringOr("MissingItemLabel", "") : null;
    }
}
