package com.sheepfromheaven.minenow.dominion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.*;
import java.util.stream.Collectors;
import com.sheepfromheaven.minenow.pathfinding.DominionPathNode;
import com.sheepfromheaven.minenow.structure.DominionStructure;
import com.sheepfromheaven.minenow.structure.StructureType;

import javax.annotation.Nullable;
import com.sheepfromheaven.minenow.structure.field.Field;
import com.sheepfromheaven.minenow.structure.mine.MineClaim;

public class DominionSavedData extends SavedData {
    private final Map<UUID, Dominion> dominions;

    private static final Codec<StructureRequest> REQUEST_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.STRING.fieldOf("structureId").forGetter(r -> r.structureId().toString()),
                    Codec.STRING.fieldOf("item").forGetter(StructureRequest::itemKey),
                    Codec.STRING.optionalFieldOf("category", "").forGetter(StructureRequest::categoryName),
                    Codec.STRING.fieldOf("label").forGetter(StructureRequest::label),
                    Codec.INT.fieldOf("amount").forGetter(StructureRequest::amount),
                    Codec.STRING.fieldOf("priority").forGetter(r -> r.priority().name()),
                    Codec.LONG.fieldOf("createdTick").forGetter(StructureRequest::createdTick)
            ).apply(instance, (structureId, itemKey, categoryName, label, amount, priority, createdTick) ->
                    new StructureRequest(
                            UUID.fromString(structureId),
                            StructureRequest.itemFromKey(itemKey),
                            StructureRequest.categoryFromName(categoryName),
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
                    Codec.STRING.listOf().optionalFieldOf("assignedResidentIds", List.of())
                            .forGetter(s -> s.getAssignedResidentIds().stream().map(UUID::toString).collect(java.util.stream.Collectors.toList())),
                    Codec.STRING.listOf().optionalFieldOf("assignedWorkerIds", List.of())
                            .forGetter(s -> s.getAssignedWorkerIds().stream().map(UUID::toString).collect(java.util.stream.Collectors.toList())),
                    REQUEST_CODEC.listOf().optionalFieldOf("requests", List.of()).forGetter(s -> new ArrayList<>(s.getRequests().values())),
                    DESIRED_STOCK_ENTRY_CODEC.listOf().optionalFieldOf("desiredStock", List.of())
                            .forGetter(s -> s.getDesiredStock().entrySet().stream()
                                    .map(e -> Map.entry(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(e.getKey()).toString(), e.getValue()))
                                    .collect(java.util.stream.Collectors.toList())),
                    net.minecraft.world.item.ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("filterTemplates", List.of())
                            .forGetter(DominionStructure::getFilterTemplates),
                    Codec.BOOL.optionalFieldOf("filterWhitelist", true).forGetter(DominionStructure::isFilterWhitelist),
                    Codec.BOOL.optionalFieldOf("filterMatchMetadata", false).forGetter(DominionStructure::isFilterMatchMetadata),
                    Codec.BOOL.optionalFieldOf("placeSupportBeams", false).forGetter(DominionStructure::isPlaceSupportBeams),
                    Codec.INT.optionalFieldOf("level", 1).forGetter(DominionStructure::getLevel)
            ).apply(instance, (id, name, type, markerPos, roomBlocks, residentIds, workerIds, requests, desiredStock, filterTemplates, filterWhitelist, filterMatchMetadata, placeSupportBeams, level) -> {
                var structure = new DominionStructure(UUID.fromString(id), name, StructureType.valueOf(type),
                        markerPos, new HashSet<>(roomBlocks),
                        residentIds.stream().map(UUID::fromString).toList(),
                        workerIds.stream().map(UUID::fromString).toList());
                for (var req : requests) {
                    structure.restoreRequest(req);
                }
                for (var entry : desiredStock) {
                    var item = net.minecraft.core.registries.BuiltInRegistries.ITEM
                            .getValue(net.minecraft.resources.Identifier.parse(entry.getKey()));
                    structure.setDesiredStock(item, entry.getValue());
                }
                structure.setFilter(filterTemplates, filterWhitelist, filterMatchMetadata);
                structure.setPlaceSupportBeams(placeSupportBeams);
                structure.setLevel(level);
                return structure;
            })
    );

    private static final Codec<DominionPathNode> PATH_NODE_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.STRING.fieldOf("id").forGetter(n -> n.getId().toString()),
                    BlockPos.CODEC.fieldOf("pos").forGetter(DominionPathNode::getPos),
                    Codec.STRING.fieldOf("kind").forGetter(n -> n.getKind().name()),
                    Codec.STRING.optionalFieldOf("impact", DominionPathNode.Impact.MEDIUM.name())
                            .forGetter(n -> n.getImpact().name())
            ).apply(instance, (id, pos, kind, impact) -> {
                DominionPathNode.Impact imp;
                try { imp = DominionPathNode.Impact.valueOf(impact); } catch (IllegalArgumentException e) { imp = DominionPathNode.Impact.MEDIUM; }
                return new DominionPathNode(UUID.fromString(id), pos, DominionPathNode.Kind.valueOf(kind), imp);
            })
    );

    private static final Codec<Field> FIELD_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.STRING.fieldOf("id").forGetter(f -> f.getId().toString()),
                    BlockPos.CODEC.fieldOf("markerPos").forGetter(Field::getMarkerPos),
                    BlockPos.CODEC.listOf().fieldOf("area").forGetter(f -> new ArrayList<>(f.getArea())),
                    Codec.STRING.optionalFieldOf("assignedStructureId", "").forGetter(f -> f.getAssignedStructureId() != null ? f.getAssignedStructureId().toString() : "")
            ).apply(instance, (id, markerPos, area, assignedStructureId) -> {
                return new Field(UUID.fromString(id), markerPos, new HashSet<>(area),
                        assignedStructureId.isEmpty() ? null : UUID.fromString(assignedStructureId));
            })
    );

    private static final Codec<MineClaim> MINE_CLAIM_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.STRING.fieldOf("id").forGetter(c -> c.getId().toString()),
                    BlockPos.CODEC.fieldOf("markerPos").forGetter(MineClaim::getMarkerPos),
                    Codec.STRING.optionalFieldOf("assignedStructureId", "").forGetter(c -> c.getAssignedStructureId() != null ? c.getAssignedStructureId().toString() : "")
            ).apply(instance, (id, markerPos, assignedStructureId) ->
                    new MineClaim(UUID.fromString(id), markerPos,
                            assignedStructureId.isEmpty() ? null : UUID.fromString(assignedStructureId))
            )
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
                    INHABITANT_CODEC.listOf().optionalFieldOf("villagers", List.of()).forGetter(d -> new ArrayList<>(d.getInhabitants())),
                    PATH_NODE_CODEC.listOf().optionalFieldOf("pathNodes", List.of()).forGetter(d -> new ArrayList<>(d.getPathNodes())),
                    FIELD_CODEC.listOf().optionalFieldOf("fields", List.of()).forGetter(d -> new ArrayList<>(d.getFields())),
                    MINE_CLAIM_CODEC.listOf().optionalFieldOf("mineClaims", List.of()).forGetter(d -> new ArrayList<>(d.getMineClaims())),
                    Codec.STRING.optionalFieldOf("pendingImmigrantId", "").forGetter(d -> d.getPendingImmigrantId() != null ? d.getPendingImmigrantId().toString() : ""),
                    Codec.LONG.optionalFieldOf("nextImmigrationAttemptTick", 0L).forGetter(Dominion::getNextImmigrationAttemptTick),
                    net.minecraft.world.level.block.entity.BannerPatternLayers.CODEC.optionalFieldOf("bannerPattern", net.minecraft.world.level.block.entity.BannerPatternLayers.EMPTY)
                            .forGetter(Dominion::getBannerPattern)
            ).apply(instance, (id, owner, name, pos, tier, structures, villagers, pathNodes, fields, mineClaims, pendingImmigrantId, nextImmigrationAttemptTick, bannerPattern) -> {
                var dominion = new Dominion(UUID.fromString(id), UUID.fromString(owner), name, pos, DominionTier.valueOf(tier));
                for (var s : structures) {
                    dominion.addStructure(s);
                }
                for (var v : villagers) {
                    dominion.addInhabitant(v);
                }
                for (var n : pathNodes) {
                    dominion.addPathNode(n);
                }
                for (var f : fields) {
                    dominion.addField(f);
                }
                for (var c : mineClaims) {
                    dominion.addMineClaim(c);
                }
                dominion.setPendingImmigrantId(pendingImmigrantId.isEmpty() ? null : UUID.fromString(pendingImmigrantId));
                dominion.setNextImmigrationAttemptTick(nextImmigrationAttemptTick);
                dominion.setBannerPattern(bannerPattern);
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
            int radius = dominion.getEffectiveRadius();
            if (Math.abs(pos.getX() - banner.getX()) <= radius &&
                    Math.abs(pos.getZ() - banner.getZ()) <= radius) {
                return dominion;
            }
        }
        return null;
    }

    /** A structure matched by a territory query, plus the dominion that owns it (and stores its markers). */
    public record StructureMatch(Dominion dominion, DominionStructure structure) {}

    /**
     * The nearest structure of {@code type} whose {@link DominionStructure#getTerritory() territory}
     * contains {@code pos}, among dominions owned by {@code owner}. A structure's territory may extend
     * beyond its dominion's own bounds, so work-area markers are placed against territory rather than
     * against which dominion contains the marker (only the structure itself must sit inside the dominion).
     */
    @Nullable
    public StructureMatch findStructureWithTerritoryContaining(BlockPos pos, StructureType type, UUID owner) {
        StructureMatch best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (var dominion : dominions.values()) {
            if (!dominion.getOwnerUuid().equals(owner)) continue;
            for (var structure : dominion.getStructures()) {
                if (structure.getType() != type) continue;
                var territory = structure.getTerritory();
                if (territory == null || !territory.contains(pos)) continue;
                double distSq = structure.getMarkerPos().distSqr(pos);
                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
                    best = new StructureMatch(dominion, structure);
                }
            }
        }
        return best;
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

    /**
     * Periodic (every ~100 ticks, i.e. ~5 seconds) re-scan of every field's claimed area: a player may
     * have placed or removed blocks since the field was last computed (adding/removing tillable ground,
     * or opening up a bigger hole for the flood-fill to spread into), so re-run
     * {@link com.sheepfromheaven.minenow.structure.field.FieldAreaDetector#detect} from the marker the
     * same way placement does. Only a successful re-scan replaces the field's area - a marker that's
     * momentarily not enclosed (mid-edit) or whose flood-fill temporarily exceeds the cap just keeps
     * its last-known-good area rather than shrinking/clearing it.
     */
    public void tickFieldRescan(ServerLevel level) {
        for (var dominion : dominions.values()) {
            for (var field : dominion.getFields()) {
                var below = field.getMarkerPos().below();
                var result = com.sheepfromheaven.minenow.structure.field.FieldAreaDetector.detect(
                        level, below, field.getMarkerPos(), com.sheepfromheaven.minenow.core.Config.maxRoomSize);
                if (result instanceof com.sheepfromheaven.minenow.structure.field.FieldAreaDetector.Result.Success(var blocks)
                        && !blocks.equals(field.getArea())) {
                    field.setArea(blocks);
                    setDirty();
                }
            }
        }
    }

    /**
     * Periodic (every ~100 ticks) growth pass for every dominion: recomputes tier level-up and, for
     * attractive dominions off cooldown with room, rolls a 10% chance to spawn an immigration candidate.
     * Shares one tick hook with level-up rather than adding a second redundant loop.
     */
    public void tickGrowth(ServerLevel level) {
        long currentTick = level.getGameTime();
        for (var dominion : dominions.values()) {
            var newTier = dominion.checkAndApplyLevelUp(level);
            if (newTier != null) {
                setDirty();
                var trigger = switch (newTier) {
                    case VILLAGE -> com.sheepfromheaven.minenow.core.ModCriteriaTriggers.DOMINION_TIER_VILLAGE;
                    case TOWN -> com.sheepfromheaven.minenow.core.ModCriteriaTriggers.DOMINION_TIER_TOWN;
                    case CITY -> com.sheepfromheaven.minenow.core.ModCriteriaTriggers.DOMINION_TIER_CITY;
                    default -> null;
                };
                var owner = level.getServer().getPlayerList().getPlayer(dominion.getOwnerUuid());
                if (owner != null) {
                    owner.displayClientMessage(Component.literal(
                            dominion.getName() + " has grown into a " + newTier.getDisplayName() + "!"), false);
                    if (trigger != null) {
                        trigger.get().trigger(owner);
                    }
                }
            }
            if (dominion.canAttemptImmigration(currentTick, level) && level.getRandom().nextFloat() < 0.10f) {
                spawnImmigrationCandidate(level, dominion);
                setDirty();
            }
        }
    }

    private void spawnImmigrationCandidate(ServerLevel level, Dominion dominion) {
        var random = level.getRandom();
        var bannerPos = dominion.getBannerPos();
        double angle = random.nextDouble() * Math.PI * 2.0;
        double radius = dominion.getEffectiveRadius()
                + 16 + random.nextDouble() * 16.0;
        int x = (int) Math.round(bannerPos.getX() + Math.cos(angle) * radius);
        int z = (int) Math.round(bannerPos.getZ() + Math.sin(angle) * radius);
        int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);

        var entity = com.sheepfromheaven.minenow.inhabitant.ModEntities.INHABITANT.get()
                .create(level, net.minecraft.world.entity.EntitySpawnReason.NATURAL);
        if (entity == null) {
            return;
        }
        entity.snapTo(x + 0.5, y, z + 0.5, 0, 0);
        var name = "Newcomer";
        entity.setCustomName(net.minecraft.network.chat.Component.literal(name));
        entity.setCustomNameVisible(true);
        entity.setImmigrationJob(new com.sheepfromheaven.minenow.inhabitant.immigration.ImmigrationJob(dominion.getId()));
        level.addFreshEntity(entity);
        dominion.setPendingImmigrantId(entity.getUUID());
    }

    public static DominionSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }
}
