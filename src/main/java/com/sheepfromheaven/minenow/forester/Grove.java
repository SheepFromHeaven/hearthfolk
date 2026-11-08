package com.sheepfromheaven.minenow.forester;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The trees around a nursery a forester may fell. A tree is a group of connected logs with natural leaves (not
 * persistent, so player-placed leaves and bare log builds never count) whose lowest logs all stand on dirt: putting
 * cobblestone (or anything else) under its trunk keeps it.
 */
final class Grove {
    static final int HEIGHT = 4;
    private static final int TREE_SPREAD = 6;
    private static final int MAX_LOGS = 300;
    static final TagKey<Block> KEEPS_TREE = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("c", "cobblestones"));

    private Grove() {}

    /** A tree to fell: its logs, and the lowest ones (the stumps, where saplings go back). */
    record Tree(List<BlockPos> logs, List<BlockPos> stumps) {
        BlockPos base() {
            return stumps.getFirst();
        }
    }

    static List<Tree> trees(ServerLevel level, BlockPos nursery, int range) {
        List<Tree> trees = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        for (BlockPos pos : BlockPos.betweenClosed(nursery.offset(-range, -HEIGHT, -range), nursery.offset(range, HEIGHT, range))) {
            if (seen.contains(pos) || !level.getBlockState(pos).is(BlockTags.LOGS) || level.getBlockState(pos.below()).is(BlockTags.LOGS)) continue;
            Tree tree = tree(level, pos.immutable(), seen);
            if (tree != null) trees.add(tree);
        }
        return trees;
    }

    private static Tree tree(ServerLevel level, BlockPos start, Set<BlockPos> seen) {
        List<BlockPos> logs = new ArrayList<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start);
        boolean natural = false;
        while (!queue.isEmpty() && logs.size() < MAX_LOGS) {
            BlockPos pos = queue.poll();
            logs.add(pos);
            for (Direction side : Direction.values()) {
                BlockState next = level.getBlockState(pos.relative(side));
                if (next.getBlock() instanceof LeavesBlock && !next.getValue(LeavesBlock.PERSISTENT)) natural = true;
            }
            for (BlockPos near : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
                if (near.getY() < start.getY() || Math.abs(near.getX() - start.getX()) > TREE_SPREAD || Math.abs(near.getZ() - start.getZ()) > TREE_SPREAD) continue;
                if (seen.contains(near) || !level.getBlockState(near).is(BlockTags.LOGS)) continue;
                BlockPos found = near.immutable();
                seen.add(found);
                queue.add(found);
            }
        }
        if (!natural) return null;
        List<BlockPos> stumps = logs.stream().filter(pos -> !level.getBlockState(pos.below()).is(BlockTags.LOGS)).toList();
        if (stumps.stream().anyMatch(pos -> !level.getBlockState(pos.below()).is(BlockTags.DIRT))) return null;
        logs.sort(Comparator.comparingInt((BlockPos pos) -> pos.getY()).reversed());
        return new Tree(logs, stumps);
    }
}
