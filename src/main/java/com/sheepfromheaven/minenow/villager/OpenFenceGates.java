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
import java.util.Optional;
import java.util.Set;

/**
 * Shepherds and butchers open the fence gates on their path, like every villager opens doors, and close them as soon
 * as they're clear of the gateway. An animal in the gateway of a gate they hold open, or caught there when it closes, is
 * shooed back to the pen's side ({@link #keepAnimalsIn}), whichever way the villager is going, so it never slips out. Their navigation plans through closed gates ({@link #canOpen}); other villagers' doesn't.
 */
public class OpenFenceGates extends Behavior<Villager> {
    private static final double REACH = 1.2;
    private static final long PEN_SIDE_REMEMBERED = 20;
    private static final java.util.Map<ServerLevel, java.util.Map<BlockPos, PenSide>> PEN_SIDES = new java.util.WeakHashMap<>();

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
        for (BlockPos gate : opened) keepAnimalsIn(level, gate);
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

    /**
     * An animal in the gateway of an open gate goes back to the pen's side: the one of the two cells the gate leads
     * between from which an animal is enclosed ({@link Pens#isEnclosed}). With both or neither enclosed, it stays where it is.
     */
    public static void keepAnimalsIn(ServerLevel level, BlockPos gate) {
        BlockState state = level.getBlockState(gate);
        if (!(state.getBlock() instanceof FenceGateBlock)) return;
        penSide(level, gate, state).ifPresent(inside -> shoo(level, gate, inside));
    }

    /** Before a gate closes, an animal in its gateway goes to the pen's side, or failing that to the side it is on, so the closing gate never puts it out. */
    private static void shooBack(ServerLevel level, Villager body, BlockPos gate, BlockState state) {
        Direction through = state.getValue(FenceGateBlock.FACING);
        Optional<BlockPos> pen = penSide(level, gate, state);
        for (Animal animal : level.getEntitiesOfClass(Animal.class, new AABB(gate))) {
            BlockPos inside = pen.orElseGet(() -> {
                double animalSide = offset(animal.getX(), animal.getZ(), gate, through);
                double side = Math.abs(animalSide) > 0.05 ? animalSide : -offset(body.getX(), body.getZ(), gate, through);
                return gate.relative(side > 0 ? through : through.getOpposite());
            });
            snap(animal, inside);
        }
    }

    /** The pen's side of a gate: of the two cells it leads between, the one in the smaller enclosure ({@link Pens#reach}); none when they're alike. */
    private static Optional<BlockPos> penSide(ServerLevel level, BlockPos gate, BlockState state) {
        java.util.Map<BlockPos, PenSide> sides = PEN_SIDES.computeIfAbsent(level, key -> new java.util.HashMap<>());
        PenSide known = sides.get(gate);
        if (known != null && known.until() > level.getGameTime()) return known.inside();
        Direction through = state.getValue(FenceGateBlock.FACING);
        BlockPos ahead = gate.relative(through);
        BlockPos behind = gate.relative(through.getOpposite());
        int aheadCells = Pens.reach(level, ahead);
        int behindCells = Pens.reach(level, behind);
        Optional<BlockPos> inside = aheadCells == behindCells ? Optional.empty() : Optional.of(aheadCells < behindCells ? ahead : behind);
        if (sides.size() > 256) sides.clear();
        sides.put(gate.immutable(), new PenSide(inside, level.getGameTime() + PEN_SIDE_REMEMBERED));
        return inside;
    }

    private record PenSide(Optional<BlockPos> inside, long until) {}

    private static void shoo(ServerLevel level, BlockPos gate, BlockPos inside) {
        for (Animal animal : level.getEntitiesOfClass(Animal.class, new AABB(gate))) snap(animal, inside);
    }

    private static void snap(Animal animal, BlockPos to) {
        animal.snapTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5, animal.getYRot(), animal.getXRot());
        animal.getNavigation().stop();
    }

    private static double offset(double x, double z, BlockPos gate, Direction through) {
        return (x - gate.getX() - 0.5) * through.getStepX() + (z - gate.getZ() - 0.5) * through.getStepZ();
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

    public static void close(ServerLevel level, Villager body, BlockPos gate) {
        BlockState state = level.getBlockState(gate);
        if (!(state.getBlock() instanceof FenceGateBlock gateBlock) || !state.getValue(FenceGateBlock.OPEN)) return;
        shooBack(level, body, gate, state);
        level.setBlock(gate, state.setValue(FenceGateBlock.OPEN, false), 10);
        level.playSound(null, gate, gateBlock.closeSound, SoundSource.BLOCKS, 1.0F, level.getRandom().nextFloat() * 0.1F + 0.9F);
        level.gameEvent(body, GameEvent.BLOCK_CLOSE, gate);
    }
}
