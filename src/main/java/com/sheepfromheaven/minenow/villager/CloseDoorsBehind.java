package com.sheepfromheaven.minenow.villager;

import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

/**
 * Vanilla's {@code InteractWithDoor} only closes a door while the villager's path is still running, and only remembers
 * the doors it opened itself. So a villager that walks through a door and then reaches its goal (its bed, say) leaves it
 * open for the night, and one that walks through a door the player left open never closes it. This remembers every
 * wooden door the villager brushes past in {@code DOORS_TO_CLOSE} and closes each once the villager is more than 2 blocks
 * from it (or asleep) and nothing stands in the doorway. Double Doors, when installed, is told so the other half of a
 * double door closes with it, as it does for vanilla's own closing.
 */
public class CloseDoorsBehind extends Behavior<Villager> {
    private static final double LEFT_BEHIND = 2.0;
    private static final double BRUSHING = 0.6;
    private static final Method DOUBLE_DOORS = doubleDoors();

    public CloseDoorsBehind() {
        super(ImmutableMap.of(MemoryModuleType.DOORS_TO_CLOSE, MemoryStatus.REGISTERED));
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager body) {
        return (level.getGameTime() + body.getId()) % 4 == 0;
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager body, long timestamp) {
        return false;
    }

    @Override
    protected void start(ServerLevel level, Villager body, long timestamp) {
        Set<GlobalPos> doors = new HashSet<>(body.getBrain().getMemory(MemoryModuleType.DOORS_TO_CLOSE).orElse(Set.of()));
        boolean changed = remember(level, body, doors);
        Set<GlobalPos> remaining = new HashSet<>(doors);
        for (GlobalPos door : doors) {
            if (door.dimension() != level.dimension()) continue;
            BlockPos pos = door.pos();
            if (!level.isLoaded(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof DoorBlock doorBlock) || !state.is(BlockTags.WOODEN_DOORS)) {
                remaining.remove(door);
                changed = true;
                continue;
            }
            if (!body.isSleeping() && pos.closerToCenterThan(body.position(), LEFT_BEHIND)) continue;
            if (!level.getEntitiesOfClass(LivingEntity.class, new AABB(pos).inflate(0.1, 0, 0.1).expandTowards(0, 1, 0)).isEmpty()) continue;
            if (state.getValue(DoorBlock.OPEN)) {
                doorBlock.setOpen(body, level, state, pos, false);
                closeOtherHalf(level, pos, state);
            }
            remaining.remove(door);
            changed = true;
        }
        if (!changed) return;
        if (remaining.isEmpty()) body.getBrain().eraseMemory(MemoryModuleType.DOORS_TO_CLOSE);
        else body.getBrain().setMemory(MemoryModuleType.DOORS_TO_CLOSE, remaining);
    }

    private static boolean remember(ServerLevel level, Villager body, Set<GlobalPos> doors) {
        boolean added = false;
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(body.getBoundingBox().inflate(BRUSHING).getMinPosition()), BlockPos.containing(body.getBoundingBox().inflate(BRUSHING).getMaxPosition()))) {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof DoorBlock) || !state.is(BlockTags.WOODEN_DOORS) || !state.getValue(DoorBlock.OPEN)) continue;
            BlockPos lower = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos.immutable();
            added |= doors.add(GlobalPos.of(level.dimension(), lower));
        }
        return added;
    }

    private static void closeOtherHalf(Level level, BlockPos pos, BlockState opened) {
        if (DOUBLE_DOORS == null) return;
        try {
            DOUBLE_DOORS.invoke(null, null, level, pos, opened, Boolean.FALSE);
        } catch (ReflectiveOperationException | RuntimeException e) {
            // Double Doors changed under us: the door itself is closed, only its twin may stay open
        }
    }

    private static Method doubleDoors() {
        try {
            return Class.forName("com.serilum.doubledoors_common_neoforge.util.Util").getMethod("processDoor",
                    net.minecraft.world.entity.player.Player.class, Level.class, BlockPos.class, BlockState.class, Boolean.class);
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }
}
