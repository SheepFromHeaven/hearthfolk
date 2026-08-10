package com.sheepfromheaven.minenow.jobs.cook;

import com.sheepfromheaven.minenow.dominion.DominionSavedData;
import com.sheepfromheaven.minenow.structure.DominionStructure;
import com.sheepfromheaven.minenow.dominion.ItemCategory;
import com.sheepfromheaven.minenow.inhabitant.InhabitantEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;

import javax.annotation.Nullable;
import com.sheepfromheaven.minenow.jobs.ContainerActionPacer;
import com.sheepfromheaven.minenow.jobs.ContainerUtil;
import com.sheepfromheaven.minenow.jobs.Job;
import com.sheepfromheaven.minenow.jobs.JobActions;

/**
 * Goes to the tavern, finds raw food and fuel in storage, loads them into the tavern's actual
 * Smoker block (so vanilla furnace mechanics do the real cooking - see {@link CookingRecipes} for
 * the recipe-manager-driven checks that make modded food/fuel work without any changes here), and
 * deposits the cooked result back into storage once the smoker produces it.
 */
public class CookJob implements Job {
    private static final int MISSING_INGREDIENTS_RETRY_TICKS = 100;

    private enum Phase { GOTO_HUT, FIND_INGREDIENTS, COOKING, DEPOSIT, MISSING_INGREDIENTS }

