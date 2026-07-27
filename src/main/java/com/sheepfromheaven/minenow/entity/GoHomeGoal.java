package com.sheepfromheaven.minenow.entity;

import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

public class GoHomeGoal extends Goal {
    private static final int WAIT_TICKS = 200;
    private static final double ARRIVE_DISTANCE_SQ = 4.0;

    private final InhabitantEntity inhabitant;
    private int waitTicksRemaining = -1;

    public GoHomeGoal(InhabitantEntity inhabitant) {
        this.inhabitant = inhabitant;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return inhabitant.isGoHomeRequested();
    }

    @Override
    public boolean canContinueToUse() {
        return inhabitant.isGoHomeRequested();
    }

    @Override
    public void start() {
        waitTicksRemaining = -1;
        var pos = inhabitant.getHomeTargetPos();
        if (pos == null) {
            inhabitant.clearGoHomeRequest();
            return;
        }
        inhabitant.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 1.0);
    }

    @Override
    public void stop() {
        waitTicksRemaining = -1;
        inhabitant.getNavigation().stop();
    }

    @Override
    public void tick() {
        var pos = inhabitant.getHomeTargetPos();
        if (pos == null) {
            inhabitant.clearGoHomeRequest();
            return;
        }

        if (waitTicksRemaining < 0) {
            boolean arrived = inhabitant.getNavigation().isDone()
                    || inhabitant.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) <= ARRIVE_DISTANCE_SQ;
            if (arrived) {
                inhabitant.getNavigation().stop();
                waitTicksRemaining = WAIT_TICKS;
            }
        } else if (--waitTicksRemaining <= 0) {
            inhabitant.clearGoHomeRequest();
        }
    }
}
