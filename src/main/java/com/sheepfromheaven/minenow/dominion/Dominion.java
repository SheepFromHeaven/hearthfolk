package com.sheepfromheaven.minenow.dominion;

import com.sheepfromheaven.minenow.entity.InhabitantEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;

import java.util.*;
import java.util.stream.Collectors;

public class Dominion {
    private final UUID id;
    private final UUID ownerUuid;
    private String name;
    private BlockPos bannerPos;
    private DominionTier tier;
    private final Map<UUID, DominionStructure> structures = new HashMap<>();
    private final Map<UUID, DominionInhabitant> inhabitants = new HashMap<>();
    private final Map<UUID, DominionPathNode> pathNodes = new HashMap<>();

    public Dominion(UUID ownerUuid, String name, BlockPos bannerPos) {
        this.id = UUID.randomUUID();
        this.ownerUuid = ownerUuid;
        this.name = name;
        this.bannerPos = bannerPos;
        this.tier = DominionTier.SETTLEMENT;
    }

    public Dominion(UUID id, UUID ownerUuid, String name, BlockPos bannerPos, DominionTier tier) {
        this.id = id;
        this.ownerUuid = ownerUuid;
        this.name = name;
        this.bannerPos = bannerPos;
        this.tier = tier;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BlockPos getBannerPos() {
        return bannerPos;
    }

    public void setBannerPos(BlockPos bannerPos) {
        this.bannerPos = bannerPos;
    }

    public DominionTier getTier() {
        return tier;
    }

    public void setTier(DominionTier tier) {
        this.tier = tier;
    }

    public void addStructure(DominionStructure structure) {
        structures.put(structure.getId(), structure);
    }

    public void removeStructure(UUID structureId, ServerLevel level) {
        var structure = structures.get(structureId);
        if (structure != null) {
            unassignInhabitant(structure, level);
            unassignWorker(structure, level);
            structures.remove(structureId);
        }
    }

    public void unassignInhabitant(DominionStructure structure, ServerLevel level) {
        UUID inhabitantId = structure.getAssignedInhabitantId();
        if (inhabitantId == null) return;
        var inhabitant = inhabitants.get(inhabitantId);
        if (inhabitant != null) {
            inhabitant.setHomeStructureId(null);
            var entity = level.getEntity(inhabitant.getEntityUuid());
            if (entity instanceof InhabitantEntity ie) {
                ie.setHomeStructureId(null);
            }
        }
        structure.setAssignedInhabitantId(null);
        releaseHomePoi(structure, level);
    }

    public void unassignWorker(DominionStructure structure, ServerLevel level) {
        UUID workerId = structure.getAssignedWorkerId();
        if (workerId == null) return;
        var inhabitant = inhabitants.get(workerId);
        if (inhabitant != null) {
            inhabitant.setWorkStructureId(null);
            var entity = level.getEntity(inhabitant.getEntityUuid());
            if (entity instanceof InhabitantEntity ie) {
                ie.setWorkStructureId(null);
            }
        }
        structure.setAssignedWorkerId(null);
    }

    /**
     * Assigns {@code inhabitantId} as the structure's inhabitant, clearing whoever previously
     * lived there and unassigning the inhabitant from any other structure it called home -
     * an inhabitant can only ever have one home at a time. Pass {@code null} to just vacate it.
     * Refuses to move someone in if the structure doesn't meet its requirements (e.g. no bed);
     * vacating is always allowed. Returns whether the assignment took effect.
     */
    public boolean assignHouse(DominionStructure structure, UUID inhabitantId, ServerLevel level) {
        if (inhabitantId == null) {
            unassignInhabitant(structure, level);
            return true;
        }
        if (!structure.isFullySatisfied(level)) {
            return false;
        }
        unassignInhabitant(structure, level);
        var newInhabitant = inhabitants.get(inhabitantId);
        if (newInhabitant == null) return false;

        for (var s : structures.values()) {
            if (inhabitantId.equals(s.getAssignedInhabitantId())) {
                unassignInhabitant(s, level);
            }
        }

        structure.setAssignedInhabitantId(inhabitantId);
        newInhabitant.setHomeStructureId(structure.getId());
        var entity = level.getEntity(newInhabitant.getEntityUuid());
        if (entity instanceof InhabitantEntity ie) {
            ie.setHomeStructureId(structure.getId());
        }
        claimHomePoi(structure, level);
        return true;
    }

    /**
     * Reserves the structure's bed as a vanilla "home" point of interest so wandering vanilla
     * villagers can't claim it as their own bed once it's assigned to an inhabitant.
     */
    private void claimHomePoi(DominionStructure structure, ServerLevel level) {
        var bedPos = resolveHomePoiPos(structure, level);
        if (bedPos == null) return;
        level.getPoiManager().take(type -> type.is(PoiTypes.HOME), (type, pos) -> pos.equals(bedPos), bedPos, 1);
    }

    private void releaseHomePoi(DominionStructure structure, ServerLevel level) {
        var bedPos = resolveHomePoiPos(structure, level);
        if (bedPos == null) return;
        var poiManager = level.getPoiManager();
        if (poiManager.exists(bedPos, type -> type.is(PoiTypes.HOME))) {
            poiManager.release(bedPos);
        }
    }

    private BlockPos resolveHomePoiPos(DominionStructure structure, ServerLevel level) {
        var bedPos = structure.findBedPos(level);
        var state = level.getBlockState(bedPos);
        if (!state.is(BlockTags.BEDS)) return null;
        if (state.getValue(BedBlock.PART) != BedPart.HEAD) {
            bedPos = bedPos.relative(BedBlock.getConnectedDirection(state));
        }
        return bedPos;
    }

    /**
     * Assigns {@code inhabitantId} as the structure's worker, clearing whoever previously worked
     * there and unassigning the inhabitant from any other job it held - an inhabitant can only
     * ever hold one job at a time. Pass {@code null} to just vacate it.
     */
    public void assignWorker(DominionStructure structure, UUID inhabitantId, ServerLevel level) {
        unassignWorker(structure, level);
        if (inhabitantId == null) return;
        var newInhabitant = inhabitants.get(inhabitantId);
        if (newInhabitant == null) return;

        for (var s : structures.values()) {
            if (inhabitantId.equals(s.getAssignedWorkerId())) {
                unassignWorker(s, level);
            }
        }

        structure.setAssignedWorkerId(inhabitantId);
        newInhabitant.setWorkStructureId(structure.getId());
        var entity = level.getEntity(newInhabitant.getEntityUuid());
        if (entity instanceof InhabitantEntity ie) {
            ie.setWorkStructureId(structure.getId());
        }
    }

    public DominionStructure getStructureById(UUID structureId) {
        return structures.get(structureId);
    }

    public DominionStructure getStructureAt(BlockPos markerPos) {
        for (var s : structures.values()) {
            if (s.getMarkerPos().equals(markerPos)) {
                return s;
            }
        }
        return null;
    }

    public DominionStructure getStructureContaining(BlockPos pos) {
        for (var s : structures.values()) {
            if (s.containsBlock(pos)) {
                return s;
            }
        }
        return null;
    }

    public Collection<DominionStructure> getStructures() {
        return Collections.unmodifiableCollection(structures.values());
    }

    /**
     * All pending item requests across every structure in this dominion, sorted URGENT first then
     * by creation tick (oldest first within each priority level).
     */
    public List<StructureRequest> getPendingRequests() {
        return structures.values().stream()
                .flatMap(s -> s.getRequests().values().stream())
                .sorted(Comparator
                        .comparing(StructureRequest::priority).reversed()
                        .thenComparingLong(StructureRequest::createdTick))
                .collect(Collectors.toList());
    }

    /** Finds the nearest structure of the given type (by marker distance to {@code from}) satisfying {@code filter}. */
    public Optional<DominionStructure> findNearestStructureOfType(StructureType type, BlockPos from, java.util.function.Predicate<DominionStructure> filter) {
        DominionStructure best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (var s : structures.values()) {
            if (s.getType() != type || !filter.test(s)) continue;
            double distSq = s.getMarkerPos().distSqr(from);
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = s;
            }
        }
        return Optional.ofNullable(best);
    }

