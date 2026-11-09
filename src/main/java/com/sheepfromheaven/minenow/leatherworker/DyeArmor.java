package com.sheepfromheaven.minenow.leatherworker;

import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/** At its cauldron the leatherworker dyes the plain leather armor it holds with a dye it holds, one piece at a time. */
public class DyeArmor extends Behavior<Villager> {
    private static final double REACH = 2.0;
    private static final float SPEED = 0.5F;
    private static final int RESCAN = 200;
    private static final int GIVE_UP_AFTER_TICKS = 300;

    private long since;
    private long nextStartAllowed;

    public DyeArmor() {
        super(ImmutableMap.of(
                MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT
        ), GIVE_UP_AFTER_TICKS + 20);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager body) {
        if (level.getGameTime() < nextStartAllowed || body.isTrading()) return false;
        if (!body.getVillagerData().profession().is(VillagerProfession.LEATHERWORKER) || !body.getBrain().isActive(Activity.WORK)) return false;
        nextStartAllowed = level.getGameTime() + RESCAN;
        return plainPiece(body) >= 0 && dye(body) >= 0;
    }

    @Override
    protected void start(ServerLevel level, Villager body, long timestamp) {
        since = timestamp;
        cauldron(level, body).ifPresent(pos -> {
            body.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(pos, SPEED, 1));
            body.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(pos));
        });
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager body, long timestamp) {
        return body.getBrain().isActive(Activity.WORK) && !body.isTrading() && timestamp - since < GIVE_UP_AFTER_TICKS && plainPiece(body) >= 0 && dye(body) >= 0;
    }

    @Override
    protected void tick(ServerLevel level, Villager body, long timestamp) {
        Optional<BlockPos> cauldron = cauldron(level, body);
        if (cauldron.isEmpty() || body.distanceToSqr(Vec3.atBottomCenterOf(cauldron.get())) > REACH * REACH) return;
        int pieceSlot = plainPiece(body);
        int dyeSlot = dye(body);
        if (pieceSlot < 0 || dyeSlot < 0) return;
        ItemStack dye = body.getInventory().getItem(dyeSlot);
        DyeColor color = dye.get(DataComponents.DYE);
        ItemStack piece = body.getInventory().getItem(pieceSlot);
        body.getInventory().setItem(pieceSlot, DyedItemColor.applyDyes(piece, List.of(color)));
        dye.shrink(1);
        level.playSound(null, cauldron.get(), SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.6F, 1.2F);
        body.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
    }

    @Override
    protected void stop(ServerLevel level, Villager body, long timestamp) {
        body.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        body.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
    }

    private static int plainPiece(Villager body) {
        for (int i = 0; i < body.getInventory().getContainerSize(); i++) {
            ItemStack stack = body.getInventory().getItem(i);
            if (Leatherworker.displayWorth(stack) > 0 && !stack.has(DataComponents.DYED_COLOR)) return i;
        }
        return -1;
    }

    private static int dye(Villager body) {
        for (int i = 0; i < body.getInventory().getContainerSize(); i++) {
            if (body.getInventory().getItem(i).has(DataComponents.DYE)) return i;
        }
        return -1;
    }

    private static Optional<BlockPos> cauldron(ServerLevel level, Villager body) {
        return body.getBrain().getMemory(MemoryModuleType.JOB_SITE).filter(pos -> pos.dimension() == level.dimension()).map(GlobalPos::pos);
    }
}
