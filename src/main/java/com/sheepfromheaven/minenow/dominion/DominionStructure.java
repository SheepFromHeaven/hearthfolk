package com.sheepfromheaven.minenow.dominion;

import com.sheepfromheaven.minenow.entity.job.TreeScanner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;

import java.util.*;

public class DominionStructure {
    public static final int MIN_WORK_AREA_RADIUS = 2;
    public static final int MAX_WORK_AREA_RADIUS = 6;
    public static final int DEFAULT_WORK_AREA_RADIUS = 6;

    private final UUID id;
    private String name;
    private StructureType type;
    private final BlockPos markerPos;
    private Set<BlockPos> roomBlocks;
    private UUID assignedInhabitantId;
    private UUID assignedWorkerId;
    private BlockPos cachedBedPos;
    /** Offset from {@link #markerPos}; {@link BlockPos#ZERO} means the work area is centered on the marker. */
    private BlockPos workAreaOffset = BlockPos.ZERO;
    private int workAreaRadius = DEFAULT_WORK_AREA_RADIUS;

    public DominionStructure(String name, StructureType type, BlockPos markerPos, Set<BlockPos> roomBlocks) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.type = type;
        this.markerPos = markerPos;
        this.roomBlocks = roomBlocks;
    }

    public DominionStructure(UUID id, String name, StructureType type, BlockPos markerPos, Set<BlockPos> roomBlocks,
                              UUID assignedInhabitantId, UUID assignedWorkerId,
                              BlockPos workAreaOffset, int workAreaRadius) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.markerPos = markerPos;
        this.roomBlocks = roomBlocks;
        this.assignedInhabitantId = assignedInhabitantId;
        this.assignedWorkerId = assignedWorkerId;
        this.workAreaOffset = workAreaOffset;
        this.workAreaRadius = clampWorkAreaRadius(workAreaRadius);
    }

    public BlockPos getWorkAreaCenter() {
        return markerPos.offset(workAreaOffset);
    }

    /** The raw offset from the marker, as persisted by the save codec. */
    public BlockPos getWorkAreaOffset() {
        return workAreaOffset;
    }

    public void setWorkAreaCenter(BlockPos center) {
        this.workAreaOffset = center.subtract(markerPos);
    }

    public int getWorkAreaRadius() {
        return workAreaRadius;
    }

    public void setWorkAreaRadius(int radius) {
        this.workAreaRadius = clampWorkAreaRadius(radius);
    }

    private static int clampWorkAreaRadius(int radius) {
        return Math.clamp(radius, MIN_WORK_AREA_RADIUS, MAX_WORK_AREA_RADIUS);
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
        this.cachedBedPos = null;
    }

    public UUID getAssignedInhabitantId() {
        return assignedInhabitantId;
    }

    public void setAssignedInhabitantId(UUID assignedInhabitantId) {
        this.assignedInhabitantId = assignedInhabitantId;
    }

    public UUID getAssignedWorkerId() {
        return assignedWorkerId;
    }

    public void setAssignedWorkerId(UUID assignedWorkerId) {
        this.assignedWorkerId = assignedWorkerId;
    }

    public boolean containsBlock(BlockPos pos) {
        return roomBlocks.contains(pos);
    }

    public BlockPos findBedPos(Level level) {
        if (cachedBedPos != null && level.getBlockState(cachedBedPos).is(BlockTags.BEDS)) {
            return cachedBedPos;
        }
        for (var bp : collectInteriorAndWalls()) {
            if (level.getBlockState(bp).is(BlockTags.BEDS)) {
                cachedBedPos = bp;
                return bp;
            }
        }
        return markerPos;
    }

    public RequirementStatus getSpaceStatus() {
        return new RequirementStatus("Space", type.getMinSize(), roomBlocks.size());
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

    public List<RequirementStatus> checkRangeRequirements(Level level) {
        int radius = type.getRangeRadius();
        var rangeReqs = type.getRangeRequirements();
        if (radius <= 0 || rangeReqs.isEmpty()) {
            return List.of();
        }
        int height = type.getRangeHeight();
        var counts = new int[rangeReqs.size()];
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) continue;
                for (int dy = -height; dy <= height; dy++) {
                    var pos = markerPos.offset(dx, dy, dz);
                    var state = level.getBlockState(pos);
                    for (int i = 0; i < rangeReqs.size(); i++) {
                        var req = rangeReqs.get(i);
                        if (req.matcher().test(state) && hasOpenAirAbove(level, pos, req.airAbove())) {
                            counts[i]++;
                        }
                    }
                }
            }
        }
        var results = new ArrayList<RequirementStatus>();
        for (int i = 0; i < rangeReqs.size(); i++) {
            results.add(new RequirementStatus(rangeReqs.get(i).displayName(), rangeReqs.get(i).count(), counts[i]));
        }
        return results;
    }

    private static final TagKey<net.minecraft.world.level.block.Block> STORAGE_TAG =
            TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("minenow", "storage"));

    public List<BlockPos> findStorageContainers(Level level) {
        var results = new ArrayList<BlockPos>();
        for (var bp : collectInteriorAndWalls()) {
            var state = level.getBlockState(bp);
            if (state.is(STORAGE_TAG) && level.getBlockEntity(bp) instanceof Container) {
                results.add(bp);
            }
        }
        return results;
    }

    public List<BlockPos> findContainers(Level level) {
        var results = new ArrayList<BlockPos>();
        for (var bp : collectInteriorAndWalls()) {
            if (level.getBlockEntity(bp) instanceof Container) {
                results.add(bp);
            }
        }
        return results;
    }

    /**
     * Scans the structure's range for a naturally-grown tree (see {@link TreeScanner}), skipping any
     * log blocks that turn out to be part of a player-built structure rather than a real trunk.
     */
    public Optional<List<BlockPos>> findTreeInRange(Level level) {
        int radius = workAreaRadius;
        int height = type.getRangeHeight();
        var center = getWorkAreaCenter();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) continue;
                for (int dy = -height; dy <= height; dy++) {
                    var pos = center.offset(dx, dy, dz);
                    if (level.getBlockState(pos).is(BlockTags.LOGS)) {
                        var tree = TreeScanner.findTree(level, pos);
                        if (tree.isPresent()) {
                            return tree;
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    public Optional<BlockPos> findRangeRequirementPosition(Level level, StructureType.Requirement requirement) {
        int radius = type.getRangeRadius();
        int height = type.getRangeHeight();
        if (radius <= 0) {
            return Optional.empty();
        }
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) continue;
                for (int dy = -height; dy <= height; dy++) {
                    var pos = markerPos.offset(dx, dy, dz);
                    var state = level.getBlockState(pos);
                    if (requirement.matcher().test(state) && hasOpenAirAbove(level, pos, requirement.airAbove())) {
                        return Optional.of(pos);
                    }
                }
            }
        }
        return Optional.empty();
    }

    private boolean hasOpenAirAbove(Level level, BlockPos pos, int required) {
        for (int i = 1; i <= required; i++) {
            var above = pos.above(i);
            if (!level.getBlockState(above).getCollisionShape(level, above).isEmpty()) {
                return false;
            }
        }
        return true;
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
