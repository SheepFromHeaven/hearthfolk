package com.sheepfromheaven.minenow.dominion;

import com.sheepfromheaven.minenow.inhabitant.InhabitantEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;
import com.sheepfromheaven.minenow.pathfinding.DominionPathNode;
import com.sheepfromheaven.minenow.structure.DominionStructure;
import com.sheepfromheaven.minenow.structure.StructureType;
import com.sheepfromheaven.minenow.structure.field.Field;

public class Dominion {
    private final UUID id;
    private final UUID ownerUuid;
    private String name;
    private BlockPos bannerPos;
    private DominionTier tier;
    private final Map<UUID, DominionStructure> structures = new HashMap<>();
    private final Map<UUID, DominionInhabitant> inhabitants = new HashMap<>();
    private final Map<UUID, DominionPathNode> pathNodes = new HashMap<>();
    private final Map<UUID, Field> fields = new HashMap<>();
    /** The single immigrant candidate currently walking to (or awaiting a decision at) the banner, if any. */
    @Nullable
    private UUID pendingImmigrantId;
    /** Game tick before which another immigration roll should not be attempted (rate limit after approve/reject). */
    private long nextImmigrationAttemptTick;

    /** Structure types that make a dominion "attractive" enough for immigrants to wander in on their own. */
    private static final Set<StructureType> ATTRACTION_TYPES = Set.of(StructureType.TAVERN);
    /** Ticks (~2 minutes) a dominion must wait after an immigrant is approved or rejected before rolling again. */
    private static final long IMMIGRATION_COOLDOWN_TICKS = 2400;

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
            unassignAllResidents(structure, level);
            unassignAllWorkers(structure, level);
            structures.remove(structureId);
            for (var field : fields.values()) {
                if (structureId.equals(field.getAssignedStructureId())) {
                    field.setAssignedStructureId(null);
                }
            }
        }
    }

    public void addField(Field field) {
        fields.put(field.getId(), field);
    }

    public void removeField(UUID fieldId) {
        fields.remove(fieldId);
    }

    public Field getFieldById(UUID fieldId) {
        return fields.get(fieldId);
    }

    /** The field whose marker sits exactly at {@code markerPos}, if any. */
    public Field getFieldAt(BlockPos markerPos) {
        for (var f : fields.values()) {
            if (f.getMarkerPos().equals(markerPos)) {
                return f;
            }
        }
        return null;
    }

    /** The field whose claimed farmland area contains {@code pos}, if any. */
    public Field getFieldContaining(BlockPos pos) {
        for (var f : fields.values()) {
            if (f.getArea().contains(pos)) {
                return f;
            }
        }
        return null;
    }

    public Collection<Field> getFields() {
        return Collections.unmodifiableCollection(fields.values());
    }

    /** All fields currently assigned to the given structure (typically a FARM), e.g. for a farmer to tend. */
    public List<Field> getFieldsAssignedTo(UUID structureId) {
        return fields.values().stream()
                .filter(f -> structureId.equals(f.getAssignedStructureId()))
                .collect(Collectors.toList());
    }

    /** Frees {@code residentId}'s slot (and their bed's POI reservation) on {@code structure}, if they hold one there. */
    public void unassignInhabitant(DominionStructure structure, UUID residentId, ServerLevel level) {
        if (residentId == null || !structure.getAssignedResidentIds().contains(residentId)) return;
        var inhabitant = inhabitants.get(residentId);
        if (inhabitant != null) {
            inhabitant.setHomeStructureId(null);
            var entity = level.getEntity(inhabitant.getEntityUuid());
            if (entity instanceof InhabitantEntity ie) {
                ie.setHomeStructureId(null);
            }
        }
        releaseHomePoi(structure, residentId, level);
        structure.removeResident(residentId);
    }

    /** Frees every resident slot on {@code structure} - e.g. when the structure itself is being removed. */
    public void unassignAllResidents(DominionStructure structure, ServerLevel level) {
        for (var residentId : List.copyOf(structure.getAssignedResidentIds())) {
            unassignInhabitant(structure, residentId, level);
        }
    }

    /** Frees {@code workerId}'s slot on {@code structure}, if they hold one there. */
    public void unassignWorker(DominionStructure structure, UUID workerId, ServerLevel level) {
        if (workerId == null || !structure.getAssignedWorkerIds().contains(workerId)) return;
        var inhabitant = inhabitants.get(workerId);
        if (inhabitant != null) {
            inhabitant.setWorkStructureId(null);
            var entity = level.getEntity(inhabitant.getEntityUuid());
            if (entity instanceof InhabitantEntity ie) {
                ie.setWorkStructureId(null);
            }
        }
        structure.removeWorker(workerId);
    }

    /** Frees every work slot on {@code structure} - e.g. when the structure itself is being removed. */
    public void unassignAllWorkers(DominionStructure structure, ServerLevel level) {
        for (var workerId : List.copyOf(structure.getAssignedWorkerIds())) {
            unassignWorker(structure, workerId, level);
        }
    }

    /**
     * Assigns {@code inhabitantId} to the next open resident slot on {@code structure}, first
     * unassigning them from any other structure they called home - an inhabitant can only ever have one
     * home at a time. Refuses to move someone in if the structure doesn't meet its requirements (e.g. no
     * bed) or every slot is already taken.
     *
     * @return true if they now live here; false if the inhabitant doesn't exist, the structure isn't
     * ready, or there's no open slot
     */
    public boolean assignHouse(DominionStructure structure, UUID inhabitantId, ServerLevel level) {
        if (inhabitantId == null) return false;
        if (!structure.isFullySatisfied(level, this)) return false;
        var newInhabitant = inhabitants.get(inhabitantId);
        if (newInhabitant == null) return false;

        for (var s : structures.values()) {
            if (s.getAssignedResidentIds().contains(inhabitantId)) {
                unassignInhabitant(s, inhabitantId, level);
            }
        }

        if (!structure.hasOpenResidentSlot()) return false;

        structure.addResident(inhabitantId);
        newInhabitant.setHomeStructureId(structure.getId());
        var entity = level.getEntity(newInhabitant.getEntityUuid());
        if (entity instanceof InhabitantEntity ie) {
            ie.setHomeStructureId(structure.getId());
        }
        claimHomePoi(structure, inhabitantId, level);
        return true;
    }

    /**
     * Reserves {@code residentId}'s own bed as a vanilla "home" point of interest so wandering vanilla
     * villagers can't claim it once it's assigned to an inhabitant.
     */
    private void claimHomePoi(DominionStructure structure, UUID residentId, ServerLevel level) {
        var bedPos = resolveHomePoiPos(structure, residentId, level);
        if (bedPos == null) return;
        level.getPoiManager().take(type -> type.is(PoiTypes.HOME), (type, pos) -> pos.equals(bedPos), bedPos, 1);
    }

    private void releaseHomePoi(DominionStructure structure, UUID residentId, ServerLevel level) {
        var bedPos = resolveHomePoiPos(structure, residentId, level);
        if (bedPos == null) return;
        var poiManager = level.getPoiManager();
        if (poiManager.exists(bedPos, type -> type.is(PoiTypes.HOME))) {
            poiManager.release(bedPos);
        }
    }

    /** {@code residentId}'s own bed (by their slot index among the structure's residents), as a home-POI head position. */
    private BlockPos resolveHomePoiPos(DominionStructure structure, UUID residentId, ServerLevel level) {
        int slotIndex = structure.getAssignedResidentIds().indexOf(residentId);
        if (slotIndex < 0) return null;
        var bedPos = structure.findBedPos(level, slotIndex);
        var state = level.getBlockState(bedPos);
        if (!state.is(BlockTags.BEDS)) return null;
        if (state.getValue(BedBlock.PART) != BedPart.HEAD) {
            bedPos = bedPos.relative(BedBlock.getConnectedDirection(state));
        }
        return bedPos;
    }

    /**
     * Assigns {@code inhabitantId} to the next open work slot on {@code structure}, first unassigning
     * them from any other job they held - an inhabitant can only ever hold one job at a time.
     *
     * @return true if they now occupy a slot here; false if the inhabitant doesn't exist or every
     * slot is already taken
     */
    public boolean assignWorker(DominionStructure structure, UUID inhabitantId, ServerLevel level) {
        if (inhabitantId == null) return false;
        var newInhabitant = inhabitants.get(inhabitantId);
        if (newInhabitant == null) return false;

        for (var s : structures.values()) {
            if (s.getAssignedWorkerIds().contains(inhabitantId)) {
                unassignWorker(s, inhabitantId, level);
            }
        }

        if (!structure.hasOpenWorkSlot()) return false;

        structure.addWorker(inhabitantId);
        newInhabitant.setWorkStructureId(structure.getId());
        var entity = level.getEntity(newInhabitant.getEntityUuid());
        if (entity instanceof InhabitantEntity ie) {
            ie.setWorkStructureId(structure.getId());
        }
        return true;
    }

    public DominionStructure getStructureById(UUID structureId) {
        return structures.get(structureId);
    }

    /**
     * The highest level {@code structureId} could reach right now, if the player triggered every
     * upgrade in sequence - the lower of its type's own cap and this dominion's tier cap. Does not
     * check per-level requirements, only the ceiling.
     */
    public int getStructureLevelCap(UUID structureId) {
        var structure = structures.get(structureId);
        if (structure == null) return 0;
        return Math.min(structure.getType().getMaxLevel(), tier.getMaxStructureLevel());
    }

    /**
     * Player-triggered upgrade: advances {@code structureId} to the next level if its requirements
     * for that level are met and the dominion's current tier allows it. Never happens automatically.
     *
     * @return true if the structure leveled up, false if it's missing, already at its cap, or its
     * next-level requirements aren't met yet
     */
    public boolean tryLevelUpStructure(UUID structureId, ServerLevel level) {
        var structure = structures.get(structureId);
        if (structure == null || structure.isMaxLevel()) return false;
        int nextLevel = structure.getLevel() + 1;
        if (nextLevel > tier.getMaxStructureLevel()) return false;
        if (!structure.isReadyToLevelUp(level, this)) return false;
        structure.setLevel(nextLevel);
        return true;
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

    /**
     * Adds {@code inhabitant} to this dominion, refusing if the dominion is already at its tier's
     * inhabitant cap.
     *
     * @return true if added, false if rejected because the dominion is at capacity
     */
    public boolean addInhabitant(DominionInhabitant inhabitant) {
        if (inhabitants.size() >= tier.getMaxInhabitants()) {
            return false;
        }
        inhabitants.put(inhabitant.getId(), inhabitant);
        return true;
    }

    /** True if this dominion has room for at least one more inhabitant at its current tier. */
    public boolean hasRoomForInhabitant() {
        return inhabitants.size() < tier.getMaxInhabitants();
    }

    @Nullable
    public UUID getPendingImmigrantId() {
        return pendingImmigrantId;
    }

    public void setPendingImmigrantId(@Nullable UUID pendingImmigrantId) {
        this.pendingImmigrantId = pendingImmigrantId;
    }

    public long getNextImmigrationAttemptTick() {
        return nextImmigrationAttemptTick;
    }

    public void setNextImmigrationAttemptTick(long nextImmigrationAttemptTick) {
        this.nextImmigrationAttemptTick = nextImmigrationAttemptTick;
    }

    /** Vertical range (blocks above/below bannerPos) scanned when looking for a bell anywhere in the dominion's area. */
    private static final int BELL_SCAN_VERTICAL_RANGE = 16;

    /**
     * True if this dominion contains at least one structure that draws immigrants on its own: either a
     * logical attraction structure (currently just a tavern) or a real vanilla bell block placed
     * anywhere within the dominion's overall claimed area (centered on {@code bannerPos}, out to its
     * radius), regardless of whether it sits inside a registered structure's footprint.
     */
    public boolean isAttractive(ServerLevel level) {
        return structures.values().stream().anyMatch(s -> ATTRACTION_TYPES.contains(s.getType()))
                || hasBellInArea(level);
    }

    /**
     * This dominion's effective claimed radius in blocks: the configured base
     * {@code Config.defaultDominionRadius} scaled by its current tier's {@link DominionTier#getRadiusMultiplier()}.
     * Higher-tier dominions claim a proportionally larger area. Used for containment/claim checks, the
     * bell-attraction area scan, and the immigrant spawn-ring distance calc.
     */
    public int getEffectiveRadius() {
        return (int) Math.round(com.sheepfromheaven.minenow.core.Config.defaultDominionRadius * tier.getRadiusMultiplier());
    }

    private boolean hasBellInArea(ServerLevel level) {
        int radius = getEffectiveRadius();
        var min = bannerPos.offset(-radius, -BELL_SCAN_VERTICAL_RANGE, -radius);
        var max = bannerPos.offset(radius, BELL_SCAN_VERTICAL_RANGE, radius);
        for (var pos : BlockPos.betweenClosed(min, max)) {
            if (level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.BELL)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Pure gating check for whether this dominion may roll for a new immigration attempt right now:
     * attractive, not at capacity, no immigrant currently pending, and not on cooldown. Extracted as its
     * own method (rather than inlined in the periodic tick) so it can be tested deterministically without
     * relying on RNG.
     */
    public boolean canAttemptImmigration(long currentTick, ServerLevel level) {
        return isAttractive(level)
                && hasRoomForInhabitant()
                && pendingImmigrantId == null
                && currentTick >= nextImmigrationAttemptTick;
    }

    /** Total items currently stored across every STOREHOUSE-type structure's containers. */
    public int getTotalStoredResources(ServerLevel level) {
        int total = 0;
        for (var structure : structures.values()) {
            if (structure.getType() != StructureType.STOREHOUSE) continue;
            for (var count : structure.getInventory(level, false).values()) {
                total += count;
            }
        }
        return total;
    }

    /**
     * Recomputes population/structures/stored-resources against the current tier's level-up milestone
     * and advances exactly one tier if met. Safe to call repeatedly/periodically - a live recomputation,
     * no persisted progress counters. Returns the new tier if it leveled up, or {@code null} otherwise.
     */
    @Nullable
    public DominionTier checkAndApplyLevelUp(ServerLevel level) {
        var milestone = tier.getLevelUpMilestone();
        var nextTier = tier.next();
        if (milestone == null || nextTier == null) {
            return null;
        }
        int population = inhabitants.size();
        int structureCount = structures.size();
        int storedResources = getTotalStoredResources(level);
        if (population >= milestone.minPopulation()
                && structureCount >= milestone.minStructures()
                && storedResources >= milestone.minStoredResources()) {
            this.tier = nextTier;
            return nextTier;
        }
        return null;
    }

    public void removeInhabitant(UUID inhabitantId, ServerLevel level) {
        var inhabitant = inhabitants.remove(inhabitantId);
        if (inhabitant == null) return;
        if (inhabitant.getHomeStructureId() != null) {
            var structure = structures.get(inhabitant.getHomeStructureId());
            if (structure != null) {
                releaseHomePoi(structure, inhabitantId, level);
                structure.removeResident(inhabitantId);
            }
        }
        if (inhabitant.getWorkStructureId() != null) {
            var structure = structures.get(inhabitant.getWorkStructureId());
            if (structure != null) {
                structure.removeWorker(inhabitantId);
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
        fields.clear();
    }
}
