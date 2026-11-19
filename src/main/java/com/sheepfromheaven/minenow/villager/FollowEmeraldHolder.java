package com.sheepfromheaven.minenow.villager;

import com.google.common.collect.ImmutableMap;
import com.sheepfromheaven.minenow.MineNowConfig;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Unemployed villagers follow a player holding an emerald, like animals follow food, so players can lead them to
 * their own settlement. Once led far from its old bed it gives the bed and meeting bell up and settles anew.
 */
public class FollowEmeraldHolder extends Behavior<Villager> {
    private static final double NOTICE_RANGE = 10.0;
    private static final double LOSE_RANGE = 16.0;
    private static final int KEEP_DISTANCE = 2;
    private static final float SPEED = 0.6F;
    static final double MOVE_OUT_DISTANCE = 32.0;
    private static final List<Activity> BUSY = List.of(Activity.PANIC, Activity.HIDE, Activity.PRE_RAID, Activity.RAID);

    private @Nullable Player leader;

    public FollowEmeraldHolder() {
        super(ImmutableMap.of(), 1200);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager body) {
        if (!canFollow(body)) return false;
        leader = level.getNearestPlayer(body.getX(), body.getY(), body.getZ(), NOTICE_RANGE, entity -> entity instanceof Player player && !player.isSpectator() && holdsEmerald(player));
        return leader != null;
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager body, long timestamp) {
        return leader != null && leader.isAlive() && holdsEmerald(leader) && canFollow(body)
                && body.distanceToSqr(leader) < LOSE_RANGE * LOSE_RANGE;
    }

    @Override
    protected void tick(ServerLevel level, Villager body, long timestamp) {
        if (leader == null) return;
        Brain<Villager> brain = body.getBrain();
        brain.setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(leader, true));
        brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new EntityTracker(leader, false), SPEED, KEEP_DISTANCE));
        moveOutIfFarFromHome(level, body);
    }

    @Override
    protected void stop(ServerLevel level, Villager body, long timestamp) {
        leader = null;
        body.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    }

    private static void moveOutIfFarFromHome(ServerLevel level, Villager body) {
        for (var memory : List.of(MemoryModuleType.HOME, MemoryModuleType.MEETING_POINT)) {
            boolean farAway = body.getBrain().getMemory(memory)
                    .map(GlobalPos::pos).filter(pos -> pos.distToCenterSqr(body.position()) > MOVE_OUT_DISTANCE * MOVE_OUT_DISTANCE)
                    .isPresent()
                    || body.getBrain().getMemory(memory).filter(pos -> pos.dimension() != level.dimension()).isPresent();
            if (farAway) {
                body.releasePoi(memory);
                body.getBrain().eraseMemory(memory);
            }
        }
    }

    private static boolean canFollow(Villager body) {
        return MineNowConfig.enabled(MineNowConfig.Feature.VILLAGERS_FOLLOW_EMERALDS)
                && !body.isBaby() && !body.isSleeping() && !body.isTrading()
                && body.getVillagerData().profession().is(VillagerProfession.NONE)
                && BUSY.stream().noneMatch(body.getBrain()::isActive);
    }

    private static boolean holdsEmerald(Player player) {
        return player.getMainHandItem().is(Items.EMERALD) || player.getOffhandItem().is(Items.EMERALD);
    }
}
