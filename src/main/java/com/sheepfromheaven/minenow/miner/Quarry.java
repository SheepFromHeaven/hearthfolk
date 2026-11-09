package com.sheepfromheaven.minenow.miner;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A miner's quarry is the ground the player rimmed with cobblestone ({@code #minenow:mine_rim}) beside its furnace
 * minecart: the cart stands on the rim, flush in the ground, and the cells the closed rim encloses at that level, within
 * {@link #RANGE} blocks, are the outline (a rim with a gap encloses nothing). The miner digs the outline itself first,
 * then the mine laid out by {@link MineLayout} below it: the shaft layer by
 * layer down to a platform, then the platform's ladder hole, its tunnels one direction after another, its middle, and on
 * down, to {@link MinerSkill#depth} under the cart, or to the platform a redstone signal into the cart allows
 * ({@link MineLayout#deepestPlatform}: there only its tunnels, not its middle). The two layers right under a platform are dug together, column by
 * column, since one layer alone would leave only a one-block crawl space under the ring.
 */
final class Quarry {
    static final int RANGE = 8;
    static final TagKey<Block> RIM = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("minenow", "mine_rim"));
    private static final int LONGEST_TUNNEL = 24;
    private static final int SEAL_RANGE = 8;

    private final Set<BlockPos> outline;
    private final int top;
    private final @Nullable Ladder ladder;
    private final MineLayout layout;

    private Quarry(Set<BlockPos> outline, int top, @Nullable Ladder ladder, MineLayout layout) {
        this.outline = outline;
        this.top = top;
        this.ladder = ladder;
        this.layout = layout;
    }

    static Quarry around(ServerLevel level, BlockPos furnace) {
        int top = furnace.getY() - 1;
        BlockPos under = furnace.below();
        Set<BlockPos> outline = Set.of();
        if (level.getBlockState(under).is(RIM)) {
            for (Direction side : Direction.Plane.HORIZONTAL) {
                Set<BlockPos> enclosed = enclosed(level, furnace, under.relative(side));
                if (enclosed.size() > outline.size()) outline = enclosed;
            }
        }
        MineLayout shape = new MineLayout(outline, top, under);
        Ladder ladder = pickLadder(level, furnace, outline, shape);
        return new Quarry(outline, top, ladder, ladder == null ? shape : new MineLayout(outline, top, ladder.top()));
    }

    /** The cells connected to {@code start} inside the rim, or none if they reach past {@link #RANGE} (the rim isn't closed). */
    private static Set<BlockPos> enclosed(ServerLevel level, BlockPos furnace, BlockPos start) {
        Set<BlockPos> cells = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            if (level.getBlockState(pos).is(RIM) || !cells.add(pos)) continue;
            if (Math.abs(pos.getX() - furnace.getX()) > RANGE || Math.abs(pos.getZ() - furnace.getZ()) > RANGE) return Set.of();
            for (Direction side : Direction.Plane.HORIZONTAL) queue.add(pos.relative(side));
        }
        return cells;
    }

    /** Where the ladder goes down: a cell beside a solid wall whose top is free ground to step out onto, off the tunnel walls, nearest the cart first. */
    private static @Nullable Ladder pickLadder(ServerLevel level, BlockPos furnace, Set<BlockPos> outline, MineLayout shape) {
        return outline.stream()
                .sorted(Comparator.comparingDouble((BlockPos cell) -> cell.distSqr(furnace)).thenComparing(BlockPos::asLong))
                .flatMap(cell -> Direction.Plane.HORIZONTAL.stream()
                        .filter(side -> {
                            BlockPos wall = cell.relative(side);
                            BlockPos exit = wall.above();
                            return !outline.contains(wall) && !exit.equals(furnace) && !shape.tunnelWall(cell, side)
                                    && level.getBlockState(wall).isFaceSturdy(level, wall, side.getOpposite())
                                    && level.getBlockState(exit).getCollisionShape(level, exit).isEmpty()
                                    && level.getBlockState(exit.above()).getCollisionShape(level, exit.above()).isEmpty();
                        })
                        .map(side -> new Ladder(cell, side.getOpposite()))
                        .findFirst().stream())
                .findFirst()
                .orElse(null);
    }

    /** A ladder column: the cells from {@code top} down under this outline cell, each ladder facing away from its wall. */
    record Ladder(BlockPos top, Direction facing) {
        BlockPos at(int y) {
            return top.atY(y);
        }
    }

    /** A torch spot: one block above a floor cell below the wanted block light, against an outer wall of the mine. */
    record TorchSpot(BlockPos pos, Direction facing) {}

    Optional<Ladder> ladder(ServerLevel level, BlockPos furnace) {
        return Optional.ofNullable(ladder);
    }

    int top() {
        return top;
    }

    Set<BlockPos> outline() {
        return outline;
    }

    boolean isEmpty() {
        return outline.isEmpty();
    }

    /** The feet level to work on {@code pos} from: above it in the shaft, on the platform for a tunnel cell, beside it for the ladder. */
    int standLevel(BlockPos pos) {
        if (isShaft(pos)) return pos.getY() + 1;
        for (int platform = pos.getY() - 1; platform >= pos.getY() - MineLayout.TUNNEL_HEIGHT; platform--) {
            if (layout.isPlatform(platform)) return platform + 1;
        }
        return pos.getY();
    }

    /** Whether {@code pos} is in the shaft, the marked layer included. */
    boolean isShaft(BlockPos pos) {
        return pos.getY() <= top && outline.contains(pos.atY(top));
    }

    /** Whether {@code pos} is down in the mine: in the shaft below the marked layer or in a tunnel. */
    boolean contains(BlockPos pos) {
        return isShaft(pos) || layout.inTunnel(pos, Integer.MIN_VALUE, LONGEST_TUNNEL);
    }

    /**
     * The next work, top-down: the highest shaft layer with something the pickaxe can dig, or, once a platform is open,
     * its tunnels (each tunnel line up to the first block that can't be dug). Nothing once the mine is done.
     */
    List<BlockPos> nextWork(ServerLevel level, int bottom, ItemStack pickaxe, int tunnelLength, int deepestPlatform) {
        for (int y = top; y >= Math.max(Math.max(bottom, deepestPlatform), level.getMinY() + 1); y--) {
            List<BlockPos> layer = new ArrayList<>();
            for (BlockPos column : outline) {
                BlockPos pos = column.atY(y);
                if (layout.digs(pos) && diggable(level, pos, pickaxe)) layer.add(pos);
            }
            if (!layout.isPlatform(y)) {
                if (layout.isPlatform(y + 1) && y - 1 >= Math.max(bottom, level.getMinY() + 1)) {
                    for (BlockPos column : outline) {
                        BlockPos below = column.atY(y - 1);
                        if (layout.digs(below) && diggable(level, below, pickaxe)) layer.add(below);
                    }
                }
                if (!layer.isEmpty()) return layer;
                continue;
            }
            boolean lastPlatform = y == deepestPlatform;
            if (!lastPlatform && ladder != null && layer.contains(ladder.at(y))) return List.of(ladder.at(y));
            for (List<List<BlockPos>> direction : layout.tunnelsByDirection(y, tunnelLength)) {
                List<BlockPos> tunnel = new ArrayList<>();
                for (List<BlockPos> line : direction) {
                    for (BlockPos pos : line) {
                        if (open(level, pos)) continue;
                        if (!diggable(level, pos, pickaxe)) break;
                        tunnel.add(pos);
                    }
                }
                if (!tunnel.isEmpty()) return tunnel;
            }
            if (lastPlatform) return List.of();
            if (!layer.isEmpty()) return layer;
        }
        return List.of();
    }

    /**
     * The spots at feet level {@code feetY} the miner can stand on once it is there on its ladder: a flood from the
     * ladder column through every open, two-high cell of the mine with a floor under it (the ladder cell only where it has one).
     */
    Set<BlockPos> reachable(ServerLevel level, int feetY) {
        Set<BlockPos> spots = new HashSet<>();
        if (feetY == top + 1) return onRim(level);
        if (ladder == null) return spots;
        BlockPos start = ladder.at(feetY);
        if (!open(level, start) || !open(level, start.above())) return spots;
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start);
        if (floored(level, start)) spots.add(start);
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos next = pos.relative(side);
                if (!seen.add(next) || !contains(next) || !open(level, next) || !open(level, next.above()) || !floored(level, next)) continue;
                spots.add(next);
                queue.add(next);
            }
        }
        return spots;
    }

    /** Spots on the surface beside the outline, to dig the marked layer from before there is any mine to stand in. */
    private Set<BlockPos> onRim(ServerLevel level) {
        Set<BlockPos> spots = new HashSet<>();
        for (BlockPos column : outline) {
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos spot = column.relative(side).above();
                if (!outline.contains(column.relative(side)) && open(level, spot) && open(level, spot.above()) && floored(level, spot)) spots.add(spot);
            }
        }
        return spots;
    }

    /** Whether getting between these feet levels means passing a platform (from on top of it to below it), so on the ladder. */
    boolean platformBetween(int fromFeet, int toFeet) {
        for (int y = Math.min(fromFeet, toFeet) + 1; y < Math.max(fromFeet, toFeet); y++) {
            if (layout.isPlatform(y)) return true;
        }
        return false;
    }

    /** The highest open cell of the ladder column that has no ladder yet, from the marked layer down to where digging reached. */
    Optional<BlockPos> missingLadder(ServerLevel level) {
        if (ladder == null) return Optional.empty();
        for (int y = top; y > level.getMinY(); y--) {
            BlockPos pos = ladder.at(y);
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.LADDER)) continue;
            return state.isAir() ? Optional.of(pos) : Optional.empty();
        }
        return Optional.empty();
    }

    /**
     * Blocks to fill with cobblestone, near {@code around}: fluid in or beside the mine (beside an open cell, or beside a
     * cell still to dig, which can't be dug while it touches fluid), and openings in the walls, floor or ceiling of the
     * dug mine where it meets a cave. Never above the marked layer.
     */
    List<BlockPos> sealSpots(ServerLevel level, BlockPos around, int bottom, int tunnelLength) {
        List<BlockPos> spots = new ArrayList<>();
        int low = Math.max(Math.max(bottom, level.getMinY() + 1), around.getY() - SEAL_RANGE);
        int high = Math.min(top, around.getY() + SEAL_RANGE);
        for (BlockPos cell : BlockPos.betweenClosed(around.offset(-SEAL_RANGE, 0, -SEAL_RANGE).atY(low), around.offset(SEAL_RANGE, 0, SEAL_RANGE).atY(high))) {
            if (!contains(cell)) continue;
            BlockPos pos = cell.immutable();
            boolean cellOpen = open(level, pos);
            if (!level.getFluidState(pos).isEmpty()) {
                spots.add(pos);
                continue;
            }
            for (Direction side : Direction.values()) {
                BlockPos next = pos.relative(side);
                if (next.getY() > top || spots.contains(next)) continue;
                boolean fluid = !level.getFluidState(next).isEmpty();
                boolean caveOpening = cellOpen && !contains(next) && !fluid && level.getBlockState(next).canBeReplaced() && !level.getBlockState(next).is(BlockTags.CLIMBABLE);
                if (fluid || caveOpening) spots.add(next);
            }
        }
        return spots;
    }

    /** Spots one block above dark floor cells of the shaft and tunnels, where monsters could spawn, with an outer wall to put a torch on. */
    List<TorchSpot> darkSpots(ServerLevel level, int bottom, int light, int tunnelLength) {
        List<BlockPos> cells = new ArrayList<>();
        for (BlockPos column : outline) {
            for (int y = top; y >= Math.max(bottom, level.getMinY() + 1); y--) cells.add(column.atY(y));
        }
        for (int y = top - 1; y >= Math.max(bottom, level.getMinY() + 1); y--) {
            if (layout.isPlatform(y)) cells.addAll(layout.tunnels(y, tunnelLength));
        }
        List<TorchSpot> spots = new ArrayList<>();
        for (BlockPos floor : cells) {
            if (!level.getBlockState(floor).isAir()) continue;
            if (!level.getBlockState(floor.below()).isFaceSturdy(level, floor.below(), Direction.UP)) continue;
            if (level.getBrightness(LightLayer.BLOCK, floor) >= light || litNearby(level, floor, light)) continue;
            BlockPos pos = floor.above();
            if (!level.getBlockState(pos).isAir()) continue;
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos wall = pos.relative(side);
                if (outline.contains(wall.atY(top)) || layout.inTunnel(wall, bottom, tunnelLength)) continue;
                if (!level.getBlockState(wall).isFaceSturdy(level, wall, side.getOpposite())) continue;
                spots.add(new TorchSpot(pos, side.getOpposite()));
                break;
            }
        }
        return spots;
    }

    /**
     * Whether a light source nearby (a torch, lantern or glowstone, the player's or the miner's own) reaches this cell
     * with at least {@code light}: block light only updates a moment after a block changes, and reading it alone made
     * the miner put several torches side by side.
     */
    private static boolean litNearby(ServerLevel level, BlockPos cell, int light) {
        int range = 15 - light;
        for (BlockPos pos : BlockPos.betweenClosed(cell.offset(-range, -range, -range), cell.offset(range, range, range))) {
            int distance = Math.abs(pos.getX() - cell.getX()) + Math.abs(pos.getY() - cell.getY()) + Math.abs(pos.getZ() - cell.getZ());
            if (distance > range) continue;
            if (level.getBlockState(pos).getLightEmission(level, pos) - distance >= light) return true;
        }
        return false;
    }

    private static boolean floored(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    private static boolean open(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.is(BlockTags.CLIMBABLE) || (state.getFluidState().isEmpty() && state.getCollisionShape(level, pos).isEmpty());
    }

    static boolean diggable(ServerLevel level, BlockPos pos, ItemStack pickaxe) {
        BlockState state = level.getBlockState(pos);
        if (open(level, pos) || !state.getFluidState().isEmpty() || level.getBlockEntity(pos) != null) return false;
        if (state.getDestroySpeed(level, pos) < 0.0F) return false;
        if (state.requiresCorrectToolForDrops() && !pickaxe.isCorrectToolForDrops(state)) return false;
        for (Direction side : Direction.values()) {
            if (!level.getFluidState(pos.relative(side)).isEmpty()) return false;
        }
        return true;
    }
}
