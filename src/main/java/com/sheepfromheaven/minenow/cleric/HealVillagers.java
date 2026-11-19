package com.sheepfromheaven.minenow.cleric;

import com.google.common.collect.ImmutableMap;
import com.sheepfromheaven.minenow.villager.Economy;
import com.sheepfromheaven.minenow.villager.WorkXp;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.SwingAnimation;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.Optional;

/**
 * A cleric heals a hurt villager that can pay: it walks to the nearest villager within {@link #RANGE} blocks below
 * {@link #HURT} of its health that holds the price of its healing potion, takes the emeralds from its goods (nothing while villagers do not pay each other), and gives
 * it the potion to drink. Iron golems are the golemsmith's business. In CORE like BrewPotions, while it is awake and
 * not fighting.
 */
public class HealVillagers extends Behavior<Villager> {
    private static final double RANGE = 16.0;
    private static final float HURT = 0.6F;
    private static final double REACH = 2.5;
    private static final int GIVE_UP_AFTER_TICKS = 400;
    private static final int RESCAN_DELAY = 100;

    private @Nullable Villager patient;
    private long since;
    private long nextStartAllowed;

    public HealVillagers() {
        super(ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT), GIVE_UP_AFTER_TICKS + 20);
    }

    /** Emeralds a healing potion costs: the price of the brew that makes it. */
    static Optional<Integer> price(Villager cleric) {
        return Brews.upTo(cleric.getVillagerData().level()).stream()
                .filter(brew -> brew.price().isPresent() && PotionGoods.is(brew.output().stack(), Items.POTION, Potions.HEALING))
                .map(brew -> brew.price().get()).findFirst();
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager body) {
        if (level.getGameTime() < nextStartAllowed || body.isTrading() || !ClericFight.isCleric(body) || body.isSleeping()) return false;
        if (body.getBrain().isActive(Activity.FIGHT) || body.getBrain().isActive(Activity.PANIC) || body.getBrain().isActive(Activity.REST)) return false;
        patient = pickPatient(level, body);
        if (patient == null) nextStartAllowed = level.getGameTime() + RESCAN_DELAY;
        return patient != null;
    }

    private static @Nullable Villager pickPatient(ServerLevel level, Villager cleric) {
        Optional<Integer> price = price(cleric);
        if (price.isEmpty() || PotionGoods.count(level, cleric, Items.POTION, Potions.HEALING) < 1) return null;
        return level.getEntitiesOfClass(Villager.class, cleric.getBoundingBox().inflate(RANGE),
                        other -> other != cleric && other.isAlive() && !other.isSleeping() && other.getHealth() < other.getMaxHealth() * HURT
                                && Economy.villagerBudget(Economy.emeralds(level, other)) >= price.get())
                .stream().min(Comparator.comparingDouble(cleric::distanceToSqr)).orElse(null);
    }

    @Override
    protected void start(ServerLevel level, Villager body, long timestamp) {
        since = timestamp;
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager body, long timestamp) {
        return patient != null && patient.isAlive() && !body.isTrading() && timestamp - since < GIVE_UP_AFTER_TICKS && !body.getBrain().isActive(Activity.FIGHT);
    }

    @Override
    protected void tick(ServerLevel level, Villager body, long timestamp) {
        if (patient == null) return;
        if (body.distanceTo(patient) > REACH) {
            body.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new EntityTracker(patient, false), 0.6F, 1));
            body.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(patient, true));
            return;
        }
        heal(level, body, patient);
        patient = null;
    }

    private static void heal(ServerLevel level, Villager cleric, Villager patient) {
        int price = price(cleric).orElse(0);
        if (price <= 0 || Economy.villagerBudget(Economy.emeralds(level, patient)) < price) return;
        ItemStack potion = PotionGoods.takeOne(level, cleric, Items.POTION, Potions.HEALING);
        if (potion.isEmpty()) return;
        Economy.pay(level, patient, cleric, price);
        PotionContents contents = potion.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
        if (contents != null) contents.applyToLivingEntity(patient, 1.0F);
        cleric.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
        level.playSound(null, patient.blockPosition(), SoundEvents.GENERIC_DRINK.value(), SoundSource.NEUTRAL, 0.8F, 1.0F);
        WorkXp.award(cleric, WorkXp.HEALED);
    }

    @Override
    protected void stop(ServerLevel level, Villager body, long timestamp) {
        patient = null;
        body.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        body.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        nextStartAllowed = Math.max(nextStartAllowed, timestamp + 40);
    }
}
