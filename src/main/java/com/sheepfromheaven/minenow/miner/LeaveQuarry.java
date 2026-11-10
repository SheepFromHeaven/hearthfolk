package com.sheepfromheaven.minenow.miner;

import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;

/**
 * When work is over, or its pickaxe broke, a miner climbs out of its mine, to its bed, the meeting bell or a new pickaxe.
 * Any other villager that ends up down a mine climbs out by its ladder straight away.
 */
public class LeaveQuarry extends Behavior<Villager> {
    private static final int CHECK_EVERY = 20;

    private long nextCheck;
    private long startedAt;
    /** Climbing out has failed in odd corners of a half-dug mine; after this long a villager is helped out rather than trapped for good. */
    private static final int HELP_OUT_AFTER = 600;
    private net.minecraft.core.@org.jspecify.annotations.Nullable BlockPos cart;

    public LeaveQuarry() {
        super(ImmutableMap.of(), 1200);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager body) {
        if (level.getGameTime() < nextCheck || body.isBaby() || body.isSleeping()) return false;
        nextCheck = level.getGameTime() + CHECK_EVERY;
        cart = LadderClimb.mineOf(level, body).orElse(null);
        return cart != null && mustLeave(level, body, cart);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager body, long timestamp) {
        return cart != null && mustLeave(level, body, cart) && !LadderClimb.climbedOut(level, body, cart);
    }

    /** Not its mine, or work is over, or its pickaxe broke and it has to fetch or buy another. */
    private static boolean mustLeave(ServerLevel level, Villager body, net.minecraft.core.BlockPos cart) {
        return !LadderClimb.ownCart(level, body).map(cart::equals).orElse(false)
                || !body.getBrain().isActive(Activity.WORK) || !com.sheepfromheaven.minenow.villager.Tools.has(body);
    }

    /** When each villager first started trying to climb out, kept across restarts so the help-out timer can't be reset forever. */
    private static final java.util.Map<java.util.UUID, Long> TRYING_SINCE = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    protected void start(ServerLevel level, Villager body, long timestamp) {
        startedAt = TRYING_SINCE.computeIfAbsent(body.getUUID(), id -> timestamp);
        if (timestamp - startedAt > 2 * HELP_OUT_AFTER) startedAt = timestamp;
        TRYING_SINCE.put(body.getUUID(), startedAt);
    }

    @Override
    protected void tick(ServerLevel level, Villager body, long timestamp) {
        if (timestamp - startedAt > HELP_OUT_AFTER) {
            LadderClimb.helpOut(level, body, cart);
            return;
        }
        LadderClimb.climbOut(level, body, cart);
    }

    @Override
    protected void stop(ServerLevel level, Villager body, long timestamp) {
        LadderClimb.forgetRoute(body);
        if (cart != null && LadderClimb.climbedOut(level, body, cart)) TRYING_SINCE.remove(body.getUUID());
        cart = null;
    }
}
