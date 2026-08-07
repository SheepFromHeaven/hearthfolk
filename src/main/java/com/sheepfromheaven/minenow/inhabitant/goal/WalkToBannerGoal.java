package com.sheepfromheaven.minenow.inhabitant.goal;

import com.sheepfromheaven.minenow.dominion.DominionSavedData;
import com.sheepfromheaven.minenow.inhabitant.immigration.ImmigrationJob;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;
import com.sheepfromheaven.minenow.inhabitant.InhabitantEntity;

/**
 * Drives an immigration candidate toward its target dominion's banner while its {@link ImmigrationJob}
 * is in {@link ImmigrationJob.Phase#WALK_TO_FLAG}. Modeled on {@link StrollTowardPathNodeGoal}: periodic
 * repathing rather than every tick. Once within {@link #ARRIVAL_DISTANCE_SQ} of the banner, hands off to
 * {@link ImmigrationJob.Phase#AWAITING_DECISION} and stops moving.
 */
public class WalkToBannerGoal extends Goal {
    private static final int REPATH_INTERVAL = 40;
    private static final double WALK_SPEED = 0.35;
    private static final double ARRIVAL_DISTANCE_SQ = 4.0; // ~2 blocks

    private final InhabitantEntity inhabitant;
    private int repathCooldown;

    public WalkToBannerGoal(InhabitantEntity inhabitant) {
        this.inhabitant = inhabitant;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        var job = inhabitant.getImmigrationJob();
        return job != null && job.getPhase() == ImmigrationJob.Phase.WALK_TO_FLAG;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        repathCooldown = 0;
    }

    @Override
    public void tick() {
        var job = inhabitant.getImmigrationJob();
        if (job == null || !(inhabitant.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        var dominion = DominionSavedData.get(serverLevel).getDominionById(job.getDominionId());
        if (dominion == null) {
            return;
        }
        BlockPos bannerPos = dominion.getBannerPos();
        double distSq = inhabitant.distanceToSqr(bannerPos.getX() + 0.5, bannerPos.getY(), bannerPos.getZ() + 0.5);
        if (distSq <= ARRIVAL_DISTANCE_SQ) {
            job.setPhase(ImmigrationJob.Phase.AWAITING_DECISION);
            inhabitant.getNavigation().stop();
            return;
        }
        if (--repathCooldown <= 0) {
            inhabitant.getNavigation().moveTo(bannerPos.getX() + 0.5, bannerPos.getY(), bannerPos.getZ() + 0.5, WALK_SPEED);
            repathCooldown = REPATH_INTERVAL;
        }
    }

    @Override
    public void stop() {
        inhabitant.getNavigation().stop();
    }
}
