package com.sheepfromheaven.minenow.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

/**
 * Vanilla's {@link WalkNodeEvaluator} classifies closed fence gates as {@link PathType#FENCE},
 * the same bucket as a plain, never-openable fence or wall — there's no per-mob malus that can
 * tell those apart, since {@code getPathTypeOfMob} returns {@code FENCE} for that whole bucket
 * before any malus lookup happens. Reporting closed gates as {@code DOOR_WOOD_CLOSED} instead
 * reuses the existing (already-working) door traversal logic: it's walkable when the mob's
 * navigation has {@code canOpenDoors} set, and {@link OpenFenceGateGoal} does the actual opening.
 */
public class FenceGateAwareNodeEvaluator extends WalkNodeEvaluator {
    @Override
    public PathType getPathType(PathfindingContext context, int x, int y, int z) {
        var state = context.getBlockState(new BlockPos(x, y, z));
        if (state.getBlock() instanceof FenceGateBlock) {
            return state.getValue(FenceGateBlock.OPEN) ? PathType.DOOR_OPEN : PathType.DOOR_WOOD_CLOSED;
        }
        return super.getPathType(context, x, y, z);
    }
}
