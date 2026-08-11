package com.sheepfromheaven.minenow.jobs.farmer;

import com.sheepfromheaven.minenow.structure.field.CropKind;
import com.sheepfromheaven.minenow.structure.field.Field;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Finds a mature, plantable-here crop for a farmer to harvest. Prefers the farm's assigned
 * {@link Field}s (each with its own crop allowance list - see {@link CropKind}), falling back to a
 * plain spherical work-area scan (mirroring {@link com.sheepfromheaven.minenow.jobs.lumberjack.TreeScanner}'s
 * tree search) for farms with no fields set up yet.
 */
public final class CropScanner {
    private CropScanner() {}

    /** Scans every assigned field's claimed farmland for a mature crop this field allows. */
    public static Optional<BlockPos> findMatureCropInFields(Level level, List<Field> fields, Set<BlockPos> excluded) {
        for (var field : fields) {
            for (var farmlandPos : field.getArea()) {
                var cropPos = farmlandPos.above();
                if (excluded.contains(cropPos)) continue;
                var state = level.getBlockState(cropPos);
                var kind = CropKind.fromState(state).orElse(null);
                if (kind != null && field.allows(kind) && kind.isMature(state)) {
                    return Optional.of(cropPos);
                }
            }
        }
        return Optional.empty();
    }

    @Nullable
    public static Item seedItemFor(net.minecraft.world.level.block.state.BlockState state) {
        return CropKind.fromState(state).map(CropKind::getSeedItem).orElse(null);
    }
}
