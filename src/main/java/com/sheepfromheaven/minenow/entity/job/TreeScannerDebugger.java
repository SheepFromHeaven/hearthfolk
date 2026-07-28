package com.sheepfromheaven.minenow.entity.job;

import com.sheepfromheaven.minenow.network.ModNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Dev tool backing the Tree Scanner Wand: right-clicking a log starts a scan that flood-fills the
 * trunk 4 blocks per second - following every same-species log connected to it, including
 * diagonal acacia-style jumps/forks {@link TreeScanner} would - highlighting each block it checks
 * and narrating the decision in chat, so a rejected (or partially recognized) tree can be
 * diagnosed in-world instead of by guesswork.
 */
public final class TreeScannerDebugger {
    private static final int TICKS_PER_STEP = 5;
    /** Matches {@link ModNetwork.TreeScanHighlightPacket}'s category field. */
    private static final int CATEGORY_LOG = 0;
    private static final int CATEGORY_LEAF = 1;
    private static final int CATEGORY_BLOCKED = 2;

    private static final Map<UUID, Session> ACTIVE = new HashMap<>();

    private TreeScannerDebugger() {}

    public static void start(ServerPlayer player, ServerLevel level, BlockPos rootPos) {
        var rootState = level.getBlockState(rootPos);
        if (!rootState.is(BlockTags.LOGS)) {
            player.displayClientMessage(Component.literal("§c[TreeScanner] That's not a log block."), false);
            return;
        }
        if (!TreeScanner.isSoil(level.getBlockState(rootPos.below()))) {
            player.displayClientMessage(
                    Component.literal("§c[TreeScanner] No soil (dirt/grass) directly below - never recognized as a tree root."), false);
            return;
        }

        var session = new Session(level, rootPos, rootState.getBlock());
        ACTIVE.put(player.getUUID(), session);
        ModNetwork.sendTreeScanClear(player);
        player.displayClientMessage(Component.literal("§a[TreeScanner] Scanning from " + describePos(rootPos)
                + " - flood-filling every connected " + describeBlock(rootState) + ", 4 blocks per second..."), false);
    }

    public static void serverTick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        var it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            var player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                it.remove();
                continue;
            }
            var session = entry.getValue();
            if (session.ticksUntilStep-- > 0) {
                continue;
            }
            session.ticksUntilStep = TICKS_PER_STEP;
            if (step(player, session)) {
                finish(player, session);
                it.remove();
            }
        }
    }

    /** Advances the scan by one trunk block (breadth-first flood fill). Returns true once the queue is empty. */
    private static boolean step(ServerPlayer player, Session session) {
        if (session.queue.isEmpty()) {
            return true;
        }
        var level = session.level;
        var cur = session.queue.poll();
        ModNetwork.sendTreeScanHighlight(player, CATEGORY_LOG, List.of(cur));

        boolean branched = false;
        for (var dir : Direction.values()) {
            var neighborPos = cur.relative(dir);
            var neighborState = level.getBlockState(neighborPos);
            if (neighborState.is(session.logBlock)) {
                if (session.trunk.add(neighborPos)) {
                    session.queue.add(neighborPos);
                }
                continue;
            }
            switch (TreeScanner.classifyNeighbor(neighborState)) {
                case LEAF_CONFIRMS -> {
                    ModNetwork.sendTreeScanHighlight(player, CATEGORY_LEAF, List.of(neighborPos));
                    if (!session.confirmedTree) {
                        player.displayClientMessage(Component.literal("§a[TreeScanner] Decaying leaf found at " + describePos(neighborPos)
                                + " - this confirms a natural tree."), false);
                    }
                    session.confirmedTree = true;
                }
                case BLOCKS -> {
                    ModNetwork.sendTreeScanHighlight(player, CATEGORY_BLOCKED, List.of(neighborPos));
                    player.displayClientMessage(Component.literal("§c[TreeScanner] Blocked by " + describeBlock(neighborState) + " at "
                            + describePos(neighborPos) + " (not the same log species, a decaying leaf, beehive, vine, or replaceable plant)"
                            + " - scan aborted."), false);
                    session.aborted = true;
                    session.queue.clear();
                    return true;
                }
                case IGNORED -> {}
            }
        }

        for (var candidate : diagonalContinuations(cur)) {
            if (level.getBlockState(candidate).is(session.logBlock) && session.trunk.add(candidate)) {
                if (branched) {
                    player.displayClientMessage(Component.literal("§e[TreeScanner] Trunk forks - also following the branch at "
                            + describePos(candidate) + "."), false);
                } else {
                    player.displayClientMessage(Component.literal("§e[TreeScanner] Trunk jumps diagonally to " + describePos(candidate) + "."), false);
                }
                session.queue.add(candidate);
                branched = true;
            }
        }

        if (session.trunk.size() > TreeScanner.maxTrunkSize()) {
            player.displayClientMessage(Component.literal("§c[TreeScanner] Reached the max trunk size (" + TreeScanner.maxTrunkSize()
                    + " logs) - scan aborted."), false);
            session.aborted = true;
            session.queue.clear();
            return true;
        }

        return session.queue.isEmpty();
    }

    private static List<BlockPos> diagonalContinuations(BlockPos cur) {
        var above = cur.above();
        var candidates = new ArrayList<BlockPos>(4);
        for (var dir : Direction.Plane.HORIZONTAL) {
            candidates.add(above.relative(dir));
        }
        return candidates;
    }

    private static void finish(ServerPlayer player, Session session) {
        if (session.aborted) {
            return;
        }
        if (session.confirmedTree) {
            player.displayClientMessage(Component.literal("§a[TreeScanner] Trunk fully explored (" + session.trunk.size()
                    + " log(s)) - a decaying leaf was found beside it, so this IS recognized as a tree."), false);
            drawOutline(player, session);
        } else {
            player.displayClientMessage(Component.literal("§c[TreeScanner] Trunk fully explored (" + session.trunk.size()
                    + " log(s)) without ever finding a non-persistent leaf beside it - NOT recognized as a tree."), false);
        }
    }

    /** Re-runs the real recognition and highlights every trunk/leaf block it found, as a final outline. */
    private static void drawOutline(ServerPlayer player, Session session) {
        var shape = TreeScanner.findTreeShape(session.level, session.rootPos);
        if (shape.isEmpty()) {
            return;
        }
        ModNetwork.sendTreeScanClear(player);
        ModNetwork.sendTreeScanHighlight(player, CATEGORY_LOG, shape.get().trunk());
        ModNetwork.sendTreeScanHighlight(player, CATEGORY_LEAF, new ArrayList<>(shape.get().leaves()));
        player.displayClientMessage(Component.literal("§b[TreeScanner] Outline: " + shape.get().trunk().size() + " trunk log(s), "
                + shape.get().leaves().size() + " canopy leaf/leaves."), false);
    }

    private static String describePos(BlockPos pos) {
        return "(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
    }

    private static String describeBlock(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    private static final class Session {
        final ServerLevel level;
        final BlockPos rootPos;
        final Block logBlock;
        final ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        final HashSet<BlockPos> trunk = new HashSet<>();
        boolean confirmedTree;
        boolean aborted;
        int ticksUntilStep;

        Session(ServerLevel level, BlockPos rootPos, Block logBlock) {
            this.level = level;
            this.rootPos = rootPos;
            this.logBlock = logBlock;
            trunk.add(rootPos);
            queue.add(rootPos);
        }
    }
}
