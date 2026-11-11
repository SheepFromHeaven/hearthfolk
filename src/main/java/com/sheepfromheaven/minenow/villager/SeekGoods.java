package com.sheepfromheaven.minenow.villager;

import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * A working villager without the tool of its trade asks for one: it holds up an emerald when it could pay a player for
 * one, and walks up to a player who comes near its workplace, cheering, since a player can sell or throw it one. A
 * villager nearby selling a tool it can afford comes first ({@link BuyFromVillagers} goes to buy it). Fuel it can't run
 * without is the same once no villager sells it for {@link #PATIENCE} ticks and it can pay a player's price; a villager
 * too broke for that shakes its head instead ({@link IdleCues}).
 */
public class SeekGoods extends Behavior<Villager> {
    /** How long a worker out of fuel gives villagers to sell it some ({@link BuyFromVillagers}'s give-up window) before it asks a player. */
    static final int PATIENCE = 600;
    private static final double PLAYER_RANGE = 16;
    private static final int WORKPLACE_RANGE = 24;
    private static final float SPEED = 0.5F;
    private static final int CLOSE_ENOUGH = 2;
    private static final double CHEER_RANGE = 4;
    private static final int CHEER_INTERVAL = 80;
    private static final int RECHECK_INTERVAL = 40;
    private static final Map<Villager, Long> CHEERED_AT = new WeakHashMap<>();

    private @Nullable ServerPlayer player;
    private long nextCheck;
    private long nextCheer;
    private long shortSince = -1;

    public SeekGoods() {
        super(ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED), Integer.MAX_VALUE);
    }

    public static long cheeredAt(Villager villager) {
        return CHEERED_AT.getOrDefault(villager, -1L);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager body) {
        if (level.getGameTime() < nextCheck) return false;
        nextCheck = level.getGameTime() + RECHECK_INTERVAL;
        if (wantsATool(level, body)) return !BuyFromVillagers.canBuyToolFromAVillager(level, body);
        return wantsFuel(level, body, level.getGameTime());
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager body, long timestamp) {
        boolean tool = wantsATool(level, body);
        if (!tool && !(working(body) && Shortage.fuel(level, body).isPresent())) return false;
        if (timestamp < nextCheck) return true;
        nextCheck = timestamp + RECHECK_INTERVAL;
        return tool ? !BuyFromVillagers.canBuyToolFromAVillager(level, body) : wantsFuel(level, body, timestamp);
    }

    @Override
    protected void tick(ServerLevel level, Villager body, long timestamp) {
        showEmerald(level, body);
        if (player == null || !player.isAlive() || timestamp % RECHECK_INTERVAL == 0) player = nearbyPlayer(level, body).orElse(null);
        if (player == null || busyElsewhere(body)) return;
        body.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(player, true));
        if (body.distanceTo(player) > CLOSE_ENOUGH + 0.5) {
            body.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new EntityTracker(player, false), SPEED, CLOSE_ENOUGH));
        } else {
            body.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        }
        if (body.distanceTo(player) <= CHEER_RANGE && timestamp >= nextCheer) {
            level.playSound(null, body.blockPosition(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1.0F, body.getVoicePitch());
            CHEERED_AT.put(body, timestamp);
            nextCheer = timestamp + CHEER_INTERVAL;
        }
    }

    @Override
    protected void stop(ServerLevel level, Villager body, long timestamp) {
        if (player != null) {
            body.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            body.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        }
        player = null;
        if (body.getMainHandItem().is(Items.EMERALD)) body.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
    }

    /** Picking up a thrown tool, buying, eating or gathering its belongings walk elsewhere and come first. */
    private static boolean busyElsewhere(Villager body) {
        return body.getBrain().getRunningBehaviors().stream().anyMatch(behavior -> behavior instanceof PickUpGiftedTool
                || behavior instanceof BuyFromVillagers || behavior instanceof EatMeals || behavior instanceof Belongings.Gather);
    }

    /** Out of fuel with nobody selling it, for long enough, and with the emeralds to pay a player for it. */
    private boolean wantsFuel(ServerLevel level, Villager body, long now) {
        if (!working(body) || Shortage.fuel(level, body).isEmpty() || Shortage.canBuyFromAVillager(level, body)) {
            shortSince = -1;
            return false;
        }
        if (shortSince < 0) shortSince = now;
        return now - shortSince >= PATIENCE && Shortage.canPayAPlayer(level, body);
    }

    private static boolean working(Villager body) {
        return !body.isBaby() && !body.isTrading() && !body.isSleeping() && body.getBrain().isActive(Activity.WORK);
    }

    private static boolean wantsATool(ServerLevel level, Villager body) {
        return working(body)
                && TradeTable.forVillager(body).filter(IdleReason::requiresTool).isPresent() && !Unloading.ownsTool(level, body);
    }

    /** Vanilla's trade display uses the main hand while a player looks at it; the emerald goes back up once it's done. */
    private static void showEmerald(ServerLevel level, Villager body) {
        boolean canPay = wantsATool(level, body) ? TradeTable.forVillager(body).map(table -> table.toolsUpTo(body.getVillagerData().level()).stream()
                .anyMatch(tool -> tool.price() <= Economy.emeralds(level, body) - Economy.FOOD_MONEY)).orElse(false)
                : Shortage.canPayAPlayer(level, body);
        if (canPay && body.getMainHandItem().isEmpty() && !body.getBrain().hasMemoryValue(MemoryModuleType.INTERACTION_TARGET)) {
            body.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.EMERALD));
            body.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        } else if (!canPay && body.getMainHandItem().is(Items.EMERALD)) {
            body.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }
    }

    private static Optional<ServerPlayer> nearbyPlayer(ServerLevel level, Villager body) {
        Optional<BlockPos> site = body.getBrain().getMemory(MemoryModuleType.JOB_SITE)
                .filter(pos -> pos.dimension() == level.dimension()).map(GlobalPos::pos);
        return level.players().stream()
                .filter(p -> p.isAlive() && !p.isSpectator() && p.distanceTo(body) <= PLAYER_RANGE)
                .filter(p -> site.map(pos -> pos.closerToCenterThan(p.position(), WORKPLACE_RANGE)).orElse(true))
                .min(java.util.Comparator.comparingDouble(p -> p.distanceToSqr(body)));
    }
}
