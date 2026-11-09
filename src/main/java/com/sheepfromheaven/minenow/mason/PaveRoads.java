package com.sheepfromheaven.minenow.mason;

import com.sheepfromheaven.minenow.villager.WorkXp;
import com.google.common.collect.ImmutableMap;
import com.sheepfromheaven.minenow.villager.Economy;
import com.sheepfromheaven.minenow.villager.Walking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * While a redstone signal reaches its stonecutter, a mason from apprentice on paves the paths the player shovelled
 * within {@link MasonSkill#roadRange} of it, scattered ones under open sky ({@link #surfacePaths}) as well as those
 * joined to the road network around its workshop, roofed or not ({@link #roads}): each dirt path becomes cobblestone or gravel, or from journeyman stone
 * bricks or slabs, as {@link RoadPattern} has it for that spot, and a journeyman lays stone on the cobblestone and gravel
 * it paved before ({@link Mason#PAVED}). It only ever replaces dirt paths and its own paving, with blocks from its goods.
 */
public class PaveRoads extends Behavior<Villager> {
    private static final int HEIGHT = 4;
    private static final int START = 8;
    private static final int MAX_NETWORK = 8192;
    private static final int SURFACE_RESCAN = 600;
    private static final int LOOK_DOWN = 16;
    private static final TagKey<Block> TIER_2 = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("minenow", "villager_roads/tier_2"));
    private static final TagKey<Block> TIER_3 = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("minenow", "villager_roads/tier_3"));
    private static final double REACH = 3.0;
    private static final float SPEED = 0.5F;
    private static final int RESCAN = 60;
    private static final int GIVE_UP_AFTER_TICKS = 1200;
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
    private Set<BlockPos> surface = Set.of();
    private long surfaceAt = Long.MIN_VALUE;
    private @Nullable BlockPos surfaceSite;
    private int surfaceRange;
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
        WorkXp.award(body, WorkXp.PAVED);
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
        Set<BlockPos> paths = new HashSet<>(surfacePaths(level, site.get(), range, now));
        paths.addAll(roads(level, site.get(), range));
        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos pos : paths) {
            if (level.getBlockState(pos).is(Blocks.DIRT_PATH)) candidates.add(pos);
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

    /**
     * Every dirt path under open sky within {@code range} of its stonecutter, wherever it lies (scattered paths laid to
     * make the settlement look nice join no road): per column the first solid block from the top, looking past leaves
     * and the like ({@link #seeThrough}), in loaded chunks; remembered for
     * {@link #SURFACE_RESCAN} ticks, since a master's reach covers a lot of columns.
     */
    private Set<BlockPos> surfacePaths(ServerLevel level, BlockPos site, int range, long now) {
        if (surfaceAt != Long.MIN_VALUE && now - surfaceAt < SURFACE_RESCAN && site.equals(surfaceSite) && range == surfaceRange) return surface;
        Set<BlockPos> found = new HashSet<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = site.getX() - range; x <= site.getX() + range; x++) {
            for (int z = site.getZ() - range; z <= site.getZ() + range; z++) {
                if (!level.hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z))) continue;
                pos.set(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, z);
                for (int step = 0; step < LOOK_DOWN && seeThrough(level, pos); step++) pos.move(0, -1, 0);
                if (level.getBlockState(pos).is(Blocks.DIRT_PATH)) found.add(pos.immutable());
            }
        }
        surface = found;
        surfaceAt = now;
        surfaceSite = site;
        surfaceRange = range;
        return found;
    }

    /** What the sky reaches the ground through: air, leaves, barriers and anything without collision, like grass and flowers. */
    private static boolean seeThrough(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.is(BlockTags.LEAVES) || state.is(Blocks.BARRIER) || state.getCollisionShape(level, pos).isEmpty();
    }

    /**
     * The road network around its workshop: road blocks (any tier, so paved stretches still join it) near its
     * stonecutter and everything joined to them, a step up or down allowed, within {@code range} of it and only in
     * loaded chunks. Following the roads rather than scanning the whole area keeps a long reach cheap.
     */
    private static Set<BlockPos> roads(ServerLevel level, BlockPos site, int range) {
        Set<BlockPos> found = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (BlockPos pos : BlockPos.betweenClosed(site.offset(-START, -HEIGHT, -START), site.offset(START, HEIGHT, START))) {
            if (isRoad(level, pos) && found.add(pos.immutable())) queue.add(pos.immutable());
        }
        while (!queue.isEmpty() && found.size() < MAX_NETWORK) {
            BlockPos pos = queue.poll();
            for (Direction side : Direction.Plane.HORIZONTAL) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos next = pos.relative(side).above(dy);
                    if (Math.abs(next.getX() - site.getX()) > range || Math.abs(next.getZ() - site.getZ()) > range || Math.abs(next.getY() - site.getY()) > range) continue;
                    if (found.contains(next) || !level.isLoaded(next) || !isRoad(level, next)) continue;
                    found.add(next);
                    queue.add(next);
                }
            }
        }
        return found;
    }

    private static boolean isRoad(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(Blocks.DIRT_PATH) || state.is(TIER_2) || state.is(TIER_3);
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
