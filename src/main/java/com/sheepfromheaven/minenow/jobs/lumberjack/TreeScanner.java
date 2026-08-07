package com.sheepfromheaven.minenow.jobs.lumberjack;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Distinguishes a naturally-grown tree trunk from a player-built log structure so the lumberjack
 * never tears down a house. The trunk is a flood fill of every block of the *same log species*
 * connected to the root - face-adjacent, or diagonally offset by one block the way acacia (and
 * similar) trees jump/fork - so orientation doesn't matter, only species. A trunk only counts as a
 * tree once a non-persistent (naturally decaying) leaf is found touching it; any other solid
 * neighbor (planks, stone, a persistent decorative leaf, a *different* log species) aborts the scan
 * entirely rather than risking a false positive. Replaceable vegetation (grass, ferns, flowers) and
 * vines beside the trunk are ignored, since they commonly grow right up against natural trees and
 * carry no structural meaning.
 */
public final class TreeScanner {
    /** Safety cap on the canopy flood fill. */
    private static final int MAX_LEAF_FLOOD = 512;

    /** What one face-adjacent neighbor of a trunk log (that isn't the same log species) means for tree recognition. */
    public enum NeighborOutcome { IGNORED, LEAF_CONFIRMS, BLOCKS }

    /** The full recognized tree: the trunk logs (what the lumberjack chops) and the flood-filled canopy. */
    public record TreeShape(List<BlockPos> trunk, Set<BlockPos> leaves) {
        public Set<BlockPos> allBlocks() {
            var all = new HashSet<>(trunk);
            all.addAll(leaves);
            return all;
        }
    }

    private TreeScanner() {
    }

    /** Max number of connected logs a trunk can contain (see {@code Config#maxTrunkSize} - configurable since large jungle/dark oak trees need more than the default). */
    public static int maxTrunkSize() {
        return com.sheepfromheaven.minenow.core.Config.maxTrunkSize;
    }

    public static Optional<List<BlockPos>> findTree(Level level, BlockPos rootPos) {
        return scan(level, rootPos).map(TreeShape::trunk);
    }

    /** Same recognition as {@link #findTree}, but also flood-fills the whole leaf canopy touching the trunk. */
    public static Optional<TreeShape> findTreeShape(Level level, BlockPos rootPos) {
        return scan(level, rootPos);
    }

    private static Optional<TreeShape> scan(Level level, BlockPos rootPos) {
        var rootState = level.getBlockState(rootPos);
        if (!rootState.is(BlockTags.LOGS) || !isSoil(level.getBlockState(rootPos.below()))) {
            return Optional.empty();
        }
        var logBlock = rootState.getBlock();

        var trunk = new LinkedHashSet<BlockPos>();
        var leafTouchPoints = new ArrayList<BlockPos>();
        var queue = new ArrayDeque<BlockPos>();
        trunk.add(rootPos);
        queue.add(rootPos);

        while (!queue.isEmpty()) {
            if (trunk.size() > maxTrunkSize()) {
                break;
            }
            var cur = queue.poll();

            for (var dir : Direction.values()) {
                var neighborPos = cur.relative(dir);
                var neighborState = level.getBlockState(neighborPos);
                if (neighborState.is(logBlock)) {
                    if (trunk.add(neighborPos)) {
                        queue.add(neighborPos);
                    }
                    continue;
                }
                switch (classifyNeighbor(neighborState)) {
                    case BLOCKS -> {
                        return Optional.empty();
                    }
                    case LEAF_CONFIRMS -> leafTouchPoints.add(neighborPos);
                    case IGNORED -> {}
                }
            }

            // Acacia-style trees jump the trunk diagonally by one block (and can fork into more than
            // one such branch) instead of always continuing face-adjacent - these aren't reachable by
            // the face-neighbor scan above, so check for them separately.
            for (var candidate : diagonalContinuations(cur)) {
                if (level.getBlockState(candidate).is(logBlock) && trunk.add(candidate)) {
                    queue.add(candidate);
                }
            }
        }

        if (leafTouchPoints.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new TreeShape(new ArrayList<>(trunk), floodFillLeaves(level, leafTouchPoints)));
    }

    /** The 4 positions one block up and to one horizontal side of {@code cur}. */
    private static List<BlockPos> diagonalContinuations(BlockPos cur) {
        var above = cur.above();
        var candidates = new ArrayList<BlockPos>(4);
        for (var dir : Direction.Plane.HORIZONTAL) {
            candidates.add(above.relative(dir));
        }
        return candidates;
    }

    /**
     * Expands outward from every leaf block that directly touched the trunk to the whole connected,
     * non-persistent canopy - a tree's leaves are usually much wider than just the blocks touching
     * the trunk, and can touch it at more than one point (a low branch as well as the main canopy).
     */
    private static Set<BlockPos> floodFillLeaves(Level level, List<BlockPos> touchPoints) {
        var leaves = new HashSet<BlockPos>();
        var queue = new ArrayDeque<>(touchPoints);
        leaves.addAll(touchPoints);

        while (!queue.isEmpty() && leaves.size() < MAX_LEAF_FLOOD) {
            var pos = queue.poll();
            for (var dir : Direction.values()) {
                var neighbor = pos.relative(dir);
                if (leaves.contains(neighbor)) {
                    continue;
                }
                var state = level.getBlockState(neighbor);
                if (state.is(BlockTags.LEAVES) && !state.getValue(LeavesBlock.PERSISTENT)) {
                    leaves.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }
        return leaves;
    }

    /**
     * Classifies a trunk log's face-adjacent neighbor that is NOT the same log species as the trunk
     * (same-species neighbors are flood-filled into the trunk directly - see {@link #scan}). Shared
     * with {@link TreeScannerDebugger} so the debug wand explains exactly the same rule the real
     * scan just applied.
     */
    public static NeighborOutcome classifyNeighbor(BlockState neighborState) {
        if (neighborState.isAir()) {
            return NeighborOutcome.IGNORED;
        }
        if (neighborState.is(Blocks.BEEHIVE) || neighborState.is(Blocks.BEE_NEST) || neighborState.is(Blocks.VINE)) {
            return NeighborOutcome.IGNORED;
        }
        if (isSoil(neighborState)) {
            // The ground beneath (or a hillside beside) the trunk - now that every direction of every
            // trunk log is checked, not just the horizontal ones, this would otherwise abort every scan
            // via the soil directly under the root.
            return NeighborOutcome.IGNORED;
        }
        if (neighborState.is(BlockTags.LEAVES) && !neighborState.getValue(LeavesBlock.PERSISTENT)) {
            return NeighborOutcome.LEAF_CONFIRMS;
        }
        if (neighborState.getBlock() instanceof VegetationBlock) {
            return NeighborOutcome.IGNORED;
        }
        return NeighborOutcome.BLOCKS;
    }

    static boolean isSoil(BlockState state) {
        return state.is(BlockTags.DIRT) || state.is(Blocks.GRASS_BLOCK);
    }
}
