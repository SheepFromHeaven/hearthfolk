package com.sheepfromheaven.minenow.dominion;

import net.minecraft.core.BlockPos;

import java.util.Set;
import java.util.UUID;

public class DominionStructure {
    private final UUID id;
    private String name;
    private StructureType type;
    private final BlockPos markerPos;
    private final Set<BlockPos> roomBlocks;

    public DominionStructure(String name, StructureType type, BlockPos markerPos, Set<BlockPos> roomBlocks) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.type = type;
        this.markerPos = markerPos;
        this.roomBlocks = roomBlocks;
    }

    public DominionStructure(UUID id, String name, StructureType type, BlockPos markerPos, Set<BlockPos> roomBlocks) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.markerPos = markerPos;
        this.roomBlocks = roomBlocks;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public StructureType getType() {
        return type;
    }

    public void setType(StructureType type) {
        this.type = type;
    }

    public BlockPos getMarkerPos() {
        return markerPos;
    }

    public Set<BlockPos> getRoomBlocks() {
        return roomBlocks;
    }

    public boolean containsBlock(BlockPos pos) {
        return roomBlocks.contains(pos);
    }
}
