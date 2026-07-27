package com.sheepfromheaven.minenow.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

public class GoHomeGoal extends Goal {
    private static final int WAIT_TICKS = 200;
    private static final int REPATH_INTERVAL = 60;
    private static final double ARRIVE_DISTANCE_SQ = 4.0;
    private static final double BED_ARRIVE_DISTANCE_SQ = 1.5 * 1.5;
    private static final long NIGHT_START = 12542;
    private static final long NIGHT_END = 23460;

    private final InhabitantEntity inhabitant;
    private int waitTicksRemaining = -1;
    private boolean nightTriggered;
    private boolean isBedTarget;
    private BlockPos targetPos;
    private int repathCooldown;

    public GoHomeGoal(InhabitantEntity inhabitant) {
        this.inhabitant = inhabitant;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private boolean isNight() {
        long dayTime = inhabitant.level().getDayTime() % 24000;
        return dayTime >= NIGHT_START && dayTime < NIGHT_END;
    }

    @Override
    public boolean canUse() {
        if (inhabitant.isGoHomeRequested()) return true;
        if (inhabitant.hasAssignedHome() && isNight()) {
            nightTriggered = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        if (inhabitant.isGoHomeRequested()) return true;
        return nightTriggered && isNight();
    }

    @Override
    public void start() {
        waitTicksRemaining = -1;
        targetPos = inhabitant.getHomeTargetPos();
        if (targetPos == null) {
            clearAndReset();
            return;
        }
        isBedTarget = inhabitant.level().getBlockState(targetPos).is(BlockTags.BEDS);
        moveToTarget();
    }

    private void moveToTarget() {
        inhabitant.getNavigation().moveTo(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5, 1.0);
        repathCooldown = REPATH_INTERVAL;
    }

    @Override
    public void stop() {
        waitTicksRemaining = -1;
        nightTriggered = false;
        targetPos = null;
        if (inhabitant.isSleeping()) {
            inhabitant.stopSleeping();
        }
        inhabitant.getNavigation().stop();
    }

    private void clearAndReset() {
        inhabitant.clearGoHomeRequest();
        nightTriggered = false;
    }

    @Override
    public void tick() {
        if (targetPos == null) {
            clearAndReset();
            return;
        }

        if (inhabitant.isSleeping()) {
            if (!(nightTriggered && isNight())) {
                inhabitant.stopSleeping();
            }
            return;
        }

        double arriveDistSq = isBedTarget ? BED_ARRIVE_DISTANCE_SQ : ARRIVE_DISTANCE_SQ;
        boolean arrived = inhabitant.distanceToSqr(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5) <= arriveDistSq;

        if (waitTicksRemaining < 0) {
            if (arrived) {
                inhabitant.getNavigation().stop();
                if (nightTriggered && isBedTarget) {
                    inhabitant.startSleeping(targetPos);
                } else if (!nightTriggered) {
                    waitTicksRemaining = WAIT_TICKS;
                }
            } else if (--repathCooldown <= 0) {
                moveToTarget();
            }
        } else if (--waitTicksRemaining <= 0) {
            inhabitant.clearGoHomeRequest();
        }
    }
}
