package com.sheepfromheaven.minenow.jobs.charcoalburner;

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
 * Goes to the kiln, finds logs in storage, loads them into the kiln's actual Furnace block as both
 * the smelting input and (since logs also burn) the fuel, so vanilla furnace mechanics turn them into
 * charcoal, and deposits the result back into storage once the furnace produces it. Mirrors
 * {@link com.sheepfromheaven.minenow.jobs.cook.CookJob}'s Smoker-driven approach.
 */
public class CharcoalBurnerJob implements Job {
    private static final int MISSING_LOGS_RETRY_TICKS = 100;

    private enum Phase { GOTO_HUT, FIND_LOGS, BURNING, DEPOSIT, MISSING_LOGS }

    private Phase phase = Phase.GOTO_HUT;
    private int waitTicks;
    private final ContainerActionPacer actionPacer = new ContainerActionPacer();
    private final JobActions.Nav nav = new JobActions.Nav();
    private final JobActions.ContainerTarget containerTarget = new JobActions.ContainerTarget();
    /** Furnace slot (0 input / 1 fuel) a log already picked up is destined for, or -1 if not carrying one. */
    private int pendingFurnaceSlot = -1;
    /** Furnace slot the current {@link #containerTarget} trip is fetching for, mirrors {@link #pendingFurnaceSlot} once loaded. */
    private int fetchSlot = -1;

    @Override
    public boolean canStart(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        return true;
    }

    @Override
    public void start(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        // Resume wherever the last cycle left off; state persists across interruptions. Any logs
        // already loaded into the furnace keep burning there regardless, since it's a real block entity.
    }

    @Override
    public void stop(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        inhabitant.getNavigation().stop();
        workplace.clearAllWorkerIssues(inhabitant.getUUID(), level.getGameTime());
        DominionSavedData.get(level).setDirty();
    }

    @Override
    public void onInterrupted(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        // Whatever pulled control away may have moved the inhabitant away from the hut - re-approach
        // rather than resuming in place. Nothing is lost: logs already loaded stay in the furnace, any
        // result stays in its output slot, and a log already picked up but not yet loaded just stays
        // in the inhabitant's own inventory.
        phase = Phase.GOTO_HUT;
        containerTarget.pos = null;
    }

    @Override
    public boolean tick(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        return switch (phase) {
            case GOTO_HUT -> tickGotoHut(inhabitant, level, workplace);
            case FIND_LOGS -> tickFindLogs(inhabitant, level, workplace);
            case BURNING -> tickBurning(inhabitant, level, workplace);
            case DEPOSIT -> tickDeposit(inhabitant, level, workplace);
            case MISSING_LOGS -> tickMissingLogs(inhabitant, level, workplace);
        };
    }

    private boolean tickGotoHut(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        if (JobActions.goToJobsite(inhabitant, level, workplace, nav)) {
            phase = Phase.FIND_LOGS;
        }
        return true;
    }

    private boolean tickFindLogs(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        var furnacePos = workplace.findBlock(level, state -> state.is(Blocks.FURNACE)).orElse(null);
        var furnace = furnacePos != null && level.getBlockEntity(furnacePos) instanceof Container container ? container : null;
        if (furnace == null) {
            reportMissing(inhabitant, workplace, level, DominionStructure.WorkerIssue.urgent("a Furnace", 1));
            phase = Phase.MISSING_LOGS;
            waitTicks = MISSING_LOGS_RETRY_TICKS;
            return true;
        }
        clearMissing(inhabitant, workplace, level);

        // Already carrying a log picked up on a previous tick - walk it over to the furnace and load it in.
        if (pendingFurnaceSlot != -1) {
            if (!JobActions.walkTo(inhabitant, furnacePos, nav)) {
                return true;
            }
            if (!actionPacer.isReady(furnacePos)) {
                return true;
            }
            lookAtBlock(inhabitant, furnacePos);
            var carried = inhabitant.getInventory();
            for (int i = 0; i < carried.getContainerSize(); i++) {
                var stack = carried.getItem(i);
                if (!stack.isEmpty()) {
                    furnace.setItem(pendingFurnaceSlot, stack);
                    carried.setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
                    break;
                }
            }
            pendingFurnaceSlot = -1;
            actionPacer.recordAction(furnacePos);
            return true;
        }

        boolean needInput = furnace.getItem(0).isEmpty();
        boolean needFuel = furnace.getItem(1).isEmpty();
        if (!needInput && !needFuel) {
            clearMissing(inhabitant, workplace, level);
            lookAtBlock(inhabitant, furnacePos);
            inhabitant.swing(InteractionHand.MAIN_HAND);
            phase = Phase.BURNING;
            return true;
        }

        // Logs serve as both the smelting input and the fuel, so either slot pulls from the same
        // "any log" search - no separate fuel category needed like Cook's food/fuel split.
        boolean wasSearching = containerTarget.pos == null;
        if (wasSearching) {
            fetchSlot = needInput ? 0 : 1;
        }
        // Grab just one log, not the whole stack - unlike Cook's food/fuel split, both furnace slots
        // here draw from the same "any log" match, so grabbing a full stack for the first slot would
        // starve the second trip that needs the same barrel for the other slot.
        var extracted = JobActions.takeFromContainer(inhabitant, level, workplace, actionPacer, nav, containerTarget,
                CharcoalRecipes::isLog, false);
        if (wasSearching && containerTarget.pos != null) {
            // Found a container to fetch from this call - clear the "missing logs" issue right away
            // rather than waiting for the trip there and the extraction itself to finish.
            clearMissing(inhabitant, workplace, level);
        }
        if (!extracted.isEmpty()) {
            inhabitant.getInventory().addItem(extracted);
            pendingFurnaceSlot = fetchSlot;
            fetchSlot = -1;
            return true;
        }
        if (wasSearching && containerTarget.pos == null) {
            reportMissing(inhabitant, workplace, level,
                    DominionStructure.WorkerIssue.urgentCategory("logs", ItemCategory.LOGS));
            phase = Phase.MISSING_LOGS;
            waitTicks = MISSING_LOGS_RETRY_TICKS;
            fetchSlot = -1;
        }
        return true;
    }

