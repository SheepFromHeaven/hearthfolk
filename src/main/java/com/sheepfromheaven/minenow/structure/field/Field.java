package com.sheepfromheaven.minenow.structure.field;

import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.UUID;

/**
 * A player-claimed patch of ground (placed via a field marker, its footprint auto-detected the same
 * way a {@link com.sheepfromheaven.minenow.structure.block.StructureMarkerBlock} detects an enclosed
 * room - see {@link FieldAreaDetector}). The footprint need not already be farmland; tilling it and
 * keeping it watered is the assigned farmer's job. Which crops may be planted here is configured
 * through the marker's own seed-template inventory (see {@code FieldMarkerBlockEntity}), not stored
 * on this class. Each field is assigned to at most one FARM structure at a time; a single farm can
 * have any number of fields assigned to it.
 */
public class Field {
    private final UUID id;
    private final BlockPos markerPos;
    private Set<BlockPos> area;
    @Nullable
    private UUID assignedStructureId;

    public Field(BlockPos markerPos, Set<BlockPos> area) {
        this(UUID.randomUUID(), markerPos, area, null);
    }

    public Field(UUID id, BlockPos markerPos, Set<BlockPos> area, @Nullable UUID assignedStructureId) {
        this.id = id;
        this.markerPos = markerPos;
        this.area = area;
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

    @Nullable
    public UUID getAssignedStructureId() {
        return assignedStructureId;
    }

    public void setAssignedStructureId(@Nullable UUID assignedStructureId) {
        this.assignedStructureId = assignedStructureId;
    }
}
