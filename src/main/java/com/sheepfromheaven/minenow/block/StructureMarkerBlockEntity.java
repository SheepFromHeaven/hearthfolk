package com.sheepfromheaven.minenow.block;

import com.sheepfromheaven.minenow.dominion.StructureType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.UUID;

public class StructureMarkerBlockEntity extends BlockEntity {
    private String structureName = "";
    private StructureType structureType = StructureType.HOUSE;
    private UUID dominionId = null;
    private UUID structureId = null;

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
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        structureName = input.getStringOr("StructureName", "");
        structureType = StructureType.valueOf(input.getStringOr("StructureType", "HOUSE"));
        input.getString("DominionId").ifPresent(s -> dominionId = UUID.fromString(s));
        input.getString("StructureId").ifPresent(s -> structureId = UUID.fromString(s));
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
