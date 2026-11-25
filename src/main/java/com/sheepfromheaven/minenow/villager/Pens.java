package com.sheepfromheaven.minenow.villager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Whether an animal stands in a pen: the cells it can walk to (a step up or down of a block, a drop of up to three, never
 * through water, fences, walls, doors or fence gates, open or shut, since a villager closes them behind it) are fewer than
 * {@link #MAX_CELLS}. An animal running free in the open reaches more than that. The answer is remembered for
 * {@link #REMEMBER} ticks for every cell the search visited, since they all share it.
 */
public final class Pens {
    static final int MAX_CELLS = 1024;
    static final long REMEMBER = 100;
    private static final int MAX_DROP = 3;
    private static final int MAX_REMEMBERED = 50_000;
    private static final Map<ServerLevel, Map<Long, Long>> REMEMBERED = new WeakHashMap<>();

    private Pens() {}

    public static boolean isPenned(ServerLevel level, Entity animal) {
        BlockPos start = animal.blockPosition();
        Map<Long, Long> known = REMEMBERED.computeIfAbsent(level, key -> new HashMap<>());
        Long entry = known.get(start.asLong());
        if (entry != null && (entry >> 1) > level.getGameTime()) return (entry & 1) == 1;

        Set<Long> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seen.add(start.asLong());
        queue.add(start);
        boolean penned = true;
        while (!queue.isEmpty()) {
            if (seen.size() > MAX_CELLS) {
                penned = false;
                break;
            }
            BlockPos from = queue.poll();
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos next = step(level, from, direction);
                if (next != null && seen.add(next.asLong())) queue.add(next);
            }
        }
        if (known.size() > MAX_REMEMBERED) known.clear();
        long value = ((level.getGameTime() + REMEMBER) << 1) | (penned ? 1 : 0);
        for (long cell : seen) known.put(cell, value);
        return penned;
    }

    public static <T extends Entity> List<T> penned(ServerLevel level, List<T> animals) {
        List<T> result = new ArrayList<>();
        for (T animal : animals) if (isPenned(level, animal)) result.add(animal);
        return result;
    }

    private static BlockPos step(ServerLevel level, BlockPos from, Direction direction) {
        BlockPos beside = from.relative(direction);
        for (int dy = 1; dy >= -MAX_DROP; dy--) {
            BlockPos stand = beside.above(dy);
            if (!clear(level, stand) || !clear(level, stand.above())) continue;
            if (dy == 1 && !clear(level, from.above(2))) continue;
            BlockPos floor = stand.below();
            BlockState floorState = level.getBlockState(floor);
            if (blocksAnimals(floorState)) return null;
            VoxelShape shape = floorState.getCollisionShape(level, floor);
            if (shape.isEmpty() || shape.max(net.minecraft.core.Direction.Axis.Y) > 1.0) continue;
            return stand;
        }
        return null;
    }

    private static boolean clear(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !blocksAnimals(state) && state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
    }

    private static boolean blocksAnimals(BlockState state) {
        return state.getBlock() instanceof FenceGateBlock || state.getBlock() instanceof DoorBlock;
    }
}
