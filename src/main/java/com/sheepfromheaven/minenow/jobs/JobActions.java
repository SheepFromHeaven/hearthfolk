package com.sheepfromheaven.minenow.jobs;

import com.sheepfromheaven.minenow.inhabitant.InhabitantEntity;
import com.sheepfromheaven.minenow.structure.DominionStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Shared tick-loop building blocks reused across {@link Job} implementations - walking to a target,
 * swinging a tool at a block over several ticks, and fetching/depositing items at a structure's storage.
 * Each helper takes whatever mutable per-job state it needs (see {@link Nav}, {@link SwingProgress},
 * {@link ContainerTarget}) as an explicit parameter rather than owning it itself, so jobs keep their own
 * phase enum and simply hold one of these alongside it. Every helper here follows the same "true means
 * done/arrived" return convention, regardless of what {@link Job#tick} itself returns.
 */
public final class JobActions {
    private JobActions() {}

    private static final int ARRIVE_DISTANCE_SQ = 4;
    private static final int REPATH_INTERVAL = 60;
    private static final double WALK_SPEED = 0.6;

    /** Per-job repath-cooldown state for {@link #walkTo}/{@link #goToJobsite}. */
    public static final class Nav {
        private int repathCooldown;
    }

    /** Per-job progress counter for {@link #tickSwing}, {@link #mineBlockWithTool} and {@link #placeBlockWithTool}. */
    public static final class SwingProgress {
        public int remainingTicks;
    }

    /** Per-job "which container am I walking to/waiting on" state for the fetch/deposit helpers. */
    public static final class ContainerTarget {
        @Nullable
        public BlockPos pos;
    }

