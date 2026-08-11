package com.sheepfromheaven.minenow.structure;

import com.sheepfromheaven.minenow.dominion.Dominion;
import com.sheepfromheaven.minenow.structure.field.Field;
import com.sheepfromheaven.minenow.structure.mine.MineClaim;
import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * A kind of placeable work-area marker block. Each kind attaches to exactly one {@link StructureType} and
 * knows how to enumerate the markers currently assigned to a given structure. This is the seam that lets
 * "one more marker type" register here instead of threading marker specifics through the requirement,
 * territory, and job code.
 */
public enum MarkerKind {
    MINE_CLAIM("mineClaim", "Mine Claim", StructureType.MINE),
    FIELD("field", "Field", StructureType.FARM);

    private final String id;
    private final String displayName;
    private final StructureType structureType;

    MarkerKind(String id, String displayName, StructureType structureType) {
        this.id = id;
        this.displayName = displayName;
        this.structureType = structureType;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public StructureType getStructureType() {
        return structureType;
    }

    @Nullable
    public static MarkerKind byId(String id) {
        for (var kind : values()) {
            if (kind.id.equals(id)) return kind;
        }
        return null;
    }

    /** Whether any marker kind attaches to {@code type} (i.e. the structure has a placement territory). */
    public static boolean acceptedBy(StructureType type) {
        for (var kind : values()) {
            if (kind.structureType == type) return true;
        }
        return false;
    }

    /** Marker positions of this kind currently assigned to {@code structureId} within {@code dominion}. */
    public List<BlockPos> assignedMarkerPositions(Dominion dominion, UUID structureId) {
        return switch (this) {
            case MINE_CLAIM -> dominion.getMineClaimsAssignedTo(structureId).stream()
                    .map(MineClaim::getMarkerPos).toList();
            case FIELD -> dominion.getFieldsAssignedTo(structureId).stream()
                    .map(Field::getMarkerPos).toList();
        };
    }
}
