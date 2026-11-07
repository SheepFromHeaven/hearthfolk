package com.sheepfromheaven.minenow.shepherd;

import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.sounds.SoundSource;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Shepherds open the fence gates on their path, like every villager opens doors, and close them once they're through
 * so the flock stays in. Their navigation plans through closed gates ({@link #canOpen}); other villagers' doesn't.
 */
public class OpenFenceGates extends Behavior<Villager> {
    private static final double REACH = 2.0;
    private static final double PASSED = 1.8;

    private final Set<BlockPos> opened = new HashSet<>();

    public OpenFenceGates() {
        super(ImmutableMap.of(), 1200);
    }

    public static boolean canOpen(Mob mob) {
        return mob instanceof Villager villager && !villager.isBaby() && villager.getVillagerData().profession().is(VillagerProfession.SHEPHERD);
    }

    public static boolean isClosedGate(BlockState state) {
        return state.getBlock() instanceof FenceGateBlock && !state.getValue(FenceGateBlock.OPEN);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager body) {
        return canOpen(body);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager body, long timestamp) {
        return canOpen(body) || !opened.isEmpty();
    }

    @Override
    protected void tick(ServerLevel level, Villager body, long timestamp) {
        body.getBrain().getMemory(MemoryModuleType.PATH).ifPresent(path -> openAhead(level, body, path));
        closeBehind(level, body);
    }

    @Override
    protected void stop(ServerLevel level, Villager body, long timestamp) {
        for (BlockPos gate : opened) close(level, body, gate);
        opened.clear();
    }

    private void openAhead(ServerLevel level, Villager body, Path path) {
        if (path.isDone()) return;
        for (int i = Math.max(0, path.getNextNodeIndex() - 1); i <= Math.min(path.getNodeCount() - 1, path.getNextNodeIndex() + 1); i++) {
            BlockPos pos = path.getNodePos(i);
            BlockState state = level.getBlockState(pos);
            if (!isClosedGate(state) || !pos.closerToCenterThan(body.position(), REACH)) continue;
            if (state.getValue(FenceGateBlock.FACING) == body.getDirection().getOpposite()) {
                state = state.setValue(FenceGateBlock.FACING, body.getDirection());
            }
            level.setBlock(pos, state.setValue(FenceGateBlock.OPEN, true), 10);
            level.playSound(null, pos, ((FenceGateBlock) state.getBlock()).openSound, SoundSource.BLOCKS, 1.0F, level.getRandom().nextFloat() * 0.1F + 0.9F);
            level.gameEvent(body, GameEvent.BLOCK_OPEN, pos);
            opened.add(pos.immutable());
        }
    }

    private void closeBehind(ServerLevel level, Villager body) {
        Iterator<BlockPos> gates = opened.iterator();
        while (gates.hasNext()) {
            BlockPos gate = gates.next();
            if (gate.closerToCenterThan(body.position(), PASSED) && level.getBlockState(gate).getBlock() instanceof FenceGateBlock) continue;
            close(level, body, gate);
            gates.remove();
        }
    }

    private static void close(ServerLevel level, Villager body, BlockPos gate) {
        BlockState state = level.getBlockState(gate);
        if (!(state.getBlock() instanceof FenceGateBlock gateBlock) || !state.getValue(FenceGateBlock.OPEN)) return;
        level.setBlock(gate, state.setValue(FenceGateBlock.OPEN, false), 10);
        level.playSound(null, gate, gateBlock.closeSound, SoundSource.BLOCKS, 1.0F, level.getRandom().nextFloat() * 0.1F + 0.9F);
        level.gameEvent(body, GameEvent.BLOCK_CLOSE, gate);
    }
}
