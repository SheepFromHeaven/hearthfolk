package com.sheepfromheaven.minenow.villager;

import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * A villager whose trade file lists {@code crafts} (and is near its workstation, so not down a mine) keeps each of them (unlocked at its level) in stock: when its goods
 * hold fewer than the craft's {@code stock} and it carries the materials, it walks to its workstation and hammers for
 * as long as its level dictates ({@link CraftSkill}) before the item is made, wearing the tool of its trade where the craft needs it ({@code tool_uses}). Lives in CORE after MoveToTargetSink, like the other trades.
 */
public class CraftAtWorkstation extends Behavior<Villager> {
    private static final double NEAR = 24.0;
    private static final int SWING_EVERY = 10;
    private static final float SPEED = 0.5F;
    private static final double REACH = 2.0;
    private static final int GIVE_UP_AFTER_TICKS = 300;
    private static final int IDLE_RESCAN_DELAY = 100;

    private TradeTable.@Nullable Craft craft;
    private long startedAt;
    private long nextStartAllowed;
    private int hammering;

    public CraftAtWorkstation() {
        super(ImmutableMap.of(
                MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT
        ), 1200);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager body) {
        if (level.getGameTime() < nextStartAllowed || body.isTrading() || !body.getBrain().isActive(Activity.WORK)) return false;
        nextStartAllowed = level.getGameTime() + IDLE_RESCAN_DELAY;
        Optional<BlockPos> station = workstation(level, body);
        if (station.isEmpty() || station.get().distToCenterSqr(body.position()) > NEAR * NEAR || Math.abs(station.get().getY() - body.getY()) > 3) return false;
        craft = nextCraft(level, body).orElse(null);
        return craft != null;
    }

    @Override
    protected void start(ServerLevel level, Villager body, long timestamp) {
        startedAt = timestamp;
        hammering = 0;
        walk(level, body);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager body, long timestamp) {
        return craft != null && !body.isTrading() && body.getBrain().isActive(Activity.WORK) && workstation(level, body).isPresent()
                && (hammering > 0 || timestamp - startedAt < GIVE_UP_AFTER_TICKS);
    }

    @Override
    protected void tick(ServerLevel level, Villager body, long timestamp) {
        BlockPos station = workstation(level, body).orElse(null);
        if (station == null) return;
        if (body.distanceToSqr(Vec3.atBottomCenterOf(station)) > REACH * REACH) {
            if (!body.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) walk(level, body);
            return;
        }
        body.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        body.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(station));
        if (hammering % SWING_EVERY == 0) {
            body.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
            SoundEvent sound = body.getVillagerData().profession().value().workSound();
            level.playSound(null, station, sound != null ? sound : SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 0.4F, 0.9F + body.getRandom().nextFloat() * 0.2F);
        }
        if (++hammering < CraftSkill.ticks(body.getVillagerData().level())) return;
        if (hasMaterials(level, body, craft) && (craft.toolUses() == 0 || Tools.has(body))) {
            for (TradeTable.Material material : craft.materials()) Economy.take(level, body, material.item(), material.count());
            Economy.give(level, body, new ItemStack(craft.item(), craft.count()));
            for (int use = 0; use < craft.toolUses(); use++) Tools.wear(level, body);
            WorkXp.award(body, WorkXp.CRAFTED);
        }
        craft = null;
    }

    @Override
    protected void stop(ServerLevel level, Villager body, long timestamp) {
        craft = null;
        hammering = 0;
        body.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        body.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        nextStartAllowed = Math.min(nextStartAllowed, timestamp + 20);
    }

    private static Optional<TradeTable.Craft> nextCraft(ServerLevel level, Villager body) {
        return TradeTable.forVillager(body).stream()
                .flatMap(table -> table.craftsUpTo(body.getVillagerData().level()).stream())
                .filter(craft -> Economy.count(level, body, craft.item()) < table(body).map(t -> t.stockTarget(craft)).orElse(craft.stock()) + Commissions.reserved(body, craft.item())
                        && hasMaterials(level, body, craft) && (craft.toolUses() == 0 || Tools.has(body)))
                .findFirst();
    }

    private static Optional<TradeTable> table(Villager body) {
        return TradeTable.forVillager(body);
    }

    private static boolean hasMaterials(ServerLevel level, Villager body, TradeTable.Craft craft) {
        return craft.materials().stream().allMatch(material -> Economy.count(level, body, material.item()) >= material.count());
    }

    private static void walk(ServerLevel level, Villager body) {
        workstation(level, body).ifPresent(station -> {
            body.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(station, SPEED, 1));
            body.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(station));
        });
    }

    private static Optional<BlockPos> workstation(ServerLevel level, Villager body) {
        return body.getBrain().getMemory(MemoryModuleType.JOB_SITE)
                .filter(site -> site.dimension() == level.dimension())
                .map(GlobalPos::pos);
    }
}
