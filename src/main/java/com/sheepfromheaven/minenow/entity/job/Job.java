package com.sheepfromheaven.minenow.entity.job;

import com.sheepfromheaven.minenow.dominion.DominionStructure;
import com.sheepfromheaven.minenow.entity.InhabitantEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;

import javax.annotation.Nullable;

/**
 * Behavior strategy for a single job type (fisher, woodcutter, ...), driven by {@link com.sheepfromheaven.minenow.entity.job.WorkGoal}.
 * One instance is created per inhabitant and kept for as long as they hold the job, so it may carry
 * its own state (current phase, target position, etc.) between ticks.
 */
public interface Job {

    /** Whether the job should attempt to (re)start work right now. */
    boolean canStart(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace);

    /** Called once when the goal takes control. */
    void start(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace);

    /**
     * Called every tick while the goal is active.
     *
     * @return true to keep working, false when the job has wound down and control can be released
     */
    boolean tick(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace);

    /** Called when the goal loses control (job finished a cycle, was interrupted, or unassigned). */
    void stop(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace);

    /**
     * Called when the goal loses control mid-cycle - i.e. {@link #tick} last returned {@code true} - rather
     * than because the job wound down naturally. Implementations should fall back to a "travel to workplace"
     * phase so the next resume re-approaches the job site instead of continuing straight from wherever the
     * interruption (e.g. being walked home to sleep) left the inhabitant standing.
     */
    default void onInterrupted(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {}

    /** Short status text for display in the inhabitant's UI, e.g. "Fishing", "Returning home". */
    String getStatusDescription();

    /** The item currently blocking work (e.g. a broken fishing rod with none left to restock), or null if none. */
    @Nullable
    Item getMissingItem();

    /** User-facing problems the job can't resolve on its own (e.g. no valid tree in range), if any. */
    default java.util.List<String> getWarnings() {
        return java.util.List.of();
    }

    /** Serializes in-progress state (phase, targets, timers) so a save/reload can resume mid-cycle. */
    default void save(CompoundTag tag) {}

    /** Restores state previously written by {@link #save}. */
    default void load(CompoundTag tag) {}

    /** Extra implementation-specific detail for the debug view (phase, targets, timers, ...). */
    default java.util.List<String> getDebugLines() {
        return java.util.List.of();
    }
}
