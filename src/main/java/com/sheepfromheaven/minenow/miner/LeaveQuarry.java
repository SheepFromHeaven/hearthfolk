package com.sheepfromheaven.minenow.miner;

import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;

/** When work is over, or its pickaxe broke, a miner climbs out of its mine, to its bed, the meeting bell or a new pickaxe. */
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
        return mustLeave(body) && LadderClimb.inQuarry(level, body);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager body, long timestamp) {
        return mustLeave(body) && LadderClimb.inQuarry(level, body);
    }

    /** Work is over, or its pickaxe broke and it has to fetch or buy another. */
    private static boolean mustLeave(Villager body) {
        return !body.getBrain().isActive(Activity.WORK) || !com.sheepfromheaven.minenow.villager.Tools.has(body);
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
