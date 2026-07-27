package com.sheepfromheaven.minenow.dominion;

import com.sheepfromheaven.minenow.entity.InhabitantEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.*;

public class Dominion {
    private final UUID id;
    private final UUID ownerUuid;
    private String name;
    private BlockPos bannerPos;
    private DominionTier tier;
    private final Map<UUID, DominionStructure> structures = new HashMap<>();
    private final Map<UUID, DominionInhabitant> inhabitants = new HashMap<>();

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

    public void addInhabitant(DominionInhabitant inhabitant) {
        inhabitants.put(inhabitant.getId(), inhabitant);
    }

    public void removeInhabitant(UUID inhabitantId) {
        var inhabitant = inhabitants.remove(inhabitantId);
        if (inhabitant == null) return;
        if (inhabitant.getHomeStructureId() != null) {
            var structure = structures.get(inhabitant.getHomeStructureId());
            if (structure != null) {
                structure.setAssignedInhabitantId(null);
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
    }
}
