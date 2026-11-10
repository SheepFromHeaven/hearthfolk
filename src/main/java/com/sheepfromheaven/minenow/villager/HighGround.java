package com.sheepfromheaven.minenow.villager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Where an archer shoots from in an alarm. A post the player marked, with a block of {@link #POSTS} (hay bales) as its
 * floor, within {@link #RANGE} blocks of the bell and no lower than it, always comes first. Failing that, a spot at least
 * {@link #MIN_RISE} blocks above the bell, indoors or out, so roofed towers count. Either needs a solid floor (never
 * leaves) with two free cells above it, and a way for the villager to walk there, since it takes stairs and ramps but
 * never ladders. Higher and nearer the bell is better, and spots taken by other archers are left to them.
 * Only the best unmarked spot of each 3x3x3 patch is tried, so a few roofs it can't reach don't crowd out the wall it can,
 * and a floor under a roof is still a candidate.
 */
final class HighGround {
    static final TagKey<Block> POSTS = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("minenow", "archer_posts"));
    static final int RANGE = 12;
    static final int MIN_RISE = 3;
    static final int MAX_RISE = 12;
    static final int TRIES = 16;
    static final double SPACING = 2.5;
    /** Paths are planned no further than a villager's 16-block follow range, so a spot further off is checked once it's this close. */
    static final double CHECK_PATH_WITHIN = 14.0;
    private static final double MARKED_BONUS = 100.0;

    private HighGround() {}

    static Optional<BlockPos> find(ServerLevel level, Villager body, BlockPos center, Collection<BlockPos> taken) {
        BlockPos.MutableBlockPos feet = new BlockPos.MutableBlockPos();
        List<BlockPos> marked = new ArrayList<>();
        Map<Long, BlockPos> best = new HashMap<>();
        for (int dx = -RANGE; dx <= RANGE; dx++) {
            for (int dz = -RANGE; dz <= RANGE; dz++) {
                if (dx * dx + dz * dz > RANGE * RANGE) continue;
                for (int y = center.getY() + MAX_RISE; y >= center.getY(); y--) {
                    feet.set(center.getX() + dx, y, center.getZ() + dz);
                    boolean post = level.getBlockState(feet.below()).is(POSTS);
                    if (!post && y < center.getY() + MIN_RISE) continue;
                    if (!standable(level, feet)) continue;
                    BlockPos spot = feet.immutable();
                    if (taken.stream().anyMatch(other -> other.distSqr(spot) < SPACING * SPACING)) continue;
                    if (post) {
                        marked.add(spot);
                    } else {
                        best.merge(BlockPos.asLong(Math.floorDiv(spot.getX(), 3), Math.floorDiv(spot.getY(), 3), Math.floorDiv(spot.getZ(), 3)), spot,
                                (a, b) -> score(a, center, body) >= score(b, center, body) ? a : b);
                    }
                }
            }
        }
        List<BlockPos> candidates = new ArrayList<>(marked);
        candidates.addAll(best.values());
        return candidates.stream()
                .sorted(Comparator.comparingDouble((BlockPos spot) -> -(score(spot, center, body) + (marked.contains(spot) ? MARKED_BONUS : 0))))
                .limit(TRIES)
                .filter(spot -> !body.position().closerThan(net.minecraft.world.phys.Vec3.atBottomCenterOf(spot), CHECK_PATH_WITHIN) || reachable(body, spot))
                .findFirst();
    }

    /** Height above the bell counts double, up to 8 blocks; a long way from it or from the villager counts against. */
    private static double score(BlockPos spot, BlockPos center, Villager body) {
        double rise = Math.min(spot.getY() - center.getY(), 8);
        return 2 * rise - 0.3 * Math.sqrt(spot.distSqr(center)) - 0.15 * Math.sqrt(spot.distSqr(body.blockPosition()));
    }

    /** The standing cells on the posts marked anywhere in the village, from the {@link ModVillagers#ARCHER_POST} index. */
    static List<BlockPos> markedPosts(ServerLevel level, Villages.Village village) {
        List<BlockPos> anchors = village.anchors();
        if (anchors.isEmpty()) return List.of();
        int x = (int) anchors.stream().mapToInt(BlockPos::getX).average().orElse(0);
        int y = (int) anchors.stream().mapToInt(BlockPos::getY).average().orElse(0);
        int z = (int) anchors.stream().mapToInt(BlockPos::getZ).average().orElse(0);
        BlockPos centre = new BlockPos(x, y, z);
        int radius = (int) Math.ceil(anchors.stream().mapToDouble(anchor -> Math.sqrt(anchor.distSqr(centre))).max().orElse(0)) + Villages.REACH + 1;
        return level.getPoiManager().getInRange(type -> type.is(ModVillagers.ARCHER_POST.getKey()), centre, radius, net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY)
                .map(record -> record.getPos())
                .filter(post -> village.contains(post) && level.getBlockState(post).is(POSTS))
                .map(BlockPos::above)
                .filter(feet -> standable(level, feet))
                .toList();
    }

    /** A cell to stand in at this column, within 3 blocks up or down of {@code y}. */
    static Optional<BlockPos> ground(ServerLevel level, int x, int z, int y) {
        for (int dy = 3; dy >= -3; dy--) {
            BlockPos feet = new BlockPos(x, y + dy, z);
            if (standable(level, feet)) return Optional.of(feet);
        }
        return Optional.empty();
    }

    static boolean standable(ServerLevel level, BlockPos feet) {
        BlockPos floor = feet.below();
        BlockState ground = level.getBlockState(floor);
        return level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir()
                && ground.isFaceSturdy(level, floor, Direction.UP) && !ground.is(BlockTags.LEAVES) && !ground.is(Blocks.BARRIER) && !ground.is(Blocks.MAGMA_BLOCK) && !ground.is(Blocks.CACTUS);
    }

    /**
     * A path there, or to beside it at most a block lower: the pathfinder often stops beside a lone block such as a
     * hay bale post rather than plan the hop up, which the villager makes on arrival.
     */
    static boolean reachable(Villager body, BlockPos spot) {
        Path path = body.getNavigation().createPath(spot, 0);
        if (path == null) return false;
        if (path.canReach()) return true;
        var end = path.getEndNode();
        return end != null && end.y >= spot.getY() - 1 && end.y <= spot.getY() && Math.abs(end.x - spot.getX()) <= 1 && Math.abs(end.z - spot.getZ()) <= 1;
    }
}
