package com.sheepfromheaven.minenow.jobs.farmer;

import com.sheepfromheaven.minenow.structure.field.CropKind;
import com.sheepfromheaven.minenow.structure.field.Field;
import com.sheepfromheaven.minenow.structure.field.block.FieldMarkerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Scans a farmer's assigned fields for the higher-priority upkeep work that comes before harvesting:
 * first making sure ground is irrigated (placing a new water source if any tillable/farmland tile
 * isn't within range of one), then tilling irrigated bare ground into farmland, then planting bare
 * farmland with whichever crop the field's marker is templated for (see
 * {@link FieldMarkerBlockEntity#getAllowedCrops}). Harvesting itself is still {@link CropScanner}'s
 * job, since it only ever looks at crops that are already growing.
 */
public final class FieldWorkScanner {
    private FieldWorkScanner() {}

    private static final Set<Block> TILLABLE = Set.of(Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.DIRT_PATH, Blocks.COARSE_DIRT);
    /** Vegetation that has to be cleared off a tillable tile before it counts as ready to till -
     * vanilla's hoe refuses to till when the block above isn't air. */
    private static final Set<Block> VEGETATION = Set.of(Blocks.SHORT_GRASS, Blocks.TALL_GRASS, Blocks.FERN,
            Blocks.LARGE_FERN, Blocks.DEAD_BUSH, Blocks.SHORT_DRY_GRASS, Blocks.TALL_DRY_GRASS);
    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
    /** How far (chebyshev distance) a tillable/farmland tile may be from a water source and still count as irrigated. */
    private static final int WATER_RANGE = 4;

    public record PlantTarget(BlockPos pos, CropKind kind) {}

    /**
     * The best spot in any assigned field to place a new water source: a tile walled in by solid
     * ground on all 4 cardinal sides (so the water stays put as a still source instead of flowing
     * away) that isn't already water, chosen to bring the most currently-unirrigated tillable/farmland
     * tiles into range - not just the first tile found. Returns empty once every field's tillable and
     * farmland ground is already irrigated (or no valid walled-in spot exists to place one at all).
     */
    public static Optional<BlockPos> findIrrigationTarget(Level level, List<Field> fields, Set<BlockPos> excluded) {
        for (var field : fields) {
            var area = field.getArea();
            var waterPositions = area.stream().filter(p -> level.getBlockState(p).is(Blocks.WATER)).toList();
            var unirrigated = area.stream()
                    .filter(p -> isTillableOrBareFarmland(level, p))
                    .filter(p -> !isIrrigated(p, waterPositions))
                    .toList();
            if (unirrigated.isEmpty()) continue;

            BlockPos best = null;
            int bestScore = 0;
            for (var candidate : area) {
                if (excluded.contains(candidate)) continue;
                if (level.getBlockState(candidate).is(Blocks.WATER)) continue;
                if (!isTillableOrBareFarmland(level, candidate)) continue;
                if (!isWalledIn(level, candidate)) continue;
                int score = 0;
                for (var target : unirrigated) {
                    if (chebyshev(candidate, target) <= WATER_RANGE) score++;
                }
                if (score > bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
            if (best != null) {
                return Optional.of(best);
            }
        }
        return Optional.empty();
    }

    /** First irrigated, non-farmland ground tile in any assigned field that still needs tilling - a
     * tile with grass/fern/etc. still growing on top isn't offered until {@link #findVegetationClearTarget}
     * has it cleared, since vanilla's hoe refuses to till under standing vegetation. */
    public static Optional<BlockPos> findTillTarget(Level level, List<Field> fields, Set<BlockPos> excluded) {
        for (var field : fields) {
            var area = field.getArea();
            var waterPositions = area.stream().filter(p -> level.getBlockState(p).is(Blocks.WATER)).toList();
            for (var pos : area) {
                if (excluded.contains(pos)) continue;
                if (!TILLABLE.contains(level.getBlockState(pos).getBlock())) continue;
                if (VEGETATION.contains(level.getBlockState(pos.above()).getBlock())) continue;
                if (!isIrrigated(pos, waterPositions)) continue;
                return Optional.of(pos);
            }
        }
        return Optional.empty();
    }

    /** First tillable ground tile in any assigned field that still has grass/fern/etc. growing on top
     * of it - takes priority over {@link #findTillTarget} since that vegetation has to be broken
     * first. */
    public static Optional<BlockPos> findVegetationClearTarget(Level level, List<Field> fields, Set<BlockPos> excluded) {
        for (var field : fields) {
            for (var pos : field.getArea()) {
                if (excluded.contains(pos)) continue;
                if (!TILLABLE.contains(level.getBlockState(pos).getBlock())) continue;
                if (!VEGETATION.contains(level.getBlockState(pos.above()).getBlock())) continue;
                return Optional.of(pos);
            }
        }
        return Optional.empty();
    }

    /** First bare farmland tile (no crop growing) in a field, paired with a crop kind its marker
     * allows whose seed hasn't already turned up missing from storage this trip (see
     * {@code FarmerJob#tickFetchSeed}). */
    public static Optional<PlantTarget> findPlantTarget(Level level, List<Field> fields, Set<BlockPos> excluded,
            Set<CropKind> unavailableSeeds) {
        for (var field : fields) {
            if (!(level.getBlockEntity(field.getMarkerPos()) instanceof FieldMarkerBlockEntity be)) continue;
            var allowedCrops = be.getAllowedCrops();
            if (allowedCrops.isEmpty()) continue;
            for (var pos : field.getArea()) {
                if (!level.getBlockState(pos).is(Blocks.FARMLAND)) continue;
                var above = pos.above();
                if (excluded.contains(above)) continue;
                if (!level.getBlockState(above).isAir()) continue;
                for (var kind : allowedCrops) {
                    if (unavailableSeeds.contains(kind)) continue;
                    return Optional.of(new PlantTarget(above, kind));
                }
            }
        }
        return Optional.empty();
    }

    /** Whether {@code pos} is ground the farmer might place a water source on - untilled ground, or
     * farmland with nothing planted on it (never destroys a growing crop to make a well). */
    public static boolean isTillableOrBareFarmland(Level level, BlockPos pos) {
        var state = level.getBlockState(pos);
        if (TILLABLE.contains(state.getBlock())) return true;
        return state.is(Blocks.FARMLAND) && level.getBlockState(pos.above()).isAir();
    }

    /** Whether {@code block} is grass/fern/etc. that has to be cleared before the tile beneath it
     * counts as ready to till - see {@code FarmerJob#doClearVegetation}. */
    public static boolean isVegetation(Block block) {
        return VEGETATION.contains(block);
    }

    private static boolean isIrrigated(BlockPos pos, List<BlockPos> waterPositions) {
        return waterPositions.stream().anyMatch(w -> chebyshev(w, pos) <= WATER_RANGE);
    }

    private static int chebyshev(BlockPos a, BlockPos b) {
        return Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getZ() - b.getZ()));
    }

    /** True if all 4 cardinal neighbors are solid ground - a water source placed here stays a still
     * pool instead of flowing out sideways. */
    private static boolean isWalledIn(Level level, BlockPos pos) {
        for (var dir : HORIZONTAL) {
            var neighbor = pos.relative(dir);
            if (level.getBlockState(neighbor).getCollisionShape(level, neighbor).isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
