package com.sheepfromheaven.minenow.dominion;

import net.minecraft.core.BlockPos;

import java.util.*;

public class Dominion {
    private final UUID id;
    private final UUID ownerUuid;
    private String name;
    private BlockPos bannerPos;
    private DominionTier tier;
    private final Map<UUID, DominionStructure> structures = new HashMap<>();

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

    public void removeStructure(UUID structureId) {
        structures.remove(structureId);
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
}