    private Phase phase = Phase.GOTO_HUT;
    private int waitTicks;
    private final ContainerActionPacer actionPacer = new ContainerActionPacer();
    private final JobActions.Nav nav = new JobActions.Nav();
    private final JobActions.ContainerTarget containerTarget = new JobActions.ContainerTarget();
    /** Smoker slot (0 food / 1 fuel) an ingredient already picked up is destined for, or -1 if not carrying one. */
    private int pendingSmokerSlot = -1;
    /** Smoker slot the current {@link #containerTarget} trip is fetching for, mirrors {@link #pendingSmokerSlot} once loaded. */
    private int fetchSlot = -1;
    /** Both "raw food" and "fuel" can be missing at once - reported to the dominion's request board together. */
    private final java.util.Set<String> missingItemLabels = new java.util.LinkedHashSet<>();

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
        workplace.clearAllWorkerIssues(inhabitant.getUUID(), level.getGameTime());
        DominionSavedData.get(level).setDirty();
    }

    @Override
    public void onInterrupted(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        // Whatever pulled control away (going to sleep, eating, ...) may have moved the inhabitant away
        // from the hut - re-approach it rather than resuming in place. Nothing is lost: any food/fuel
        // already loaded stays in the smoker, the result (if any) stays in its output slot, and an
        // ingredient already picked up but not yet loaded just stays in the inhabitant's own inventory.
        phase = Phase.GOTO_HUT;
        containerTarget.pos = null;
    }

    @Override
    public boolean tick(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        return switch (phase) {
            case GOTO_HUT -> tickGotoHut(inhabitant, level, workplace);
            case FIND_INGREDIENTS -> tickFindIngredients(inhabitant, level, workplace);
            case COOKING -> tickCooking(inhabitant, level, workplace);
            case DEPOSIT -> tickDeposit(inhabitant, level, workplace);
            case MISSING_INGREDIENTS -> tickMissingIngredients(inhabitant, level, workplace);
        };
    }

    private boolean tickGotoHut(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        if (JobActions.goToJobsite(inhabitant, level, workplace, nav)) {
            phase = Phase.FIND_INGREDIENTS;
        }
        return true;
    }

    private boolean tickFindIngredients(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        var smokerPos = workplace.findBlock(level, state -> state.is(Blocks.SMOKER)).orElse(null);
        var smoker = smokerPos != null && level.getBlockEntity(smokerPos) instanceof Container container ? container : null;
        if (smoker == null) {
            reportMissing(inhabitant, workplace, level, "a Smoker", DominionStructure.WorkerIssue.urgent("a Smoker", 1));
            phase = Phase.MISSING_INGREDIENTS;
            waitTicks = MISSING_INGREDIENTS_RETRY_TICKS;
            return true;
        }
        clearMissing(inhabitant, workplace, level, "a Smoker");

        // Already carrying an ingredient picked up on a previous tick - walk it over to the smoker
        // and load it in, rather than teleporting it straight from the barrel.
        if (pendingSmokerSlot != -1) {
            if (!JobActions.walkTo(inhabitant, smokerPos, nav)) {
                return true;
            }
            if (!actionPacer.isReady(smokerPos)) {
                return true;
            }
            lookAtBlock(inhabitant, smokerPos);
            var carried = inhabitant.getInventory();
            for (int i = 0; i < carried.getContainerSize(); i++) {
                var stack = carried.getItem(i);
                if (!stack.isEmpty()) {
                    smoker.setItem(pendingSmokerSlot, stack);
                    carried.setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
                    break;
                }
            }
            pendingSmokerSlot = -1;
            actionPacer.recordAction(smokerPos);
            return true;
        }

        boolean needFood = smoker.getItem(0).isEmpty();
        boolean needFuel = smoker.getItem(1).isEmpty();
        if (!needFood && !needFuel) {
            clearMissing(inhabitant, workplace, level, "raw food");
            clearMissing(inhabitant, workplace, level, "fuel");
            lookAtBlock(inhabitant, smokerPos);
            inhabitant.swing(InteractionHand.MAIN_HAND);
            phase = Phase.COOKING;
            return true;
        }

        if (containerTarget.pos == null) {
            var foodContainer = needFood
                    ? JobActions.searchStructureForItem(level, workplace, stack -> CookingRecipes.findSmokingRecipe(level, stack).isPresent())
                    : null;
            var fuelContainer = needFuel
                    ? JobActions.searchStructureForItem(level, workplace, stack -> CookingRecipes.isFuel(level, stack))
                    : null;

            if (needFood && foodContainer == null) {
                reportMissing(inhabitant, workplace, level, "raw food",
                        DominionStructure.WorkerIssue.urgentCategory("raw food", ItemCategory.RAW_FOOD));
            } else if (needFood) {
                clearMissing(inhabitant, workplace, level, "raw food");
            }
            if (needFuel && fuelContainer == null) {
                reportMissing(inhabitant, workplace, level, "fuel",
                        DominionStructure.WorkerIssue.urgentCategory("fuel", ItemCategory.FUEL));
            } else if (needFuel) {
                clearMissing(inhabitant, workplace, level, "fuel");
            }

            if (foodContainer != null) {
                containerTarget.pos = foodContainer;
                fetchSlot = 0;
            } else if (fuelContainer != null) {
                containerTarget.pos = fuelContainer;
                fetchSlot = 1;
            } else {
                phase = Phase.MISSING_INGREDIENTS;
                waitTicks = MISSING_INGREDIENTS_RETRY_TICKS;
                return true;
            }
        }

        java.util.function.Predicate<net.minecraft.world.item.ItemStack> matcher = fetchSlot == 0
                ? stack -> CookingRecipes.findSmokingRecipe(level, stack).isPresent()
                : stack -> CookingRecipes.isFuel(level, stack);

        // Grab the whole matching stack, not just one item - the smoker's slot holds a full stack and
        // cooks through it automatically, so this avoids a barrel trip per single item.
        var extracted = JobActions.takeFromContainer(inhabitant, level, workplace, actionPacer, nav, containerTarget, matcher, true);
        if (!extracted.isEmpty()) {
            inhabitant.getInventory().addItem(extracted);
            pendingSmokerSlot = fetchSlot;
            fetchSlot = -1;
            return true;
        }
        if (containerTarget.pos == null) {
            // The ingredient was gone by the time we arrived (race with another job/player) - rescan next tick.
            fetchSlot = -1;
        }
        return true;
    }

    private void reportMissing(InhabitantEntity inhabitant, DominionStructure workplace, ServerLevel level,
                                String label, DominionStructure.WorkerIssue issue) {
        missingItemLabels.add(label);
        workplace.reportWorkerIssue(inhabitant.getUUID(), issue, level.getGameTime());
        DominionSavedData.get(level).setDirty();
    }

    private void clearMissing(InhabitantEntity inhabitant, DominionStructure workplace, ServerLevel level, String label) {
        if (!missingItemLabels.remove(label)) return;
        workplace.clearWorkerIssue(inhabitant.getUUID(), label, level.getGameTime());
        DominionSavedData.get(level).setDirty();
    }

    private boolean tickCooking(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        var smokerPos = workplace.findBlock(level, state -> state.is(Blocks.SMOKER));
        var smoker = smokerPos.isPresent() && level.getBlockEntity(smokerPos.get()) instanceof Container container ? container : null;
        if (smoker == null) {
            // Smoker was destroyed mid-cook; give up on this cycle and re-check requirements.
            phase = Phase.FIND_INGREDIENTS;
            return true;
        }
        lookAtBlock(inhabitant, smokerPos.get());
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
        if (!JobActions.depositCarriedItems(inhabitant, level, workplace, actionPacer, nav, containerTarget)) {
            return true;
        }
        phase = Phase.GOTO_HUT;
        return false;
    }

    private boolean tickMissingIngredients(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        if (--waitTicks <= 0) {
            // Don't clear the reported issues here - tickFindIngredients re-derives and re-reports/clears
            // each of them against current storage on the very next visit, so the request board stays
            // accurate for both "raw food" and "fuel" throughout the retry loop instead of flickering.
            phase = Phase.FIND_INGREDIENTS;
        }
        return true;
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
        return missingItemLabels.isEmpty() ? null : String.join(", ", missingItemLabels);
    }

    @Override
    public void save(CompoundTag tag) {
        tag.putString("Phase", phase.name());
        tag.putInt("WaitTicks", waitTicks);
        actionPacer.save(tag, "Action");
        tag.putInt("PendingSmokerSlot", pendingSmokerSlot);
        tag.putInt("FetchSlot", fetchSlot);
        if (containerTarget.pos != null) tag.putLong("ContainerTarget", containerTarget.pos.asLong());
        if (!missingItemLabels.isEmpty()) tag.putString("MissingItemLabels", String.join(",", missingItemLabels));
    }

    @Override
    public void load(CompoundTag tag) {
        try {
            phase = Phase.valueOf(tag.getStringOr("Phase", Phase.GOTO_HUT.name()));
        } catch (IllegalArgumentException e) {
            phase = Phase.GOTO_HUT;
        }
        waitTicks = tag.getIntOr("WaitTicks", 0);
        actionPacer.load(tag, "Action");
        pendingSmokerSlot = tag.getIntOr("PendingSmokerSlot", -1);
        fetchSlot = tag.getIntOr("FetchSlot", -1);
        containerTarget.pos = tag.contains("ContainerTarget") ? BlockPos.of(tag.getLongOr("ContainerTarget", 0)) : null;
        missingItemLabels.clear();
        String saved = tag.getStringOr("MissingItemLabels", "");
        if (!saved.isEmpty()) {
            missingItemLabels.addAll(java.util.List.of(saved.split(",")));
        }
    }
}
