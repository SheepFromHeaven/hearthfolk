package com.sheepfromheaven.minenow.entity.job;

import com.sheepfromheaven.minenow.dominion.DominionSavedData;
import com.sheepfromheaven.minenow.dominion.DominionStructure;
import com.sheepfromheaven.minenow.dominion.JobType;
import com.sheepfromheaven.minenow.entity.InhabitantEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Item;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.UUID;

/**
 * Generic driver for whatever job an inhabitant currently holds. Adding a new profession only
 * requires a new {@link JobType} entry and a {@link Job} implementation - this goal itself never changes.
 */
public class WorkGoal extends Goal {
    private final InhabitantEntity inhabitant;
    private Job job;
    private UUID cachedStructureId;
    private boolean active;
    @Nullable
    private CompoundTag pendingState;

    public WorkGoal(InhabitantEntity inhabitant) {
        this.inhabitant = inhabitant;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Nullable
    private DominionStructure resolveWorkplace() {
        var structureId = inhabitant.getWorkStructureId();
        var dominionId = inhabitant.getDominionId();
        if (structureId == null || dominionId == null || !(inhabitant.level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        var dominion = DominionSavedData.get(serverLevel).getDominionById(dominionId);
        return dominion != null ? dominion.getStructureById(structureId) : null;
    }

    private void refreshJob(DominionStructure workplace) {
        if (job != null && workplace.getId().equals(cachedStructureId)) {
            return;
        }
        var jobType = workplace.getType().getJobType().orElse(null);
        job = jobType != null ? jobType.createJob() : null;
        cachedStructureId = workplace.getId();
        if (job != null && pendingState != null) {
            job.load(pendingState);
        }
        pendingState = null;
    }

    @Override
    public boolean canUse() {
        if (inhabitant.isGoHomeRequested()) return false;
        var workplace = resolveWorkplace();
        if (workplace == null || !(inhabitant.level() instanceof ServerLevel serverLevel)) return false;
        refreshJob(workplace);
        return job != null && job.canStart(inhabitant, serverLevel, workplace);
    }

    @Override
    public boolean canContinueToUse() {
        if (inhabitant.isGoHomeRequested()) return false;
        return active && job != null && resolveWorkplace() != null;
    }

    @Override
    public void start() {
        inhabitant.playGoalSwitchSound();
        active = true;
        var workplace = resolveWorkplace();
        if (workplace != null && job != null && inhabitant.level() instanceof ServerLevel serverLevel) {
            job.start(inhabitant, serverLevel, workplace);
        }
    }

    @Override
    public void stop() {
        // active still reflects the last tick()'s return value: true means the job was still mid-cycle
        // when something else (sleep, hunger, ...) took over, rather than winding down on its own.
        boolean interrupted = active;
        active = false;
        var workplace = resolveWorkplace();
        if (workplace != null && job != null && inhabitant.level() instanceof ServerLevel serverLevel) {
            job.stop(inhabitant, serverLevel, workplace);
            if (interrupted) {
                job.onInterrupted(inhabitant, serverLevel, workplace);
            }
        } else if (workplace == null) {
            // Unassigned, or the workplace structure is gone - drop the stale job outright rather than
            // leaving its last phase/position sitting there. Otherwise status text keeps showing the old
            // job forever, and reassigning to the same structure later would resume it mid-cycle instead
            // of starting fresh.
            job = null;
            cachedStructureId = null;
        }
        inhabitant.getNavigation().stop();
    }

    @Override
    public void tick() {
        var workplace = resolveWorkplace();
        if (workplace == null || job == null || !(inhabitant.level() instanceof ServerLevel serverLevel)) {
            active = false;
            return;
        }
        active = job.tick(inhabitant, serverLevel, workplace);
    }

    public boolean isActivelyWorking() {
        return active;
    }

    public String getStatusDescription() {
        return job != null ? job.getStatusDescription() : "Idle";
    }

    public java.util.List<String> getDebugLines() {
        if (job == null) {
            return java.util.List.of("No job assigned");
        }
        var lines = new java.util.ArrayList<String>();
        lines.add("Job: " + job.getClass().getSimpleName() + " (active=" + active + ")");
        lines.addAll(job.getDebugLines());
        return lines;
    }

    @Nullable
    public Item getMissingItem() {
        return job != null ? job.getMissingItem() : null;
    }

    @Nullable
    public String getMissingItemLabel() {
        return job != null ? job.getMissingItemLabel() : null;
    }

    public java.util.List<String> getWarnings() {
        return job != null ? job.getWarnings() : java.util.List.of();
    }

    @Nullable
    public JobType getJobType() {
        var workplace = resolveWorkplace();
        return workplace != null ? workplace.getType().getJobType().orElse(null) : null;
    }

    /** Persists the active job's in-progress state so a save/reload can resume mid-cycle instead of restarting. */
    public void save(CompoundTag tag) {
        if (job != null) {
            var jobTag = new CompoundTag();
            job.save(jobTag);
            tag.put("Job", jobTag);
        }
    }

    /**
     * Stashes previously saved job state; it's applied once the job is (re)created by {@link #refreshJob},
     * since the job instance itself doesn't exist yet when the entity is loaded from disk.
     */
    public void load(CompoundTag tag) {
        pendingState = tag.contains("Job") ? tag.getCompoundOrEmpty("Job") : null;
    }
}
