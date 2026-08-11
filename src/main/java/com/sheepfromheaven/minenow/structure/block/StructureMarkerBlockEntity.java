package com.sheepfromheaven.minenow.structure.block;

import com.sheepfromheaven.minenow.structure.StructureBlocks;
import com.sheepfromheaven.minenow.structure.DominionStructure;
import com.sheepfromheaven.minenow.structure.StructureType;
import com.sheepfromheaven.minenow.requirement.RequirementStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class StructureMarkerBlockEntity extends BlockEntity {
    private String structureName = "";
    private StructureType structureType = StructureType.HOUSE;
    private UUID dominionId = null;
    private UUID structureId = null;
    private List<RequirementStatus> rangeRequirementStatuses = new ArrayList<>();
    /** Half-extents of this structure's placement territory box, mirrored for client rendering. 0 = none. */
    private int territoryHalfX = 0;
    private int territoryHalfY = 0;
    private int territoryHalfZ = 0;

    public StructureMarkerBlockEntity(BlockPos pos, BlockState state) {
        super(StructureBlocks.STRUCTURE_MARKER_BE.get(), pos, state);
    }

    public String getStructureName() {
        return structureName;
    }

    public StructureType getStructureType() {
        return structureType;
    }

    public UUID getDominionId() {
        return dominionId;
    }

    public UUID getStructureId() {
        return structureId;
    }

    public List<RequirementStatus> getRangeRequirementStatuses() {
        return rangeRequirementStatuses;
    }

    public void setRangeRequirementStatuses(List<RequirementStatus> statuses) {
        this.rangeRequirementStatuses = statuses;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    public int getTerritoryHalfX() {
        return territoryHalfX;
    }

    public int getTerritoryHalfY() {
        return territoryHalfY;
    }

    public int getTerritoryHalfZ() {
        return territoryHalfZ;
    }

    public boolean hasTerritory() {
        return territoryHalfX > 0 && territoryHalfY > 0 && territoryHalfZ > 0;
    }

    /** Build/level requirement statuses are no longer mirrored here - they flow to the screen live via {@code OpenStructureScreenPacket} instead (see {@link com.sheepfromheaven.minenow.network.ModNetwork#sendOpenStructureScreen}). The placement territory box, however, is mirrored so the marker can render it client-side. */
    public void refreshStatuses(DominionStructure structure, Level level) {
        setRangeRequirementStatuses(structure.checkRangeRequirements(level));
        var territory = structure.getTerritory();
        territoryHalfX = territory != null ? territory.halfX() : 0;
        territoryHalfY = territory != null ? territory.halfY() : 0;
        territoryHalfZ = territory != null ? territory.halfZ() : 0;
    }

    public void setStructureData(String name, StructureType type, UUID dominionId, UUID structureId) {
        this.structureName = name;
        this.structureType = type;
        this.dominionId = dominionId;
        this.structureId = structureId;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("StructureName", structureName);
        output.putString("StructureType", structureType.name());
        if (dominionId != null) output.putString("DominionId", dominionId.toString());
        if (structureId != null) output.putString("StructureId", structureId.toString());
        if (!rangeRequirementStatuses.isEmpty()) {
            var rangeList = output.childrenList("RangeRequirements");
            for (var rs : rangeRequirementStatuses) {
                var child = rangeList.addChild();
                child.putString("Name", rs.name());
                child.putInt("Required", rs.required());
                child.putInt("Found", rs.found());
            }
        }
        if (hasTerritory()) {
            output.putInt("TerritoryHalfX", territoryHalfX);
            output.putInt("TerritoryHalfY", territoryHalfY);
            output.putInt("TerritoryHalfZ", territoryHalfZ);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        structureName = input.getStringOr("StructureName", "");
        structureType = StructureType.valueOf(input.getStringOr("StructureType", "HOUSE"));
        input.getString("DominionId").ifPresent(s -> dominionId = UUID.fromString(s));
        input.getString("StructureId").ifPresent(s -> structureId = UUID.fromString(s));
        rangeRequirementStatuses = new ArrayList<>();
        for (var child : input.childrenListOrEmpty("RangeRequirements")) {
            rangeRequirementStatuses.add(new RequirementStatus(
                    child.getStringOr("Name", ""),
                    child.getIntOr("Required", 0),
                    child.getIntOr("Found", 0)));
        }
        territoryHalfX = input.getIntOr("TerritoryHalfX", 0);
        territoryHalfY = input.getIntOr("TerritoryHalfY", 0);
        territoryHalfZ = input.getIntOr("TerritoryHalfZ", 0);
    }

    public AABB getRenderBoundingBox() {
        var box = new AABB(getBlockPos());
        var pos = getBlockPos();

        int radius = structureType.getRangeRadius();
        if (radius > 0) {
            int height = structureType.getRangeHeight();
            box = box.minmax(new AABB(
                    pos.getX() - radius, pos.getY() - height, pos.getZ() - radius,
                    pos.getX() + radius + 1, pos.getY() + height + 1, pos.getZ() + radius + 1
            ));
        }

        if (hasTerritory()) {
            box = box.minmax(new AABB(
                    pos.getX() - territoryHalfX, pos.getY() - territoryHalfY, pos.getZ() - territoryHalfZ,
                    pos.getX() + territoryHalfX + 1, pos.getY() + territoryHalfY + 1, pos.getZ() + territoryHalfZ + 1
            ));
        }

        return box;
    }

    @Override
    public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        return this.saveWithoutMetadata(registries);
    }
}
