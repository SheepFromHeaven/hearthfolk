package com.sheepfromheaven.minenow.dominion;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

public class DominionInhabitant {
    private final UUID id;
    private final UUID entityUuid;
    private String name;
    private UUID homeStructureId;
    private UUID workStructureId;
    private final Map<NeedType, Float> needs;

    public DominionInhabitant(UUID entityUuid, String name) {
        this.id = UUID.randomUUID();
        this.entityUuid = entityUuid;
        this.name = name;
        this.needs = new EnumMap<>(NeedType.class);
        for (var need : NeedType.values()) {
            this.needs.put(need, 1.0f);
        }
    }

    public DominionInhabitant(UUID id, UUID entityUuid, String name, UUID homeStructureId, UUID workStructureId, Map<NeedType, Float> needs) {
        this.id = id;
        this.entityUuid = entityUuid;
        this.name = name;
        this.homeStructureId = homeStructureId;
        this.workStructureId = workStructureId;
        this.needs = new EnumMap<>(needs);
    }

    public UUID getId() {
        return id;
    }

    public UUID getEntityUuid() {
        return entityUuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID getHomeStructureId() {
        return homeStructureId;
    }

    public void setHomeStructureId(UUID homeStructureId) {
        this.homeStructureId = homeStructureId;
    }

    public UUID getWorkStructureId() {
        return workStructureId;
    }

    public void setWorkStructureId(UUID workStructureId) {
        this.workStructureId = workStructureId;
    }

    public float getNeed(NeedType type) {
        return needs.getOrDefault(type, 0.0f);
    }

    public void setNeed(NeedType type, float value) {
        needs.put(type, Math.max(0.0f, Math.min(1.0f, value)));
    }

    public Map<NeedType, Float> getNeeds() {
        return needs;
    }
}