    private void reportMissing(InhabitantEntity inhabitant, DominionStructure workplace, ServerLevel level,
                                DominionStructure.WorkerIssue issue) {
        workplace.reportWorkerIssue(inhabitant.getUUID(), issue, level.getGameTime());
        DominionSavedData.get(level).setDirty();
    }

    private void clearMissing(InhabitantEntity inhabitant, DominionStructure workplace, ServerLevel level) {
        workplace.clearWorkerIssue(inhabitant.getUUID(), "logs", level.getGameTime());
        workplace.clearWorkerIssue(inhabitant.getUUID(), "a Furnace", level.getGameTime());
        DominionSavedData.get(level).setDirty();
    }

    private boolean tickBurning(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        var furnacePos = workplace.findBlock(level, state -> state.is(Blocks.FURNACE));
        var furnace = furnacePos.isPresent() && level.getBlockEntity(furnacePos.get()) instanceof Container container ? container : null;
        if (furnace == null) {
            // Furnace was destroyed mid-burn; give up on this cycle and re-check requirements.
            phase = Phase.FIND_LOGS;
            return true;
        }
        lookAtBlock(inhabitant, furnacePos.get());
        var result = furnace.getItem(2);
        if (result.isEmpty()) {
            boolean lit = level.getBlockState(furnacePos.get()).getValue(AbstractFurnaceBlock.LIT);
            if (!lit && furnace.getItem(1).isEmpty()) {
                // Ran out of fuel before the log finished smelting; go fetch more instead of waiting forever.
                phase = Phase.FIND_LOGS;
            }
            return true;
        }
        inhabitant.getInventory().addItem(furnace.removeItem(2, result.getCount()));
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

    private boolean tickMissingLogs(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        if (--waitTicks <= 0) {
            // Don't clear the reported issue here - tickFindLogs re-derives and re-reports/clears it
            // against current storage on the very next visit, so the request board stays accurate.
            phase = Phase.FIND_LOGS;
        }
        return true;
    }

    @Override
    public String getStatusDescription() {
        return switch (phase) {
            case GOTO_HUT -> "Heading to work";
            case FIND_LOGS -> "Gathering logs";
            case BURNING -> "Burning charcoal";
            case DEPOSIT -> "Storing charcoal";
            case MISSING_LOGS -> "Needs logs";
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
        return phase == Phase.MISSING_LOGS ? "logs" : null;
    }

    @Override
    public void save(CompoundTag tag) {
        tag.putString("Phase", phase.name());
        tag.putInt("WaitTicks", waitTicks);
        actionPacer.save(tag, "Action");
        tag.putInt("PendingFurnaceSlot", pendingFurnaceSlot);
        tag.putInt("FetchSlot", fetchSlot);
        if (containerTarget.pos != null) tag.putLong("ContainerTarget", containerTarget.pos.asLong());
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
        pendingFurnaceSlot = tag.getIntOr("PendingFurnaceSlot", -1);
        fetchSlot = tag.getIntOr("FetchSlot", -1);
        containerTarget.pos = tag.contains("ContainerTarget") ? BlockPos.of(tag.getLongOr("ContainerTarget", 0)) : null;
    }
}
