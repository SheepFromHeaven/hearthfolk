package com.sheepfromheaven.minenow.inhabitant.immigration;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * State machine attached to an immigration candidate {@code InhabitantEntity} that has not yet joined
 * a dominion. Not driven through {@link WorkGoal}/{@link Job#tick(com.sheepfromheaven.minenow.inhabitant.InhabitantEntity, net.minecraft.server.level.ServerLevel, com.sheepfromheaven.minenow.structure.DominionStructure)}
 * like structure-bound jobs are (there is no workplace here) - instead ticked directly from
 * {@code InhabitantEntity#aiStep} and driven toward the banner by {@link com.sheepfromheaven.minenow.inhabitant.goal.WalkToBannerGoal}.
 */
public class ImmigrationJob {

    public enum Phase {
        /** Walking toward the dominion's banner. */
        WALK_TO_FLAG,
        /** Standing near the banner, waiting for the owner to approve or reject. */
        AWAITING_DECISION,
        /** Rejected - wandering away from the banner for a while before despawning. */
        REJECTED_WANDER_OFF
    }

    /** How long (ticks) a rejected candidate wanders before despawning - roughly 10-15s. */
    public static final int WANDER_OFF_TICKS = 240;

    private final UUID dominionId;
    private Phase phase = Phase.WALK_TO_FLAG;
    private int wanderTicksRemaining = WANDER_OFF_TICKS;

    public ImmigrationJob(UUID dominionId) {
        this.dominionId = dominionId;
    }

    public UUID getDominionId() {
        return dominionId;
    }

    public Phase getPhase() {
        return phase;
    }

    public void setPhase(Phase phase) {
        this.phase = phase;
        if (phase == Phase.REJECTED_WANDER_OFF) {
            wanderTicksRemaining = WANDER_OFF_TICKS;
        }
    }

    /**
     * Called every tick from {@code InhabitantEntity#aiStep} while this job is attached.
     *
     * @return true once the wander-off countdown has elapsed and the candidate should be discarded
     */
    public boolean tickWanderOff() {
        if (phase != Phase.REJECTED_WANDER_OFF) {
            return false;
        }
        return --wanderTicksRemaining <= 0;
    }

    public void save(CompoundTag tag) {
        tag.putString("DominionId", dominionId.toString());
        tag.putString("Phase", phase.name());
        tag.putInt("WanderTicksRemaining", wanderTicksRemaining);
    }

    public static ImmigrationJob load(CompoundTag tag) {
        var job = new ImmigrationJob(UUID.fromString(tag.getStringOr("DominionId", "")));
        job.phase = Phase.valueOf(tag.getStringOr("Phase", Phase.WALK_TO_FLAG.name()));
        job.wanderTicksRemaining = tag.getIntOr("WanderTicksRemaining", WANDER_OFF_TICKS);
        return job;
    }
}
