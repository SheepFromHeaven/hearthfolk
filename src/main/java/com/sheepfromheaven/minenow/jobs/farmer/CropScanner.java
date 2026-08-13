package com.sheepfromheaven.minenow.jobs.farmer;

import com.sheepfromheaven.minenow.structure.field.CropKind;
import com.sheepfromheaven.minenow.structure.field.Field;
import com.sheepfromheaven.minenow.structure.field.block.FieldMarkerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Finds a mature crop for a farmer to harvest within its assigned {@link Field}s - see
 * {@link FieldWorkScanner} for the higher-priority tilling/watering/planting upkeep that runs first.
 */
public final class CropScanner {
    private CropScanner() {}

    /**
     * Scans every assigned field's claimed farmland for a mature crop - any vanilla crop already
     * growing there is fair game to harvest, regardless of whether the field's seed template still
     * allows planting that kind (see {@link FieldMarkerBlockEntity#getAllowedCrops}).
     */
    public static Optional<BlockPos> findMatureCropInFields(Level level, List<Field> fields, Set<BlockPos> excluded) {
        for (var field : fields) {
            for (var farmlandPos : field.getArea()) {
                var cropPos = farmlandPos.above();
                if (excluded.contains(cropPos)) continue;
                var state = level.getBlockState(cropPos);
                var kind = CropKind.fromState(state).orElse(null);
                if (kind != null && kind.isMature(state)) {
                    return Optional.of(cropPos);
                }
            }
        }
        return Optional.empty();
    }
}
