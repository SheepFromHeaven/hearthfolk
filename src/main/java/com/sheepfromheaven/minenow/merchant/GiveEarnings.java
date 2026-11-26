package com.sheepfromheaven.minenow.merchant;

import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.Optional;

/**
 * A merchant back from its tour with {@link Merchant#EARNINGS} walks up to the nearest player and throws them the emeralds,
 * the way vanilla's villagers throw a gift to the hero of the village.
 */
public final class GiveEarnings extends Behavior<Villager> {
    private static final double PLAYER_RANGE = 16;
    private static final float SPEED = 0.5F;
    private static final int CLOSE_ENOUGH = 2;
    private static final int GIVE_UP = 600;

    private @Nullable ServerPlayer player;

    public GiveEarnings() {
        super(ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED), GIVE_UP);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager merchant) {
        if (!owes(merchant)) return false;
        player = nearestPlayer(level, merchant).orElse(null);
        return player != null;
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager merchant, long timestamp) {
        return owes(merchant) && player != null && player.isAlive() && !player.isSpectator() && player.distanceTo(merchant) <= PLAYER_RANGE;
    }

    @Override
    protected void tick(ServerLevel level, Villager merchant, long timestamp) {
        if (player == null) return;
        merchant.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(player, true));
        if (merchant.distanceTo(player) > CLOSE_ENOUGH + 0.5) {
            merchant.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new EntityTracker(player, false), SPEED, CLOSE_ENOUGH));
            return;
        }
        int emeralds = merchant.getData(Merchant.EARNINGS);
        merchant.setData(Merchant.EARNINGS, 0);
        for (int left = emeralds; left > 0; left -= Items.EMERALD.getDefaultMaxStackSize()) {
            BehaviorUtils.throwItem(merchant, new ItemStack(Items.EMERALD, Math.min(left, Items.EMERALD.getDefaultMaxStackSize())), player.position());
        }
        level.playSound(null, merchant.blockPosition(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1.0F, merchant.getVoicePitch());
    }

    @Override
    protected void stop(ServerLevel level, Villager merchant, long timestamp) {
        merchant.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        merchant.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        player = null;
    }

    private static boolean owes(Villager merchant) {
        return Merchant.isMerchant(merchant) && !merchant.isBaby() && !merchant.isSleeping() && !merchant.isTrading()
                && merchant.getData(Merchant.TOUR).isEmpty() && merchant.getData(Merchant.EARNINGS) > 0;
    }

    private static Optional<ServerPlayer> nearestPlayer(ServerLevel level, Villager merchant) {
        return level.players().stream()
                .filter(p -> p.isAlive() && !p.isSpectator() && p.distanceTo(merchant) <= PLAYER_RANGE)
                .min(Comparator.comparingDouble(p -> p.distanceToSqr(merchant)));
    }
}
