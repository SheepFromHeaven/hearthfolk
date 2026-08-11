package com.sheepfromheaven.minenow.structure.mine;

import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * A player-placed mining claim: adds a workable column (the marker's XZ position, at the owning
 * MINE structure's work area radius) to the mine's tunneling area, in addition to whatever the
 * structure's own marker+radius already covers. A mine can have any number of claims assigned to
 * it this way, letting a miner tunnel at several separate sites rather than just around its hut.
 */
public class MineClaim {
    private final UUID id;
    private final BlockPos markerPos;
    @Nullable
    private UUID assignedStructureId;

    public MineClaim(BlockPos markerPos) {
        this(UUID.randomUUID(), markerPos, null);
    }

    public MineClaim(UUID id, BlockPos markerPos, @Nullable UUID assignedStructureId) {
        this.id = id;
        this.markerPos = markerPos;
        this.assignedStructureId = assignedStructureId;
    }

    public UUID getId() {
        return id;
    }

    public BlockPos getMarkerPos() {
        return markerPos;
    }

    @Nullable
    public UUID getAssignedStructureId() {
        return assignedStructureId;
    }

    public void setAssignedStructureId(@Nullable UUID assignedStructureId) {
        this.assignedStructureId = assignedStructureId;
    }
}
