package com.sheepfromheaven.minenow.miner;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A miner's quarry is the pit the player dug next to its furnace: the open cells one block below the furnace that touch
 * a side of the block under it, and all open cells connected to them at that level, within {@link #RANGE} blocks. The
 * miner digs that outline downwards layer by layer, never below {@link MinerSkill#depth} under the furnace.
 */
final class Quarry {
    static final int RANGE = 8;

    private final List<BlockPos> outline;
    private final int top;

    private Quarry(List<BlockPos> outline, int top) {
        this.outline = outline;
        this.top = top;
    }

    static Quarry around(ServerLevel level, BlockPos furnace) {
        int top = furnace.getY() - 1;
        BlockPos under = furnace.below();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        List<BlockPos> outline = new ArrayList<>();
        for (Direction side : Direction.Plane.HORIZONTAL) queue.add(under.relative(side));
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            if (!seen.add(pos) || Math.abs(pos.getX() - furnace.getX()) > RANGE || Math.abs(pos.getZ() - furnace.getZ()) > RANGE) continue;
            if (!level.getBlockState(pos).isAir()) continue;
            outline.add(pos);
            for (Direction side : Direction.Plane.HORIZONTAL) queue.add(pos.relative(side));
        }
        return new Quarry(outline, top);
    }

    boolean isEmpty() {
        return outline.isEmpty();
    }

    /** The cells of the highest layer that still has something the pickaxe can dig, or nothing if the quarry is done. */
    List<BlockPos> nextLayer(ServerLevel level, int bottom, ItemStack pickaxe) {
        for (int y = top - 1; y >= Math.max(bottom, level.getMinY() + 1); y--) {
            List<BlockPos> layer = new ArrayList<>();
            for (BlockPos column : outline) {
                BlockPos pos = column.atY(y);
                if (diggable(level, pos, pickaxe)) layer.add(pos);
            }
            if (!layer.isEmpty()) return layer;
        }
        return List.of();
    }

    boolean contains(BlockPos pos) {
        return pos.getY() < top && outline.contains(pos.atY(top));
    }

    Optional<BlockPos> anyColumn() {
        return outline.stream().findFirst();
    }

    static boolean diggable(ServerLevel level, BlockPos pos, ItemStack pickaxe) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty() || level.getBlockEntity(pos) != null) return false;
        if (state.getDestroySpeed(level, pos) < 0.0F) return false;
        if (state.requiresCorrectToolForDrops() && !pickaxe.isCorrectToolForDrops(state)) return false;
        for (Direction side : Direction.values()) {
            if (!level.getFluidState(pos.relative(side)).isEmpty()) return false;
        }
        return true;
    }
}