    public void addInhabitant(DominionInhabitant inhabitant) {
        inhabitants.put(inhabitant.getId(), inhabitant);
    }

    public void removeInhabitant(UUID inhabitantId, ServerLevel level) {
        var inhabitant = inhabitants.remove(inhabitantId);
        if (inhabitant == null) return;
        if (inhabitant.getHomeStructureId() != null) {
            var structure = structures.get(inhabitant.getHomeStructureId());
            if (structure != null) {
                structure.setAssignedInhabitantId(null);
                releaseHomePoi(structure, level);
            }
        }
        if (inhabitant.getWorkStructureId() != null) {
            var structure = structures.get(inhabitant.getWorkStructureId());
            if (structure != null) {
                structure.setAssignedWorkerId(null);
            }
        }
    }

    public DominionInhabitant getInhabitant(UUID inhabitantId) {
        return inhabitants.get(inhabitantId);
    }

    public DominionInhabitant getInhabitantByEntityUuid(UUID entityUuid) {
        for (var v : inhabitants.values()) {
            if (v.getEntityUuid().equals(entityUuid)) {
                return v;
            }
        }
        return null;
    }

    public Collection<DominionInhabitant> getInhabitants() {
        return Collections.unmodifiableCollection(inhabitants.values());
    }

    public void addPathNode(DominionPathNode node) {
        pathNodes.put(node.getId(), node);
    }

    public void removePathNode(UUID nodeId) {
        pathNodes.remove(nodeId);
    }

    /** The node (of any kind) at the exact given position, if any. */
    public DominionPathNode getPathNodeAt(BlockPos pos) {
        for (var node : pathNodes.values()) {
            if (node.getPos().equals(pos)) {
                return node;
            }
        }
        return null;
    }

    public Collection<DominionPathNode> getPathNodes() {
        return Collections.unmodifiableCollection(pathNodes.values());
    }

    /** The nearest node of the given kind to {@code from}, if any exist. */
    public Optional<DominionPathNode> findNearestPathNode(DominionPathNode.Kind kind, BlockPos from) {
        DominionPathNode best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (var node : pathNodes.values()) {
            if (node.getKind() != kind) continue;
            double distSq = node.getPos().distSqr(from);
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = node;
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * Reverts every inhabitant back to a vanilla villager, clears every structure's blocks,
     * and drops all dominion data. Called when the town banner is destroyed.
     */
    public void disband(ServerLevel level) {
        for (var inhabitant : inhabitants.values()) {
            var entity = level.getEntity(inhabitant.getEntityUuid());
            if (entity instanceof InhabitantEntity ie) {
                ie.convertToVillager();
            }
        }
        inhabitants.clear();

        for (var structure : structures.values()) {
            for (var pos : structure.getRoomBlocks()) {
                level.removeBlock(pos, false);
            }
        }
        structures.clear();
        pathNodes.clear();
    }
}
