package com.sheepfromheaven.minenow.dominion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.*;
import java.util.stream.Collectors;

public class DominionSavedData extends SavedData {
    private final Map<UUID, Dominion> dominions;

    private static final Codec<StructureRequest> REQUEST_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.STRING.fieldOf("structureId").forGetter(r -> r.structureId().toString()),
                    Codec.STRING.fieldOf("item").forGetter(StructureRequest::itemKey),
                    Codec.STRING.fieldOf("label").forGetter(StructureRequest::label),
                    Codec.INT.fieldOf("amount").forGetter(StructureRequest::amount),
                    Codec.STRING.fieldOf("priority").forGetter(r -> r.priority().name()),
                    Codec.LONG.fieldOf("createdTick").forGetter(StructureRequest::createdTick)
            ).apply(instance, (structureId, itemKey, label, amount, priority, createdTick) ->
                    new StructureRequest(
                            UUID.fromString(structureId),
                            StructureRequest.itemFromKey(itemKey),
                            label,
                            amount,
                            StructureRequest.Priority.valueOf(priority),
                            createdTick
                    )
            )
    );

    private static final Codec<Map.Entry<String, Integer>> DESIRED_STOCK_ENTRY_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.STRING.fieldOf("item").forGetter(Map.Entry::getKey),
                    Codec.INT.fieldOf("amount").forGetter(Map.Entry::getValue)
            ).apply(instance, Map::entry)
    );

    private static final Codec<DominionStructure> STRUCTURE_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.STRING.fieldOf("id").forGetter(s -> s.getId().toString()),
                    Codec.STRING.fieldOf("name").forGetter(DominionStructure::getName),
                    Codec.STRING.fieldOf("type").forGetter(s -> s.getType().name()),
                    BlockPos.CODEC.fieldOf("markerPos").forGetter(DominionStructure::getMarkerPos),
                    BlockPos.CODEC.listOf().fieldOf("roomBlocks").forGetter(s -> new ArrayList<>(s.getRoomBlocks())),
                    Codec.STRING.optionalFieldOf("assignedInhabitantId", "").forGetter(s -> s.getAssignedInhabitantId() != null ? s.getAssignedInhabitantId().toString() : ""),
                    Codec.STRING.optionalFieldOf("assignedWorkerId", "").forGetter(s -> s.getAssignedWorkerId() != null ? s.getAssignedWorkerId().toString() : ""),
                    BlockPos.CODEC.optionalFieldOf("workAreaOffset", BlockPos.ZERO).forGetter(DominionStructure::getWorkAreaOffset),
                    Codec.INT.optionalFieldOf("workAreaRadius", DominionStructure.DEFAULT_WORK_AREA_RADIUS).forGetter(DominionStructure::getWorkAreaRadius),
                    REQUEST_CODEC.listOf().optionalFieldOf("requests", List.of()).forGetter(s -> new ArrayList<>(s.getRequests().values())),
                    DESIRED_STOCK_ENTRY_CODEC.listOf().optionalFieldOf("desiredStock", List.of())
                            .forGetter(s -> s.getDesiredStock().entrySet().stream()
                                    .map(e -> Map.entry(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(e.getKey()).toString(), e.getValue()))
                                    .collect(java.util.stream.Collectors.toList()))
            ).apply(instance, (id, name, type, markerPos, roomBlocks, assignedId, workerId, workAreaOffset, workAreaRadius, requests, desiredStock) -> {
                var structure = new DominionStructure(UUID.fromString(id), name, StructureType.valueOf(type),
                        markerPos, new HashSet<>(roomBlocks),
                        assignedId.isEmpty() ? null : UUID.fromString(assignedId),
                        workerId.isEmpty() ? null : UUID.fromString(workerId),
                        workAreaOffset, workAreaRadius);
                for (var req : requests) {
                    structure.restoreRequest(req);
                }
                for (var entry : desiredStock) {
                    var item = net.minecraft.core.registries.BuiltInRegistries.ITEM
                            .getValue(net.minecraft.resources.Identifier.parse(entry.getKey()));
                    structure.setDesiredStock(item, entry.getValue());
                }
                return structure;
            })
    );

    private static final Codec<DominionInhabitant> INHABITANT_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.STRING.fieldOf("id").forGetter(v -> v.getId().toString()),
                    Codec.STRING.fieldOf("entityUuid").forGetter(v -> v.getEntityUuid().toString()),
                    Codec.STRING.fieldOf("name").forGetter(DominionInhabitant::getName),
                    Codec.STRING.optionalFieldOf("homeStructureId", "").forGetter(v -> v.getHomeStructureId() != null ? v.getHomeStructureId().toString() : ""),
                    Codec.STRING.optionalFieldOf("workStructureId", "").forGetter(v -> v.getWorkStructureId() != null ? v.getWorkStructureId().toString() : ""),
                    Codec.FLOAT.fieldOf("shelter").forGetter(v -> v.getNeed(NeedType.SHELTER)),
                    Codec.FLOAT.fieldOf("food").forGetter(v -> v.getNeed(NeedType.FOOD)),
                    Codec.FLOAT.fieldOf("rest").forGetter(v -> v.getNeed(NeedType.REST))
            ).apply(instance, (id, entityUuid, name, homeId, workId, shelter, food, rest) -> {
                var needs = new java.util.EnumMap<NeedType, Float>(NeedType.class);
                needs.put(NeedType.SHELTER, shelter);
                needs.put(NeedType.FOOD, food);
                needs.put(NeedType.REST, rest);
                return new DominionInhabitant(
                        UUID.fromString(id), UUID.fromString(entityUuid), name,
                        homeId.isEmpty() ? null : UUID.fromString(homeId),
                        workId.isEmpty() ? null : UUID.fromString(workId),
                        needs
                );
            })
    );

    private static final Codec<Dominion> DOMINION_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.STRING.fieldOf("id").forGetter(d -> d.getId().toString()),
                    Codec.STRING.fieldOf("owner").forGetter(d -> d.getOwnerUuid().toString()),
                    Codec.STRING.fieldOf("name").forGetter(Dominion::getName),
                    BlockPos.CODEC.fieldOf("bannerPos").forGetter(Dominion::getBannerPos),
                    Codec.STRING.fieldOf("tier").forGetter(d -> d.getTier().name()),
                    STRUCTURE_CODEC.listOf().optionalFieldOf("structures", List.of()).forGetter(d -> new ArrayList<>(d.getStructures())),
                    INHABITANT_CODEC.listOf().optionalFieldOf("villagers", List.of()).forGetter(d -> new ArrayList<>(d.getInhabitants()))
            ).apply(instance, (id, owner, name, pos, tier, structures, villagers) -> {
                var dominion = new Dominion(UUID.fromString(id), UUID.fromString(owner), name, pos, DominionTier.valueOf(tier));
                for (var s : structures) {
                    dominion.addStructure(s);
                }
                for (var v : villagers) {
                    dominion.addInhabitant(v);
                }
                return dominion;
            })
    );

    private static final Codec<DominionSavedData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    DOMINION_CODEC.listOf().fieldOf("dominions").forGetter(d -> new ArrayList<>(d.dominions.values()))
            ).apply(instance, DominionSavedData::new)
    );

    public static final SavedDataType<DominionSavedData> TYPE = new SavedDataType<>(
            "minenow_dominions",
            DominionSavedData::new,
            CODEC,
            null
    );

    public DominionSavedData() {
        this.dominions = new HashMap<>();
    }

    private DominionSavedData(List<Dominion> dominionList) {
        this.dominions = new HashMap<>();
        for (var d : dominionList) {
            this.dominions.put(d.getId(), d);
        }
    }

    public Dominion createDominion(UUID ownerUuid, String name, BlockPos bannerPos) {
        var dominion = new Dominion(ownerUuid, name, bannerPos);
        dominions.put(dominion.getId(), dominion);
        setDirty();
        return dominion;
    }

    public void removeDominion(UUID dominionId) {
        dominions.remove(dominionId);
        setDirty();
    }

    public Dominion getDominionById(UUID id) {
        return dominions.get(id);
    }

    public Dominion getDominionAt(BlockPos pos) {
        for (var dominion : dominions.values()) {
            if (dominion.getBannerPos().equals(pos)) {
                return dominion;
            }
        }
        return null;
    }

    public Dominion findDominionContaining(BlockPos pos) {
        for (var dominion : dominions.values()) {
            var banner = dominion.getBannerPos();
            int radius = com.sheepfromheaven.minenow.Config.defaultDominionRadius;
            if (Math.abs(pos.getX() - banner.getX()) <= radius &&
                    Math.abs(pos.getZ() - banner.getZ()) <= radius) {
                return dominion;
            }
        }
        return null;
    }

    public Collection<Dominion> getDominionsForPlayer(UUID playerUuid) {
        return dominions.values().stream()
                .filter(d -> d.getOwnerUuid().equals(playerUuid))
                .toList();
    }

    public Collection<Dominion> getAllDominions() {
        return Collections.unmodifiableCollection(dominions.values());
    }

    /**
     * Ticks desired-stock checks for every structure across all dominions. Call periodically from a
     * server tick (e.g. every 200 ticks); not every tick — the inventory scan is not free.
     */
    public void tickStructureStock(ServerLevel level) {
        var gameTick = level.getGameTime();
        for (var dominion : dominions.values()) {
            for (var structure : dominion.getStructures()) {
                if (!structure.getDesiredStock().isEmpty()) {
                    structure.tickStock(level, gameTick);
                    setDirty();
                }
            }
        }
    }

    public static DominionSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }
}
