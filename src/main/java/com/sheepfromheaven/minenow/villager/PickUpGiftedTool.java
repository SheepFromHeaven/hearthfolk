package com.sheepfromheaven.minenow.villager;

import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;

/** A villager that has no tool of its trade picks one up when a player throws it nearby. */
public class PickUpGiftedTool extends Behavior<Villager> {
    private static final int SEARCH_RADIUS = 10;
    private static final float SPEED = 0.6F;

    private @Nullable ItemEntity target;
    private long nextSearch;

    public PickUpGiftedTool() {
        super(ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT), 300);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager body) {
        if (level.getGameTime() < nextSearch || TradeTable.forVillager(body).isEmpty() || hasTool(body)) return false;
        nextSearch = level.getGameTime() + 20;
        target = level.getEntitiesOfClass(ItemEntity.class, body.getBoundingBox().inflate(SEARCH_RADIUS),
                        item -> item.isAlive() && !item.hasPickUpDelay() && Unloading.isTool(body, item.getItem()))
                .stream()
                .min(Comparator.comparingDouble(body::distanceToSqr))
                .orElse(null);
        return target != null;
    }

    @Override
    protected void start(ServerLevel level, Villager body, long timestamp) {
        body.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new EntityTracker(target, false), SPEED, 0));
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager body, long timestamp) {
        return target != null && target.isAlive() && !hasTool(body);
    }

    @Override
    protected void tick(ServerLevel level, Villager body, long timestamp) {
        if (target == null || body.distanceToSqr(target) > 2.25) {
            if (!body.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) start(level, body, timestamp);
            return;
        }
        ItemStack hoe = target.getItem().split(1);
        ItemStack rest = body.getInventory().addItem(hoe);
        if (!rest.isEmpty()) target.getItem().grow(rest.getCount());
        if (target.getItem().isEmpty()) target.discard();
        body.take(target, 1);
    }

    private static boolean hasTool(Villager body) {
        return body.getInventory().hasAnyMatching(stack -> Unloading.isTool(body, stack));
    }

    @Override
    protected void stop(ServerLevel level, Villager body, long timestamp) {
        target = null;
        body.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    }
}
