package com.sheepfromheaven.minenow.structure.field;

import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * A player-claimed patch of farmland (placed via a field marker, its footprint auto-detected the
 * same way a {@link com.sheepfromheaven.minenow.structure.block.StructureMarkerBlock} detects an
 * enclosed room - see {@link FieldAreaDetector}). Each field carries its own allowance list of
 * crop types a farmer may plant here, and is assigned to at most one FARM structure at a time; a
 * single farm can have any number of fields assigned to it.
 */
public class Field {
    private final UUID id;
    private final BlockPos markerPos;
    private Set<BlockPos> area;
    private final EnumSet<CropKind> allowedCrops;
    /** Which crop the field marker's right-click toggle is currently pointed at - see the marker block. */
    private int highlightedCropIndex;
    @Nullable
    private UUID assignedStructureId;

    public Field(BlockPos markerPos, Set<BlockPos> area) {
        this(UUID.randomUUID(), markerPos, area, EnumSet.allOf(CropKind.class), null);
    }

    public Field(UUID id, BlockPos markerPos, Set<BlockPos> area, Set<CropKind> allowedCrops, @Nullable UUID assignedStructureId) {
        this.id = id;
        this.markerPos = markerPos;
        this.area = area;
        this.allowedCrops = allowedCrops.isEmpty() ? EnumSet.noneOf(CropKind.class) : EnumSet.copyOf(allowedCrops);
        this.assignedStructureId = assignedStructureId;
    }

    public UUID getId() {
        return id;
    }

    public BlockPos getMarkerPos() {
        return markerPos;
    }

    public Set<BlockPos> getArea() {
        return area;
    }

    public void setArea(Set<BlockPos> area) {
        this.area = area;
    }

    public Set<CropKind> getAllowedCrops() {
        return Collections.unmodifiableSet(allowedCrops);
    }

    public boolean allows(CropKind kind) {
        return allowedCrops.contains(kind);
    }

    /** Toggles whether {@code kind} may be planted here, returning the new allowed state. */
    public boolean toggleCrop(CropKind kind) {
        if (allowedCrops.remove(kind)) {
            return false;
        }
        allowedCrops.add(kind);
        return true;
    }

    public CropKind getHighlightedCrop() {
        return CropKind.values()[highlightedCropIndex % CropKind.values().length];
    }

    public CropKind cycleHighlightedCrop() {
        highlightedCropIndex = (highlightedCropIndex + 1) % CropKind.values().length;
        return getHighlightedCrop();
    }

    @Nullable
    public UUID getAssignedStructureId() {
        return assignedStructureId;
    }

    public void setAssignedStructureId(@Nullable UUID assignedStructureId) {
        this.assignedStructureId = assignedStructureId;
    }
}
