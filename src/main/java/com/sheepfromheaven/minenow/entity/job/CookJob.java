package com.sheepfromheaven.minenow.entity.job;

import com.sheepfromheaven.minenow.dominion.DominionSavedData;
import com.sheepfromheaven.minenow.dominion.DominionStructure;
import com.sheepfromheaven.minenow.dominion.ItemCategory;
import com.sheepfromheaven.minenow.entity.InhabitantEntity;
import net.minecraft.core.BlockPos;
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
    private final ContainerActionPacer actionPacer = new ContainerActionPacer();
    @Nullable
    private BlockPos containerTarget;
    /** Smoker slot (0 food / 1 fuel) an ingredient already picked up is destined for, or -1 if not carrying one. */
    private int pendingSmokerSlot = -1;
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
        containerTarget = null;
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
        if (moveTowardsAndCheckArrival(inhabitant, workplace.findApproachPos(level))) {
            phase = Phase.FIND_INGREDIENTS;
        }
        return true;
    }

    private boolean tickFindIngredients(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        var smokerPos = workplace.findBlock(level, state -> state.is(Blocks.SMOKER)).orElse(null);
        var smoker = smokerPos != null && level.getBlockEntity(smokerPos) instanceof Container container ? container : null;
        if (smoker == null) {
            missingItemLabel = "a Smoker";
            workplace.reportWorkerIssue(inhabitant.getUUID(),
                    DominionStructure.WorkerIssue.urgent(missingItemLabel, 1), level.getGameTime());
            DominionSavedData.get(level).setDirty();
            phase = Phase.MISSING_INGREDIENTS;
            waitTicks = MISSING_INGREDIENTS_RETRY_TICKS;
            return true;
        }

        // Already carrying an ingredient picked up on a previous tick - walk it over to the smoker
        // and load it in, rather than teleporting it straight from the barrel.
        if (pendingSmokerSlot != -1) {
            if (!moveTowardsAndCheckArrival(inhabitant, smokerPos)) {
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

        int neededSlot = smoker.getItem(0).isEmpty() ? 0 : smoker.getItem(1).isEmpty() ? 1 : -1;
        if (neededSlot == -1) {
            missingItemLabel = null;
            lookAtBlock(inhabitant, smokerPos);
            inhabitant.swing(InteractionHand.MAIN_HAND);
            phase = Phase.COOKING;
            return true;
        }

        java.util.function.Predicate<net.minecraft.world.item.ItemStack> matcher = neededSlot == 0
                ? stack -> CookingRecipes.findSmokingRecipe(level, stack).isPresent()
                : stack -> CookingRecipes.isFuel(level, stack);

        if (containerTarget == null) {
            containerTarget = ContainerUtil.findContainerWith(level, workplace.findStorageContainers(level), matcher);
            if (containerTarget == null) {
                missingItemLabel = neededSlot == 0 ? "raw food" : "fuel";
                var category = neededSlot == 0 ? ItemCategory.RAW_FOOD : ItemCategory.FUEL;
                workplace.reportWorkerIssue(inhabitant.getUUID(),
                        DominionStructure.WorkerIssue.urgentCategory(missingItemLabel, category), level.getGameTime());
                DominionSavedData.get(level).setDirty();
                phase = Phase.MISSING_INGREDIENTS;
                waitTicks = MISSING_INGREDIENTS_RETRY_TICKS;
                return true;
            }
        }
        if (!moveTowardsAndCheckArrival(inhabitant, containerTarget)) {
            return true;
        }
        if (!actionPacer.isReady(containerTarget)) {
            return true;
        }
        lookAtBlock(inhabitant, containerTarget);
        if (level.getBlockEntity(containerTarget) instanceof Container container) {
            // Grab the whole matching stack, not just one item - the smoker's slot holds a full
            // stack and cooks through it automatically, so this avoids a barrel trip per single item.
            var extracted = ContainerUtil.extractFirst(container, inhabitant, matcher);
            if (!extracted.isEmpty()) {
                inhabitant.getInventory().addItem(extracted);
                pendingSmokerSlot = neededSlot;
                actionPacer.recordAction(containerTarget);
                containerTarget = null;
                return true;
            }
        }
        // The ingredient was gone by the time we arrived (race with another job/player) - rescan next tick.
        containerTarget = null;
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
        var carried = inhabitant.getInventory();
        for (int i = 0; i < carried.getContainerSize(); i++) {
            var stack = carried.getItem(i);
            if (stack.isEmpty()) continue;
            if (containerTarget == null) {
                containerTarget = ContainerUtil.findContainerAccepting(level, workplace.findStorageContainers(level), stack);
                if (containerTarget == null) {
                    // No room anywhere right now - stay put and retry next tick rather than getting stuck.
                    return true;
                }
            }
            if (!moveTowardsAndCheckArrival(inhabitant, containerTarget)) {
                return true;
            }
            if (!actionPacer.isReady(containerTarget)) {
                return true;
            }
            lookAtBlock(inhabitant, containerTarget);
            if (level.getBlockEntity(containerTarget) instanceof Container container) {
                carried.setItem(i, ContainerUtil.insertItem(container, inhabitant, stack));
            }
            actionPacer.recordAction(containerTarget);
            containerTarget = null;
            return true;
        }
        phase = Phase.GOTO_HUT;
        return false;
    }

    private boolean tickMissingIngredients(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        if (--waitTicks <= 0) {
            if (missingItemLabel != null) {
                workplace.clearWorkerIssue(inhabitant.getUUID(), missingItemLabel, level.getGameTime());
                DominionSavedData.get(level).setDirty();
            }
            phase = Phase.FIND_INGREDIENTS;
        }
        return true;
    }

    private boolean moveTowardsAndCheckArrival(InhabitantEntity inhabitant, BlockPos target) {
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
        actionPacer.save(tag, "Action");
        tag.putInt("PendingSmokerSlot", pendingSmokerSlot);
        if (containerTarget != null) tag.putLong("ContainerTarget", containerTarget.asLong());
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
        actionPacer.load(tag, "Action");
        pendingSmokerSlot = tag.getIntOr("PendingSmokerSlot", -1);
        containerTarget = tag.contains("ContainerTarget") ? BlockPos.of(tag.getLongOr("ContainerTarget", 0)) : null;
        missingItemLabel = tag.contains("MissingItemLabel") ? tag.getStringOr("MissingItemLabel", "") : null;
    }
}
