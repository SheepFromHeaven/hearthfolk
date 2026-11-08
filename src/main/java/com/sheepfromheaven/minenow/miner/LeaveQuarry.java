package com.sheepfromheaven.minenow.miner;

import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;

/** When work is over a miner climbs out of its quarry, so it can get to its bed and the meeting bell. */
public class LeaveQuarry extends Behavior<Villager> {
    private static final int CHECK_EVERY = 20;

    private long nextCheck;

    public LeaveQuarry() {
        super(ImmutableMap.of(), 600);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager body) {
        if (level.getGameTime() < nextCheck || !body.getVillagerData().profession().is(Miner.PROFESSION)) return false;
        nextCheck = level.getGameTime() + CHECK_EVERY;
        return !body.getBrain().isActive(Activity.WORK) && LadderClimb.inQuarry(level, body);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager body, long timestamp) {
        return !body.getBrain().isActive(Activity.WORK) && LadderClimb.inQuarry(level, body);
    }

    @Override
    protected void tick(ServerLevel level, Villager body, long timestamp) {
        LadderClimb.climbOut(level, body);
    }

    @Override
    protected void stop(ServerLevel level, Villager body, long timestamp) {
        LadderClimb.forgetRoute(body);
    }
}
