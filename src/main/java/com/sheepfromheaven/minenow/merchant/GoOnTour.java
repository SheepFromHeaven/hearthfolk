package com.sheepfromheaven.minenow.merchant;

import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.npc.villager.Villager;

import java.util.Optional;

/**
 * A merchant's tour ({@link Tour}, {@link Tours}): in the morning, rested, with a village near it short of something its
 * own village sells, it shops for the goods and walks them to the bells of the villages that need them. The tour is
 * saved on the villager, so this only picks it up wherever it was left.
 */
public final class GoOnTour extends Behavior<Villager> {
    private static final int FOREVER = 24000 * 10;
    private static final int PLAN_EVERY = 100;

    private Tours.Plan plan;
    private long nextPlan;

    public GoOnTour() {
        super(ImmutableMap.of(), FOREVER);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager merchant) {
        if (!Merchant.isMerchant(merchant) || merchant.isBaby() || merchant.isSleeping()) return false;
        if (merchant.getData(Merchant.TOUR).isPresent()) return true;
        if (level.getGameTime() < nextPlan) return false;
        nextPlan = level.getGameTime() + PLAN_EVERY;
        if (!Tours.mayStart(level, merchant)) return false;
        plan = Tours.plan(level, merchant).orElse(null);
        return plan != null;
    }

    @Override
    protected void start(ServerLevel level, Villager merchant, long timestamp) {
        if (merchant.getData(Merchant.TOUR).isEmpty() && plan != null) {
            merchant.setData(Merchant.TOUR, Optional.of(new Tour(plan.stops(), 0, Tour.Phase.SHOP, timestamp, timestamp, merchant.blockPosition(), plan.wanted())));
        }
        Tours.resume(merchant);
        plan = null;
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager merchant, long timestamp) {
        return merchant.isAlive() && merchant.getData(Merchant.TOUR).isPresent();
    }

    @Override
    protected void tick(ServerLevel level, Villager merchant, long timestamp) {
        Tours.step(level, merchant);
    }

    @Override
    protected void stop(ServerLevel level, Villager merchant, long timestamp) {
        plan = null;
    }
}