    /** Walks the inhabitant toward {@code target}, repathing periodically. Returns true once arrived. */
    public static boolean walkTo(InhabitantEntity inhabitant, BlockPos target, Nav nav) {
        boolean arrived = inhabitant.distanceToSqr(target.getX() + 0.5, target.getY(), target.getZ() + 0.5) <= ARRIVE_DISTANCE_SQ;
        if (arrived) {
            inhabitant.getNavigation().stop();
            return true;
        }
        // The navigator can report itself as "done" while still short of the target (e.g. stopped
        // just outside a doorway), so force a fresh path periodically rather than only when idle.
        if (inhabitant.getNavigation().isDone() || --nav.repathCooldown <= 0) {
            inhabitant.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, WALK_SPEED);
            nav.repathCooldown = REPATH_INTERVAL;
        }
        return false;
    }

    /** Walks the inhabitant to the workplace's approach position. Returns true once arrived. */
    public static boolean goToJobsite(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace, Nav nav) {
        return walkTo(inhabitant, workplace.findApproachPos(level), nav);
    }

    /**
     * Ticks a swing cycle aimed at {@code pos}: looks at the target every tick, plays the arm-swing
     * animation and fires {@code onImpact} at the configured impact tick, then keeps counting down to
     * the end of the cycle. Callers must set {@code progress.remainingTicks = totalTicks} before the
     * first tick of the swing. Returns true once the full cycle (impact + follow-through) has elapsed.
     */
    public static boolean tickSwing(InhabitantEntity inhabitant, BlockPos pos, SwingProgress progress,
            int totalTicks, int impactTick, Runnable onImpact) {
        inhabitant.getLookControl().setLookAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        --progress.remainingTicks;
        if (progress.remainingTicks == totalTicks - impactTick) {
            inhabitant.swing(InteractionHand.MAIN_HAND);
            onImpact.run();
        }
        return progress.remainingTicks <= 0;
    }

    /** Tuning for {@link #mineBlockWithTool}. */
    public record MineOptions(int totalTicks, int impactTick, boolean requireCorrectTool, int toolDamagePerBlock) {}

    /**
     * Swings {@code tool} at the block at {@code pos} over {@code options.totalTicks()}, breaking it and
     * collecting its drops at the impact tick. If {@code options.requireCorrectTool()} is set and the
     * block requires a correct tool the caller doesn't have, does nothing and reports done immediately
     * so the caller can react (e.g. fall back to a "missing tool" phase) rather than swinging forever.
     * Returns true once the block is gone (already-air counts as done too) or the cycle has finished.
     */
    public static boolean mineBlockWithTool(InhabitantEntity inhabitant, ServerLevel level, BlockPos pos,
            ItemStack tool, SwingProgress progress, MineOptions options) {
        var state = level.getBlockState(pos);
        if (state.isAir()) {
            return true;
        }
        if (options.requireCorrectTool() && state.requiresCorrectToolForDrops() && !tool.isCorrectToolForDrops(state)) {
            return true;
        }
        return tickSwing(inhabitant, pos, progress, options.totalTicks(), options.impactTick(), () -> {
            var soundType = state.getSoundType();
            level.playSound(null, pos, soundType.getBreakSound(), SoundSource.BLOCKS, soundType.getVolume(), soundType.getPitch());
            for (var drop : Block.getDrops(state, level, pos, level.getBlockEntity(pos), inhabitant, tool)) {
                inhabitant.getInventory().addItem(drop);
            }
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            if (options.toolDamagePerBlock() > 0 && !tool.isEmpty()) {
                tool.hurtAndBreak(options.toolDamagePerBlock(), inhabitant, EquipmentSlot.MAINHAND);
            }
        });
    }

    /**
     * How many ticks it should take to fully break {@code state} with {@code tool}, mirroring vanilla's
     * {@code BlockBehaviour#getDestroyProgress} - block hardness divided by the tool's real mining speed
     * for that block (its tier/material bonus, via {@link ItemStack#getDestroySpeed}), using the
     * "correct tool" 30-tick divisor since callers are expected to have already gated out incorrect-tool
     * blocks before ever reaching here. This is what makes a wooden pickaxe visibly slower than a
     * diamond one on the same block, instead of every block taking one fixed animation length.
     */
    public static int breakTicksFor(ServerLevel level, BlockPos pos, BlockState state, ItemStack tool) {
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness <= 0f) {
            return 1;
        }
        float toolSpeed = tool.getDestroySpeed(state);
        if (toolSpeed <= 0f) {
            toolSpeed = 1.0f;
        }
        return Math.max(1, (int) Math.ceil(hardness * 30f / toolSpeed));
    }

    /**
     * Like {@link #mineBlockWithTool} but times the swing to {@link #breakTicksFor} instead of a fixed
     * animation length, and broadcasts vanilla-style crack-stage progress to nearby players via {@link
     * ServerLevel#destroyBlockProgress} while swinging - so mining reads exactly like a player doing it:
     * harder blocks and weaker tools take visibly longer, with cracks forming as the block gets closer
     * to breaking, rather than every block instantly vanishing. Spends 1 durability point on {@code
     * tool} per block broken, same as vanilla survival mining. {@code onBroken} fires exactly once
     * whenever this returns true - whether {@code pos} just got mined, or was already open (e.g.
     * revisiting an already-carved tunnel on a later trip) - so callers that advance a cursor/counter
     * from it don't need to special-case "nothing to actually break here".
     */
    public static boolean mineBlockRealistically(InhabitantEntity inhabitant, ServerLevel level, BlockPos pos, ItemStack tool, SwingProgress progress, Runnable onBroken) {
        var state = level.getBlockState(pos);
        if (state.isAir()) {
            onBroken.run();
            return true;
        }
        int totalTicks = breakTicksFor(level, pos, state, tool);
        boolean done = tickSwing(inhabitant, pos, progress, totalTicks, totalTicks, () -> {
            level.destroyBlockProgress(inhabitant.getId(), pos, -1);
            var soundType = state.getSoundType();
            level.playSound(null, pos, soundType.getBreakSound(), SoundSource.BLOCKS, soundType.getVolume(), soundType.getPitch());
            for (var drop : Block.getDrops(state, level, pos, level.getBlockEntity(pos), inhabitant, tool)) {
                inhabitant.getInventory().addItem(drop);
            }
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            if (!tool.isEmpty()) {
                tool.hurtAndBreak(1, inhabitant, EquipmentSlot.MAINHAND);
            }
            onBroken.run();
        });
        if (!done) {
            int stage = Math.min(9, (int) ((float) (totalTicks - progress.remainingTicks) / totalTicks * 10f));
            level.destroyBlockProgress(inhabitant.getId(), pos, stage);
        }
        return done;
    }

    /**
     * Swings at {@code pos} over {@code totalTicks} and places {@code toPlace} there at the impact tick
     * (e.g. a sapling going into the ground). Returns true once the cycle has finished.
     */
    public static boolean placeBlockWithTool(InhabitantEntity inhabitant, ServerLevel level, BlockPos pos,
            BlockState toPlace, SwingProgress progress, int totalTicks, int impactTick) {
        return tickSwing(inhabitant, pos, progress, totalTicks, impactTick, () -> level.setBlockAndUpdate(pos, toPlace));
    }

    /** Locates a container in the structure's storage holding an item matching {@code matcher}, without taking anything. */
    @Nullable
    public static BlockPos searchStructureForItem(ServerLevel level, DominionStructure workplace, Predicate<ItemStack> matcher) {
        return ContainerUtil.findContainerWith(level, workplace.findStorageContainers(level), matcher);
    }

    /**
     * Searches the structure's storage for an item matching {@code matcher}, walks the inhabitant there,
     * and extracts it once in range and off cooldown. Returns the extracted stack, or {@link ItemStack#EMPTY}
     * while still searching/traveling/waiting or if nothing matching exists. {@code wholeStack} controls
     * whether a whole matching stack is taken ({@link ContainerUtil#extractFirst}) or a single item
     * ({@link ContainerUtil#extractOne}).
     */
    public static ItemStack takeFromContainer(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace,
            ContainerActionPacer actionPacer, Nav nav, ContainerTarget target, Predicate<ItemStack> matcher, boolean wholeStack) {
        if (target.pos == null) {
            target.pos = searchStructureForItem(level, workplace, matcher);
            if (target.pos == null) {
                return ItemStack.EMPTY;
            }
        }
        if (!walkTo(inhabitant, target.pos, nav)) {
            return ItemStack.EMPTY;
        }
        if (!actionPacer.isReady(target.pos)) {
            return ItemStack.EMPTY;
        }
        inhabitant.getLookControl().setLookAt(target.pos.getX() + 0.5, target.pos.getY() + 0.5, target.pos.getZ() + 0.5);
        var result = ItemStack.EMPTY;
        if (level.getBlockEntity(target.pos) instanceof Container container) {
            result = wholeStack ? ContainerUtil.extractFirst(container, inhabitant, matcher) : ContainerUtil.extractOne(container, inhabitant, matcher);
        }
        if (!result.isEmpty()) {
            actionPacer.recordAction(target.pos);
        }
        // Either taken, or the container was empty by the time we arrived (race with another job/player) -
        // either way, rescan from scratch next call rather than getting stuck on a stale target.
        target.pos = null;
        return result;
    }

    /**
     * Deposits every non-empty stack from the inhabitant's carried inventory into containers the
     * structure has room for, one stack per call. Returns true once the whole inventory is empty; false
     * while still searching for room, traveling, waiting on the action pacer, or with more left to
     * deposit next call.
     */
    public static boolean depositCarriedItems(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace,
            ContainerActionPacer actionPacer, Nav nav, ContainerTarget target) {
        var carried = inhabitant.getInventory();
        for (int i = 0; i < carried.getContainerSize(); i++) {
            var stack = carried.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (target.pos == null) {
                target.pos = ContainerUtil.findContainerAccepting(level, workplace.findStorageContainers(level), stack);
                if (target.pos == null) {
                    // No room anywhere right now - stay put and retry next call rather than getting stuck.
                    return false;
                }
            }
            if (!walkTo(inhabitant, target.pos, nav)) {
                return false;
            }
            if (!actionPacer.isReady(target.pos)) {
                return false;
            }
            inhabitant.getLookControl().setLookAt(target.pos.getX() + 0.5, target.pos.getY() + 0.5, target.pos.getZ() + 0.5);
            if (level.getBlockEntity(target.pos) instanceof Container container) {
                carried.setItem(i, ContainerUtil.insertItem(container, inhabitant, stack));
            }
            actionPacer.recordAction(target.pos);
            target.pos = null;
            return false;
        }
        return true;
    }
}
