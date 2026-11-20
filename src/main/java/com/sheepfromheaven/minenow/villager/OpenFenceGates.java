package com.sheepfromheaven.minenow.villager;

import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.sounds.SoundSource;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Shepherds and butchers open the fence gates on their path, like every villager opens doors, and close them as soon
 * as they're clear of the gateway. An animal caught in the gateway is shooed back to the side away from the villager,
 * so a closing gate never lets it slip out. Their navigation plans through closed gates ({@link #canOpen}); other villagers' doesn't.
 */
public class OpenFenceGates extends Behavior<Villager> {
    private static final double REACH = 1.2;

    private final Set<BlockPos> opened = new HashSet<>();

    public OpenFenceGates() {
        super(ImmutableMap.of(), 1200);
    }

    public static boolean canOpen(Mob mob) {
        return mob instanceof Villager villager && !villager.isBaby()
                && com.sheepfromheaven.minenow.MineNowConfig.professionEnabled(villager.getVillagerData().profession())
                && (villager.getVillagerData().profession().is(VillagerProfession.SHEPHERD) || villager.getVillagerData().profession().is(VillagerProfession.BUTCHER));
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
            if (level.getBlockState(gate).getBlock() instanceof FenceGateBlock && (inGateway(body, gate) || headingThrough(body, gate))) continue;
            close(level, body, gate);
            gates.remove();
        }
    }

    private static void shooBack(ServerLevel level, Villager body, BlockPos gate, BlockState state) {
        Direction through = state.getValue(FenceGateBlock.FACING);
        double villagerSide = (body.getX() - gate.getX() - 0.5) * through.getStepX() + (body.getZ() - gate.getZ() - 0.5) * through.getStepZ();
        BlockPos away = gate.relative(villagerSide > 0 ? through.getOpposite() : through);
        for (Animal animal : level.getEntitiesOfClass(Animal.class, new AABB(gate))) {
            animal.snapTo(away.getX() + 0.5, away.getY(), away.getZ() + 0.5, animal.getYRot(), animal.getXRot());
            animal.getNavigation().stop();
        }
    }

    private static boolean inGateway(Villager body, BlockPos gate) {
        return body.getBoundingBox().intersects(new AABB(gate).inflate(0.2));
    }

    private static boolean headingThrough(Villager body, BlockPos gate) {
        return body.getBrain().getMemory(MemoryModuleType.PATH)
                .filter(path -> !path.isDone())
                .map(path -> {
                    for (int i = path.getNextNodeIndex(); i < Math.min(path.getNodeCount(), path.getNextNodeIndex() + 2); i++) {
                        if (path.getNodePos(i).equals(gate)) return true;
                    }
                    return false;
                })
                .orElse(false);
    }

    private static void close(ServerLevel level, Villager body, BlockPos gate) {
        BlockState state = level.getBlockState(gate);
        if (!(state.getBlock() instanceof FenceGateBlock gateBlock) || !state.getValue(FenceGateBlock.OPEN)) return;
        shooBack(level, body, gate, state);
        level.setBlock(gate, state.setValue(FenceGateBlock.OPEN, false), 10);
        level.playSound(null, gate, gateBlock.closeSound, SoundSource.BLOCKS, 1.0F, level.getRandom().nextFloat() * 0.1F + 0.9F);
        level.gameEvent(body, GameEvent.BLOCK_CLOSE, gate);
    }
}
