package com.sheepfromheaven.minenow.dominion;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import java.util.*;

public class DominionStructure {
    private final UUID id;
    private String name;
    private StructureType type;
    private final BlockPos markerPos;
    private Set<BlockPos> roomBlocks;

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

    public void setRoomBlocks(Set<BlockPos> roomBlocks) {
        this.roomBlocks = roomBlocks;
    }

    public boolean containsBlock(BlockPos pos) {
        return roomBlocks.contains(pos);
    }

    public List<RequirementStatus> checkRequirements(Level level) {
        var interiorAndWalls = collectInteriorAndWalls();
        var results = new ArrayList<RequirementStatus>();
        for (var req : type.getRequirements()) {
            int found = 0;
            for (var bp : interiorAndWalls) {
                var state = level.getBlockState(bp);
                if (req.matcher().test(state)) {
                    found++;
                }
            }
            results.add(new RequirementStatus(req.displayName(), req.count(), found));
        }
        return results;
    }

    private Set<BlockPos> collectInteriorAndWalls() {
        var all = new HashSet<>(roomBlocks);
        for (var bp : roomBlocks) {
            for (var dir : Direction.values()) {
                all.add(bp.relative(dir));
            }
        }
        return all;
    }

    public record RequirementStatus(String name, int required, int found) {
        public boolean satisfied() { return found >= required; }
    }
}
