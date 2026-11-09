package com.sheepfromheaven.minenow.mason;

import com.google.common.collect.ImmutableMap;
import com.sheepfromheaven.minenow.villager.Economy;
import com.sheepfromheaven.minenow.villager.Walking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * While a redstone signal reaches its stonecutter, a mason from apprentice on paves the roads the player shovelled
 * within {@link MasonSkill#roadRange} of it: each dirt path becomes cobblestone or gravel, or from journeyman stone
 * bricks or slabs, as {@link RoadPattern} has it for that spot, and a journeyman lays stone on the cobblestone and gravel
 * it paved before ({@link Mason#PAVED}). It only ever replaces dirt paths and its own paving, with blocks from its goods.
 */
public class PaveRoads extends Behavior<Villager> {
    private static final int HEIGHT = 4;
    private static final double REACH = 3.0;
    private static final float SPEED = 0.5F;
    private static final int RESCAN = 60;
    private static final int GIVE_UP_AFTER_TICKS = 300;
    private static final int UNREACHABLE_COOLDOWN = 1200;
    private static final int MAX_REMEMBERED = 1024;
    private static final Map<RoadPattern.Material, Item> ITEMS = Map.of(
            RoadPattern.Material.COBBLESTONE, Items.COBBLESTONE,
            RoadPattern.Material.GRAVEL, Items.GRAVEL,
            RoadPattern.Material.STONE_BRICKS, Items.STONE_BRICKS,
            RoadPattern.Material.STONE_BRICK_SLAB, Items.STONE_BRICK_SLAB);
    private static final Map<RoadPattern.Material, Block> BLOCKS = Map.of(
            RoadPattern.Material.COBBLESTONE, Blocks.COBBLESTONE,
            RoadPattern.Material.GRAVEL, Blocks.GRAVEL,
            RoadPattern.Material.STONE_BRICKS, Blocks.STONE_BRICKS,
            RoadPattern.Material.STONE_BRICK_SLAB, Blocks.STONE_BRICK_SLAB);

    private final Map<BlockPos, Long> unreachable = new HashMap<>();
    private @Nullable BlockPos target;
    private RoadPattern.@Nullable Material material;
    private long since;
    private long nextStartAllowed;

    public PaveRoads() {
        super(ImmutableMap.of(
                MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT
        ), GIVE_UP_AFTER_TICKS + 20);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager body) {
        if (level.getGameTime() < nextStartAllowed || body.isTrading()) return false;
        if (!body.getVillagerData().profession().is(VillagerProfession.MASON) || !body.getBrain().isActive(Activity.WORK)) return false;
        nextStartAllowed = level.getGameTime() + RESCAN;
        return pickTarget(level, body);
    }

    @Override
    protected void start(ServerLevel level, Villager body, long timestamp) {
        since = timestamp;
        walk(body);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager body, long timestamp) {
        return target != null && !body.isTrading() && body.getBrain().isActive(Activity.WORK) && timestamp - since < GIVE_UP_AFTER_TICKS;
    }

    @Override
    protected void tick(ServerLevel level, Villager body, long timestamp) {
        if (target == null || material == null) return;
        if (body.getEyePosition().distanceTo(Vec3.atCenterOf(target)) > REACH) {
            if (!Walking.towards(body, target.above())) walk(body);
            return;
        }
        lay(level, body, target, material);
        if (pickTarget(level, body)) {
            since = timestamp;
            walk(body);
        } else {
            target = null;
        }
    }

    @Override
    protected void stop(ServerLevel level, Villager body, long timestamp) {
        if (target != null && timestamp - since >= GIVE_UP_AFTER_TICKS) unreachable.put(target, timestamp + UNREACHABLE_COOLDOWN);
        target = null;
        material = null;
        body.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        body.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
    }

    private void lay(ServerLevel level, Villager body, BlockPos pos, RoadPattern.Material what) {
        if (!wanted(level, body, pos) || Economy.take(level, body, ITEMS.get(what), 1) < 1) return;
        BlockState state = BLOCKS.get(what).defaultBlockState();
        level.setBlock(pos, state, 3);
        level.playSound(null, pos, state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 0.9F);
        body.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
        List<BlockPos> paved = new ArrayList<>(body.getData(Mason.PAVED));
        paved.remove(pos);
        if (what == RoadPattern.Material.COBBLESTONE || what == RoadPattern.Material.GRAVEL) paved.add(pos);
        while (paved.size() > MAX_REMEMBERED) paved.removeFirst();
        body.setData(Mason.PAVED, List.copyOf(paved));
    }

    private boolean pickTarget(ServerLevel level, Villager body) {
        target = null;
        material = null;
        Optional<BlockPos> site = body.getBrain().getMemory(MemoryModuleType.JOB_SITE).filter(pos -> pos.dimension() == level.dimension()).map(GlobalPos::pos);
        int range = MasonSkill.roadRange(body.getVillagerData().level());
        if (site.isEmpty() || range == 0 || !level.hasNeighborSignal(site.get())) return false;
        long now = level.getGameTime();
        unreachable.values().removeIf(until -> until < now);
        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(site.get().offset(-range, -HEIGHT, -range), site.get().offset(range, HEIGHT, range))) {
            if (level.getBlockState(pos).is(Blocks.DIRT_PATH)) candidates.add(pos.immutable());
        }
        if (MasonSkill.laysStone(body.getVillagerData().level())) candidates.addAll(body.getData(Mason.PAVED));
        for (BlockPos pos : candidates.stream().sorted(Comparator.comparingDouble(pos -> pos.distToCenterSqr(body.position()))).toList()) {
            if (unreachable.containsKey(pos) || !wanted(level, body, pos)) continue;
            RoadPattern.Material what = materialFor(level, body, pos);
            if (what == null) continue;
            target = pos;
            material = what;
            return true;
        }
        return false;
    }

    /** A dirt path with room to walk on it, or cobblestone or gravel it laid itself and would now lay stone on. */
    private static boolean wanted(ServerLevel level, Villager body, BlockPos pos) {
        if (!level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()) return false;
        BlockState state = level.getBlockState(pos);
        if (state.is(Blocks.DIRT_PATH)) return true;
        return body.getData(Mason.PAVED).contains(pos) && (state.is(Blocks.COBBLESTONE) || state.is(Blocks.GRAVEL));
    }

    /** What this spot gets: its pattern material, or another of the same kind it has; null if it has none, or the spot's done. */
    private static RoadPattern.@Nullable Material materialFor(ServerLevel level, Villager body, BlockPos pos) {
        boolean stone = MasonSkill.laysStone(body.getVillagerData().level());
        RoadPattern.Material wanted = RoadPattern.material(Mth.getSeed(pos), stone);
        boolean rough = wanted == RoadPattern.Material.COBBLESTONE || wanted == RoadPattern.Material.GRAVEL;
        if (rough && !level.getBlockState(pos).is(Blocks.DIRT_PATH)) return null;
        List<RoadPattern.Material> choices = rough
                ? List.of(wanted, wanted == RoadPattern.Material.COBBLESTONE ? RoadPattern.Material.GRAVEL : RoadPattern.Material.COBBLESTONE)
                : List.of(wanted, wanted == RoadPattern.Material.STONE_BRICKS ? RoadPattern.Material.STONE_BRICK_SLAB : RoadPattern.Material.STONE_BRICKS);
        return choices.stream().filter(choice -> Economy.count(level, body, ITEMS.get(choice)) > 0).findFirst().orElse(null);
    }

    private void walk(Villager body) {
        if (target == null) return;
        body.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(target.above(), SPEED, 1));
        body.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(target));
    }
}
