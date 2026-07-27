package com.sheepfromheaven.minenow.block;

import com.sheepfromheaven.minenow.dominion.DominionStructure;
import com.sheepfromheaven.minenow.dominion.StructureType;
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
    private List<DominionStructure.RequirementStatus> requirementStatuses = new ArrayList<>();
    private List<DominionStructure.RequirementStatus> rangeRequirementStatuses = new ArrayList<>();
    private DominionStructure.RequirementStatus spaceStatus = null;

    public StructureMarkerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.STRUCTURE_MARKER_BE.get(), pos, state);
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

    public List<DominionStructure.RequirementStatus> getRequirementStatuses() {
        return requirementStatuses;
    }

    public void setRequirementStatuses(List<DominionStructure.RequirementStatus> statuses) {
        this.requirementStatuses = statuses;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    public List<DominionStructure.RequirementStatus> getRangeRequirementStatuses() {
        return rangeRequirementStatuses;
    }

    public void setRangeRequirementStatuses(List<DominionStructure.RequirementStatus> statuses) {
        this.rangeRequirementStatuses = statuses;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    public DominionStructure.RequirementStatus getSpaceStatus() {
        return spaceStatus;
    }

    public void setSpaceStatus(DominionStructure.RequirementStatus spaceStatus) {
        this.spaceStatus = spaceStatus;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    public void refreshStatuses(DominionStructure structure, Level level) {
        setRequirementStatuses(structure.checkRequirements(level));
        setSpaceStatus(structure.getSpaceStatus());
        setRangeRequirementStatuses(structure.checkRangeRequirements(level));
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
        if (!requirementStatuses.isEmpty()) {
            var list = output.childrenList("Requirements");
            for (var rs : requirementStatuses) {
                var child = list.addChild();
                child.putString("Name", rs.name());
                child.putInt("Required", rs.required());
                child.putInt("Found", rs.found());
            }
        }
        if (spaceStatus != null) {
            output.putInt("SpaceRequired", spaceStatus.required());
            output.putInt("SpaceFound", spaceStatus.found());
        }
        if (!rangeRequirementStatuses.isEmpty()) {
            var rangeList = output.childrenList("RangeRequirements");
            for (var rs : rangeRequirementStatuses) {
                var child = rangeList.addChild();
                child.putString("Name", rs.name());
                child.putInt("Required", rs.required());
                child.putInt("Found", rs.found());
            }
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        structureName = input.getStringOr("StructureName", "");
        structureType = StructureType.valueOf(input.getStringOr("StructureType", "HOUSE"));
        input.getString("DominionId").ifPresent(s -> dominionId = UUID.fromString(s));
        input.getString("StructureId").ifPresent(s -> structureId = UUID.fromString(s));
        requirementStatuses = new ArrayList<>();
        for (var child : input.childrenListOrEmpty("Requirements")) {
            requirementStatuses.add(new DominionStructure.RequirementStatus(
                    child.getStringOr("Name", ""),
                    child.getIntOr("Required", 0),
                    child.getIntOr("Found", 0)));
        }
        spaceStatus = input.getInt("SpaceRequired").isPresent()
                ? new DominionStructure.RequirementStatus("Space", input.getIntOr("SpaceRequired", 0), input.getIntOr("SpaceFound", 0))
                : null;
        rangeRequirementStatuses = new ArrayList<>();
        for (var child : input.childrenListOrEmpty("RangeRequirements")) {
            rangeRequirementStatuses.add(new DominionStructure.RequirementStatus(
                    child.getStringOr("Name", ""),
                    child.getIntOr("Required", 0),
                    child.getIntOr("Found", 0)));
        }
    }

    @Override
    public AABB getRenderBoundingBox() {
        int radius = structureType.getRangeRadius();
        if (radius <= 0) {
            return new AABB(getBlockPos());
        }
        int height = structureType.getRangeHeight();
        var pos = getBlockPos();
        return new AABB(
                pos.getX() - radius, pos.getY() - height, pos.getZ() - radius,
                pos.getX() + radius + 1, pos.getY() + height + 1, pos.getZ() + radius + 1
        );
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
