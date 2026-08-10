package com.sheepfromheaven.minenow.jobs;

import com.sheepfromheaven.minenow.dominion.DominionSavedData;
import com.sheepfromheaven.minenow.structure.DominionStructure;
import com.sheepfromheaven.minenow.jobs.JobType;
import com.sheepfromheaven.minenow.inhabitant.InhabitantEntity;
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
 *
 * <p>The job/structure pair is owned by this goal, not re-derived from {@code inhabitant.getWorkStructureId()}
 * every tick - {@code Dominion.assignWorker}/{@code unassignWorker} push changes in directly via
 * {@link #assignJob} and {@link #clearJob}, so there's never a tick where the cached job could belong to a
 * structure the inhabitant isn't assigned to any more. The only time this goal reads
 * {@code getWorkStructureId()} itself is {@link #loadJobIfMissing}, the one-time fallback that resumes an
 * assignment made in a previous session, before any explicit push has happened this time around.
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
        if (cachedStructureId == null) return null;
        var dominion = resolveDominion();
        return dominion != null ? dominion.getStructureById(cachedStructureId) : null;
    }

    @Nullable
    private com.sheepfromheaven.minenow.dominion.Dominion resolveDominion() {
        var dominionId = inhabitant.getDominionId();
        if (dominionId == null || !(inhabitant.level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        return DominionSavedData.get(serverLevel).getDominionById(dominionId);
    }

    /**
     * Assigns whatever job {@code structure}'s type calls for, tearing down the previous job first if
     * one was running. Called directly by {@code Dominion.assignWorker} at the moment of reassignment,
     * so the goal never has to notice on its own that its cached job belongs to a stale structure.
     */
    public void assignJob(DominionStructure structure) {
        teardownJob();
        var jobType = structure.getType().getJobType().orElse(null);
        job = jobType != null ? jobType.createJob() : null;
        cachedStructureId = structure.getId();
        active = false;
        if (job != null) {
            inhabitant.playGoalSwitchSound();
        }
    }

    /** Drops the current job, if any, leaving the inhabitant idle. Called by {@code Dominion.unassignWorker}. */
    public void clearJob() {
        teardownJob();
        job = null;
        cachedStructureId = null;
        active = false;
    }

    /** Cleanly stops whatever job is currently cached, against the workplace it was assigned to. */
    private void teardownJob() {
        if (job == null || !(inhabitant.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        var workplace = resolveWorkplace();
        if (workplace != null) {
            job.stop(inhabitant, serverLevel, workplace);
            if (active) {
                job.onInterrupted(inhabitant, serverLevel, workplace);
            }
        }
        inhabitant.getNavigation().stop();
    }

    /**
     * Recreates the job for whatever structure {@code inhabitant.getWorkStructureId()} points at, if one
     * isn't cached yet - the one-time fallback for a freshly loaded entity that resumes an assignment
     * made in a previous session, rather than through {@link #assignJob}.
     */
    private void loadJobIfMissing() {
        if (job != null) return;
        var structureId = inhabitant.getWorkStructureId();
        var dominion = resolveDominion();
        var workplace = structureId != null && dominion != null ? dominion.getStructureById(structureId) : null;
        if (workplace == null) return;
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
        loadJobIfMissing();
        var dominion = resolveDominion();
        var workplace = resolveWorkplace();
        if (workplace == null || dominion == null || job == null
                || !(inhabitant.level() instanceof ServerLevel serverLevel)) return false;
        var dominionInhabitant = dominion.getInhabitantByEntityUuid(inhabitant.getUUID());
        return dominionInhabitant != null
                && workplace.canWork(dominionInhabitant.getId(), serverLevel, dominion)
                && job.canStart(inhabitant, serverLevel, workplace);
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
     * Stashes previously saved job state; it's applied once the job is (re)created by
     * {@link #loadJobIfMissing}, since the job instance itself doesn't exist yet when the entity is
     * loaded from disk.
     */
    public void load(CompoundTag tag) {
        pendingState = tag.contains("Job") ? tag.getCompoundOrEmpty("Job") : null;
    }
}
