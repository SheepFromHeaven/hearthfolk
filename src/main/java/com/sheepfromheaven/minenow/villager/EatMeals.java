package com.sheepfromheaven.minenow.villager;

import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.npc.villager.Villager;

/** A hungry villager eats a meal from its goods as soon as it has one ({@link Meals}). */
public class EatMeals extends Behavior<Villager> {
    private static final int CHECK_EVERY = 20;

    private long nextCheck;

    public EatMeals() {
        super(ImmutableMap.of());
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        if (!Meals.on() || level.getGameTime() < nextCheck || villager.isBaby() || villager.isSleeping()) return false;
        nextCheck = level.getGameTime() + CHECK_EVERY;
        return Meals.isHungry(villager) && Meals.hasAMeal(level, villager);
    }

    @Override
    protected void start(ServerLevel level, Villager villager, long timestamp) {
        Meals.eat(level, villager);
    }
}
