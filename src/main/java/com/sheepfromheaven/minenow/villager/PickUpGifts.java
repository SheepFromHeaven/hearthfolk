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

/**
 * A villager walks over to something it needs that lies nearby, as a player would throw it: a tool of its trade when it
 * has none, or a supply of its trade it holds less than one batch of (a farmer's first seeds), taking up to the
 * supply's {@code max}. Vanilla only walks to wanted items when nothing else sets its walk target, which a working
 * villager's own behaviours almost always do. Only items it can see; a missing tool comes first. Items that belong to
 * another villager ({@link Belongings}) are left alone.
 */
public class PickUpGifts extends Behavior<Villager> {
    private static final int SEARCH_RADIUS = 10;
    private static final float SPEED = 0.6F;

    private @Nullable ItemEntity target;
    private long nextSearch;

    public PickUpGifts() {
        super(ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED), 300);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager body) {
        if (level.getGameTime() < nextSearch || TradeTable.forVillager(body).isEmpty()) return false;
        nextSearch = level.getGameTime() + 20;
        target = level.getEntitiesOfClass(ItemEntity.class, body.getBoundingBox().inflate(SEARCH_RADIUS),
                        item -> item.isAlive() && !item.hasPickUpDelay() && (item.getTarget() == null || item.getTarget().equals(body.getUUID()))
                                && wanted(level, body, item.getItem()) > 0 && body.hasLineOfSight(item))
                .stream()
                .min(Comparator.<ItemEntity>comparingInt(item -> Unloading.isTool(body, item.getItem()) ? 0 : 1).thenComparingDouble(body::distanceToSqr))
                .orElse(null);
        return target != null;
    }

    /** How many of it the villager would take: one tool when it has none, a supply up to its {@code max} when short of a batch. */
    static int wanted(ServerLevel level, Villager body, ItemStack stack) {
        if (Unloading.isTool(body, stack)) return hasTool(body) ? 0 : 1;
        return TradeTable.forVillager(body).flatMap(table -> table.suppliesWanted(level, body).stream()
                        .filter(supply -> stack.is(supply.item()))
                        .findFirst())
                .map(supply -> {
                    int held = Economy.count(level, body, supply.item());
                    return held < supply.price().count() ? supply.max() - held : 0;
                })
                .orElse(0);
    }

    @Override
    protected void start(ServerLevel level, Villager body, long timestamp) {
        body.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new EntityTracker(target, false), SPEED, 0));
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager body, long timestamp) {
        return target != null && target.isAlive() && wanted(level, body, target.getItem()) > 0;
    }

    @Override
    protected void tick(ServerLevel level, Villager body, long timestamp) {
        if (target == null || body.distanceToSqr(target) > 2.25) {
            if (!walkingToTarget(body)) start(level, body, timestamp);
            return;
        }
        int taking = Math.min(target.getItem().getCount(), wanted(level, body, target.getItem()));
        ItemStack taken = target.getItem().split(taking);
        ItemStack rest = body.getInventory().addItem(taken);
        if (!rest.isEmpty()) target.getItem().grow(rest.getCount());
        body.take(target, taking - rest.getCount());
        if (target.getItem().isEmpty()) target.discard();
    }

    private boolean walkingToTarget(Villager body) {
        return body.getBrain().getMemory(MemoryModuleType.WALK_TARGET)
                .filter(walk -> walk.getTarget() instanceof EntityTracker tracker && tracker.getEntity() == target).isPresent();
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
