package com.sheepfromheaven.minenow.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

public class GoHomeGoal extends Goal {
    private static final int WAIT_TICKS = 200;
    private static final double ARRIVE_DISTANCE_SQ = 4.0;
    private static final long NIGHT_START = 12542;
    private static final long NIGHT_END = 23460;

    private final InhabitantEntity inhabitant;
    private int waitTicksRemaining = -1;
    private boolean nightTriggered;
    private BlockPos targetPos;

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
            inhabitant.clearGoHomeRequest();
            nightTriggered = false;
            return;
        }
        inhabitant.getNavigation().moveTo(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5, 1.0);
    }

    @Override
    public void stop() {
        waitTicksRemaining = -1;
        nightTriggered = false;
        targetPos = null;
        inhabitant.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (targetPos == null) {
            inhabitant.clearGoHomeRequest();
            nightTriggered = false;
            return;
        }

        if (waitTicksRemaining < 0) {
            boolean arrived = inhabitant.getNavigation().isDone()
                    || inhabitant.distanceToSqr(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5) <= ARRIVE_DISTANCE_SQ;
            if (arrived) {
                inhabitant.getNavigation().stop();
                if (!nightTriggered) {
                    waitTicksRemaining = WAIT_TICKS;
                }
            }
        } else if (--waitTicksRemaining <= 0) {
            inhabitant.clearGoHomeRequest();
        }
    }
}
