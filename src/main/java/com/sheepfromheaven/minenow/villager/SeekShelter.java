package com.sheepfromheaven.minenow.villager;

import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;
import org.jspecify.annotations.Nullable;

/**
 * A villager at work that stands in the rain walks under the nearest roof ({@link RainShelter}) within a few blocks,
 * taking over the walk target from vanilla's wandering around its job site. It never interrupts one of our own
 * behaviours (a job, a purchase), so a trip that is allowed is finished, and it sits after them in CORE, so a job gets the
 * first chance at the next task.
 */
public class SeekShelter extends Behavior<Villager> {
    private static final int RANGE = 6;
    private static final float SPEED = 0.5F;
    private static final int GIVE_UP_AFTER_TICKS = 200;
    private static final int RECHECK = 40;

    private @Nullable BlockPos shelter;
    private long since;
    private long nextStart;

    public SeekShelter() {
        super(ImmutableMap.of(
                MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED
        ), GIVE_UP_AFTER_TICKS + 20);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager body) {
        if (level.getGameTime() < nextStart) return false;
        nextStart = level.getGameTime() + RECHECK;
        if (!working(body) || !RainShelter.wet(level, body.blockPosition()) || busy(body)) return false;
        shelter = RainShelter.nearest(level, body.blockPosition(), RANGE).orElse(null);
        return shelter != null;
    }

    @Override
    protected void start(ServerLevel level, Villager body, long timestamp) {
        since = timestamp;
        walk(body);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager body, long timestamp) {
        return shelter != null && working(body) && RainShelter.wet(level, body.blockPosition()) && timestamp - since < GIVE_UP_AFTER_TICKS && !busy(body);
    }

    @Override
    protected void tick(ServerLevel level, Villager body, long timestamp) {
        if (shelter != null && !body.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) walk(body);
    }

    @Override
    protected void stop(ServerLevel level, Villager body, long timestamp) {
        shelter = null;
        body.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    }

    private void walk(Villager body) {
        if (shelter != null) body.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(shelter, SPEED, 0));
    }

    private static boolean working(Villager body) {
        return !body.isBaby() && !body.isTrading() && !body.isSleeping() && body.getBrain().isActive(Activity.WORK);
    }

    /** Our work and errands: a trip one of them started is finished before the villager looks for a roof. Background behaviours (sight, walking back to the job site) don't count. */
    private static final java.util.Set<String> JOBS = java.util.Set.of("TendFields", "GoFishing", "ShearSheep", "TendHerd", "DigQuarry", "SmeltOres", "BrewPotions", "HealVillagers",
            "FellTrees", "TendGolems", "PracticeArchery", "ShowArmor", "TendFlowers", "DyeArmor", "RestoreWorn", "PaveRoads", "LeaveQuarry", "CraftAtWorkstation", "EatMeals", "Gather",
            "BuyFromVillagers", "GoOnTour", "SeekGoods", "PickUpGifts");

    private boolean busy(Villager body) {
        return body.getBrain().getRunningBehaviors().stream().anyMatch(behavior -> JOBS.contains(behavior.getClass().getSimpleName()));
    }
}
