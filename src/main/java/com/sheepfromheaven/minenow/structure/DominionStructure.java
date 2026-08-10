package com.sheepfromheaven.minenow.structure;

import com.sheepfromheaven.minenow.jobs.lumberjack.TreeScanner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Predicate;
import com.sheepfromheaven.minenow.dominion.Dominion;
import com.sheepfromheaven.minenow.dominion.ItemCategory;
import com.sheepfromheaven.minenow.dominion.StructureRequest;
import com.sheepfromheaven.minenow.effect.StructureStats;
import com.sheepfromheaven.minenow.requirement.Requirement;
import com.sheepfromheaven.minenow.requirement.RequirementContext;
import com.sheepfromheaven.minenow.requirement.RequirementStatus;
import com.sheepfromheaven.minenow.requirement.RoomSizeRequirement;

public class DominionStructure {
    public static final int MIN_WORK_AREA_RADIUS = 2;
    public static final int MAX_WORK_AREA_RADIUS = 6;
    public static final int DEFAULT_WORK_AREA_RADIUS = 6;
    private static final int APPROACH_SEARCH_RADIUS = 6;
    /**
     * How far above/below the work area center to look for a tree trunk base. Deliberately more
     * generous than {@link StructureType#getRangeHeight()} (which gates the "Tree" range requirement
     * indicator): that value is tuned tight for placement validation, but the work area center is
     * usually left at the marker's own floor height, and real terrain/tree height routinely differs
     * from that by more than a few blocks even for a tree the player would call "clearly in range".
     */
    private static final int TREE_SEARCH_HEIGHT = 12;
    /**
     * How far above/below the work area center {@link #findPlantableSpotInRange} looks for bare soil.
     * Deliberately much tighter than {@link #TREE_SEARCH_HEIGHT} - ground level shouldn't vary much
     * across a work area, and a generous height would routinely surface unrelated dirt/grass deep
     * underground or on a distant hill instead of the clearing right around the nursery.
     */
    private static final int PLANT_SEARCH_HEIGHT = 3;
    /** Minimum horizontal distance a new sapling must keep from any sapling already planted nearby. */
    private static final int MIN_SAPLING_SPACING = 2;

    private final UUID id;
    private String name;
    private StructureType type;
    private final BlockPos markerPos;
    private Set<BlockPos> roomBlocks;
    private int level = 1;
    /**
     * Residents currently assigned to this structure (a house), in assignment order. Same slot-gating
     * rule as {@link #assignedWorkerIds}: slot {@code i} is only ever gated by the {@code i}-th entry of
     * {@link #getInhabitantSlotUnlockLevels()}.
     */
    private final List<UUID> assignedResidentIds = new ArrayList<>();
    /**
     * Workers currently assigned to this structure (a workplace), in assignment order. Order matters:
     * slot {@code i} (0-indexed) is only ever gated by the {@code i}-th entry of {@link #getInhabitantSlotUnlockLevels()},
     * so the earliest-assigned worker keeps their (lower-level) slot even if a later slot's requirements
     * stop being met - see {@link #canWork}. A structure only ever uses one of {@link #assignedResidentIds}
     * or {@link #assignedWorkerIds} (a house doesn't have workers, a workplace doesn't have residents),
     * but both fields exist unconditionally so the class doesn't need a role-specific subtype.
     */
    private final List<UUID> assignedWorkerIds = new ArrayList<>();
    /** One entry per resident/worker slot index, cached lazily - see {@link #findBedPos(Level, int)}. */
    private final Map<Integer, BlockPos> cachedBedPosBySlot = new HashMap<>();
    private BlockPos cachedApproachPos;
    /** Offset from {@link #markerPos}; {@link BlockPos#ZERO} means the work area is centered on the marker. */
    private BlockPos workAreaOffset = BlockPos.ZERO;
    private int workAreaRadius = DEFAULT_WORK_AREA_RADIUS;
    /**
     * Issues reported by individual workers, keyed by worker UUID then by issue label.
     * Multiple workers reporting the same label → amounts are summed when producing requests.
     * Not persisted: jobs re-report their issues on the first tick after a reload.
     */
    private final Map<UUID, Map<String, WorkerIssue>> workerIssues = new HashMap<>();
    /**
     * Desired stock levels configured for this structure (item → target count). The structure
     * periodically compares its actual inventory against these and posts SOFT requests for shortfalls.
     * Starts from type defaults; the player can override per-structure.
     */
    private final Map<Item, Integer> desiredStock = new HashMap<>();
    /** Computed requests (URGENT from worker issues + SOFT from stock shortfalls), keyed by label. */
    private final Map<String, StructureRequest> requests = new HashMap<>();
    /**
     * Items this structure is willing to store, e.g. a storehouse's optional filter for what couriers
     * may deposit here. Empty means no restriction — everything is accepted.
     */
    private List<net.minecraft.world.item.ItemStack> filterTemplates = new ArrayList<>();
    private boolean filterWhitelist = true;
    private boolean filterMatchMetadata = false;
    /**
     * Miner-only toggle: when true, {@link com.sheepfromheaven.minenow.jobs.miner.MinerJob} places
     * cosmetic wood support beams along the tunnel/stairway it digs. Stored here (rather than on
     * {@link com.sheepfromheaven.minenow.structure.block.StructureMarkerBlockEntity}) to match the
     * existing convention for per-structure settings such as {@link #filterWhitelist} - that block
     * entity only ever mirrors display data copied *from* this class, it isn't the source of truth.
     */
    private boolean placeSupportBeams = false;

    /** What a single worker reports it is blocked on or wants proactively restocked. */
    public record WorkerIssue(
            @Nullable Item item,
            @Nullable ItemCategory category,
            String label,
            int amountPerWorker,
            StructureRequest.Priority priority
    ) {
        public static WorkerIssue urgent(String label) {
            return new WorkerIssue(null, null, label, 64, StructureRequest.Priority.URGENT);
        }

        public static WorkerIssue urgent(String label, int amount) {
            return new WorkerIssue(null, null, label, amount, StructureRequest.Priority.URGENT);
        }

        public static WorkerIssue urgentItem(Item item, int amount) {
            var name = item.getDefaultInstance().getHoverName().getString();
            return new WorkerIssue(item, null, name, amount, StructureRequest.Priority.URGENT);
        }

        public static WorkerIssue urgentCategory(String label, ItemCategory category) {
            return new WorkerIssue(null, category, label, 64, StructureRequest.Priority.URGENT);
        }
    }

    public DominionStructure(String name, StructureType type, BlockPos markerPos, Set<BlockPos> roomBlocks) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.type = type;
        this.markerPos = markerPos;
        this.roomBlocks = roomBlocks;
    }

    public DominionStructure(UUID id, String name, StructureType type, BlockPos markerPos, Set<BlockPos> roomBlocks,
                              List<UUID> assignedResidentIds, List<UUID> assignedWorkerIds,
                              BlockPos workAreaOffset, int workAreaRadius) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.markerPos = markerPos;
        this.roomBlocks = roomBlocks;
        this.assignedResidentIds.addAll(assignedResidentIds);
        this.assignedWorkerIds.addAll(assignedWorkerIds);
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

    public int getLevel() {
        return level;
    }

    /** For codec restore and {@link com.sheepfromheaven.minenow.dominion.Dominion#tryLevelUpStructure} only. */
    public void setLevel(int level) {
        this.level = level;
    }

    public boolean isMaxLevel() {
        return level >= type.getMaxLevel();
    }

    /**
     * Folds every {@link com.sheepfromheaven.minenow.effect.StructureEffect} this structure has earned
     * (currently: one set per level beyond 1, from {@link StructureType#getLevelEffects}) into a single
     * snapshot. Computed fresh each call rather than cached/persisted, so it can never go stale.
     */
    public StructureStats getStats() {
        var stats = new StructureStats();
        for (int lvl = 1; lvl <= level; lvl++) {
            for (var effect : type.getLevelEffects(lvl)) {
                effect.apply(this, stats);
            }
        }
        return stats;
    }

    /** How many inhabitants (workers or residents) this structure can support in total, per its level's earned {@link com.sheepfromheaven.minenow.effect.InhabitantSlotsEffect}s. */
    public int getInhabitantSlots() {
        return getStats().getInhabitantSlots();
    }

    /**
     * Multiplier applied to this structure's base work rate, growing with level. Jobs may consult this
     * (see {@link com.sheepfromheaven.minenow.jobs.Job#getProductivityMultiplier}) to speed up their own
     * pacing; not applied automatically.
     */
    public double getProductivityMultiplier() {
        return getStats().getProductivityMultiplier();
    }

    /**
     * Requirement statuses that must all be satisfied to advance from {@link #getLevel()} to the next
     * level. Empty once {@link #isMaxLevel()}. {@code dominion} may be null if this structure isn't
     * attached to one yet; requirements that need it (e.g. another structure at a given level) simply
     * report unmet in that case.
     */
    public List<RequirementStatus> checkLevelUpRequirements(Level level, @Nullable Dominion dominion) {
        if (isMaxLevel()) return List.of();
        var ctx = new RequirementContext(level, dominion, this);
        return type.getRequirements(this.level + 1).stream().map(r -> r.evaluate(ctx)).toList();
    }

    /** Whether every requirement for the next level is currently satisfied (ignoring the dominion's tier cap). */
    public boolean isReadyToLevelUp(Level level, @Nullable Dominion dominion) {
        return !isMaxLevel() && checkLevelUpRequirements(level, dominion).stream().allMatch(RequirementStatus::satisfied);
    }

    public BlockPos getMarkerPos() {
        return markerPos;
    }

    public Set<BlockPos> getRoomBlocks() {
        return roomBlocks;
    }

    public void setRoomBlocks(Set<BlockPos> roomBlocks) {
        this.roomBlocks = roomBlocks;
        this.cachedBedPosBySlot.clear();
    }

    public List<UUID> getAssignedResidentIds() {
        return Collections.unmodifiableList(assignedResidentIds);
    }

    public boolean hasOpenResidentSlot() {
        return assignedResidentIds.size() < getInhabitantSlots();
    }

    /** Adds a resident to the next open slot. Caller must check {@link #hasOpenResidentSlot()} first. */
    public void addResident(UUID residentId) {
        assignedResidentIds.add(residentId);
    }

    public void removeResident(UUID residentId) {
        assignedResidentIds.remove(residentId);
    }

    /**
     * Whether {@code residentId} currently has a functioning slot to live in - true only if they're
     * assigned here *and* their slot (by assignment order) is one of the currently-functioning ones.
     */
    public boolean canReside(UUID residentId, Level level, @Nullable Dominion dominion) {
        int slotIndex = assignedResidentIds.indexOf(residentId);
        return slotIndex >= 0 && slotIndex < getFunctioningInhabitantSlots(level, dominion);
    }

    public List<UUID> getAssignedWorkerIds() {
        return Collections.unmodifiableList(assignedWorkerIds);
    }

    public boolean hasOpenWorkSlot() {
        return assignedWorkerIds.size() < getInhabitantSlots();
    }

    /** Adds a worker to the next open slot. Caller must check {@link #hasOpenWorkSlot()} first. */
    public void addWorker(UUID workerId) {
        assignedWorkerIds.add(workerId);
    }

    public void removeWorker(UUID workerId) {
        assignedWorkerIds.remove(workerId);
    }

    /**
     * The level each inhabitant slot was unlocked at, in slot order: every {@link com.sheepfromheaven.minenow.effect.InhabitantSlotsEffect}
     * earned at any level from 1 up to the structure's current level - including level 1 itself, so a
     * type that wants more than one worker or resident from the start just grants more than one slot
     * there - adds one entry tagged with the level that granted it.
     */
    public List<Integer> getInhabitantSlotUnlockLevels() {
        var unlockLevels = new ArrayList<Integer>();
        for (int lvl = 1; lvl <= level; lvl++) {
            for (var effect : type.getLevelEffects(lvl)) {
                if (effect instanceof com.sheepfromheaven.minenow.effect.InhabitantSlotsEffect slotsEffect) {
                    for (int i = 0; i < slotsEffect.getAmount(); i++) {
                        unlockLevels.add(lvl);
                    }
                }
            }
        }
        return unlockLevels;
    }

    /**
     * How many of this structure's inhabitant slots are currently functioning - i.e. for how many of
     * {@link #getInhabitantSlotUnlockLevels()} the block requirements still hold as though the structure's
     * level were only that slot's unlock level. A slot unlocked at a higher level can stop functioning
     * (its blocks got removed) without affecting a lower-level slot that's still fully equipped.
     */
    public int getFunctioningInhabitantSlots(Level level, @Nullable Dominion dominion) {
        int count = 0;
        for (int unlockLevel : getInhabitantSlotUnlockLevels()) {
            if (isOperationalAt(unlockLevel, level, dominion)) count++;
        }
        return count;
    }

    /**
     * Whether {@code workerId} currently has a functioning slot to work in - true only if they're
     * assigned here *and* their slot (by assignment order) is one of the currently-functioning ones.
     */
    public boolean canWork(UUID workerId, Level level, @Nullable Dominion dominion) {
        int slotIndex = assignedWorkerIds.indexOf(workerId);
        return slotIndex >= 0 && slotIndex < getFunctioningInhabitantSlots(level, dominion);
    }

    public boolean containsBlock(BlockPos pos) {
        return roomBlocks.contains(pos);
    }

    /**
     * Returns a snapshot of every item currently held across all containers in the structure.
     *
     * @param level            the server level (needed to read block entities)
     * @param includeNonStorage if true, includes all {@link Container} block entities in the room
     *                          (e.g. smoker input/output slots); if false, only storage-tagged
     *                          containers (barrels, chests) are counted
     */
    public Map<Item, Integer> getInventory(Level level, boolean includeNonStorage) {
        var containers = includeNonStorage ? findContainers(level) : findStorageContainers(level);
        var totals = new HashMap<Item, Integer>();
        for (var pos : containers) {
            if (level.getBlockEntity(pos) instanceof Container container) {
                for (int i = 0; i < container.getContainerSize(); i++) {
                    var stack = container.getItem(i);
                    if (!stack.isEmpty()) {
                        totals.merge(stack.getItem(), stack.getCount(), Integer::sum);
                    }
                }
            }
        }
        return totals;
    }

    /**
     * Called by a worker to report it is blocked on or proactively wants a resource.
     * The structure aggregates across all workers and recomputes URGENT requests immediately.
     *
     * @param workerId the UUID of the reporting worker
     * @param issue    what it needs
     * @param gameTick current game tick (for request timestamps)
     */
    public void reportWorkerIssue(UUID workerId, WorkerIssue issue, long gameTick) {
        workerIssues.computeIfAbsent(workerId, k -> new HashMap<>()).put(issue.label(), issue);
        recomputeUrgentRequests(gameTick);
    }

    /**
     * Called by a worker when it is no longer blocked on a resource it previously reported.
     * Removes the issue and recomputes URGENT requests.
     */
    public void clearWorkerIssue(UUID workerId, String label, long gameTick) {
        var workerMap = workerIssues.get(workerId);
        if (workerMap != null) {
            workerMap.remove(label);
            if (workerMap.isEmpty()) workerIssues.remove(workerId);
        }
        recomputeUrgentRequests(gameTick);
    }

    /** Removes all issues for a worker (e.g. when they are unassigned). */
    public void clearAllWorkerIssues(UUID workerId, long gameTick) {
        workerIssues.remove(workerId);
        recomputeUrgentRequests(gameTick);
    }

    private void recomputeUrgentRequests(long gameTick) {
        // Drop all URGENT entries and rebuild from current worker issues
        requests.values().removeIf(r -> r.priority() == StructureRequest.Priority.URGENT);
        // Aggregate: sum amountPerWorker across all workers reporting the same label
        var aggregated = new LinkedHashMap<String, int[]>(); // label → [totalAmount]
        var itemByLabel = new HashMap<String, Item>();
        var categoryByLabel = new HashMap<String, ItemCategory>();
        for (var workerMap : workerIssues.values()) {
            for (var issue : workerMap.values()) {
                if (issue.priority() != StructureRequest.Priority.URGENT) continue;
                aggregated.computeIfAbsent(issue.label(), k -> new int[]{0})[0] += issue.amountPerWorker();
                if (issue.item() != null) itemByLabel.putIfAbsent(issue.label(), issue.item());
                if (issue.category() != null) categoryByLabel.putIfAbsent(issue.label(), issue.category());
            }
        }
        for (var entry : aggregated.entrySet()) {
            var label = entry.getKey();
            var amount = entry.getValue()[0];
            var item = itemByLabel.get(label);
            var category = categoryByLabel.get(label);
            requests.put(label, item != null
                    ? StructureRequest.forItem(id, item, amount, StructureRequest.Priority.URGENT, gameTick)
                    : StructureRequest.forCategory(id, label, category, amount, StructureRequest.Priority.URGENT, gameTick));
        }
    }

    /**
     * Sets the desired stock level for an item. The structure will post SOFT requests when the
     * actual inventory falls below this amount. Pass 0 to remove a desired-stock entry.
     */
    public void setDesiredStock(Item item, int amount) {
        if (amount <= 0) {
            desiredStock.remove(item);
        } else {
            desiredStock.put(item, amount);
        }
    }

    public Map<Item, Integer> getDesiredStock() {
        return Collections.unmodifiableMap(desiredStock);
    }

    /**
     * Compares actual storage inventory against {@link #desiredStock} and posts or clears SOFT
     * requests accordingly. Should be called periodically from a server tick (not every tick — once
     * every few seconds is enough).
     */
    public void tickStock(ServerLevel level, long gameTick) {
        if (desiredStock.isEmpty()) return;
        var inventory = getInventory(level, false);
        // Drop all SOFT entries and rebuild from current shortfalls
        requests.values().removeIf(r -> r.priority() == StructureRequest.Priority.SOFT);
        for (var entry : desiredStock.entrySet()) {
            var item = entry.getKey();
            var desired = entry.getValue();
            var actual = inventory.getOrDefault(item, 0);
            if (actual < desired) {
                var shortfall = desired - actual;
                requests.put(item.getDefaultInstance().getHoverName().getString(),
                        StructureRequest.forItem(id, item, shortfall, StructureRequest.Priority.SOFT, gameTick));
            }
        }
    }

    /** All active requests for this structure (URGENT + SOFT), keyed by label. Unmodifiable. */
    public Map<String, StructureRequest> getRequests() {
        return Collections.unmodifiableMap(requests);
    }

    /**
     * True if this structure will accept {@code stack} into storage - always true when no filter
     * templates are configured. Otherwise matches by item id (and, if {@link #filterMatchMetadata} is
     * on, exact components too) against {@link #filterTemplates}; whitelist mode accepts a match,
     * blacklist mode accepts a non-match.
     */
    public boolean accepts(net.minecraft.world.item.ItemStack stack) {
        if (filterTemplates.isEmpty()) return true;
        boolean matches = filterTemplates.stream().anyMatch(t -> matchesTemplate(stack, t));
        return filterWhitelist == matches;
    }

    /** Convenience overload for callers that only have an {@link Item}, matched against the default instance. */
    public boolean accepts(Item item) {
        return accepts(item.getDefaultInstance());
    }

    private boolean matchesTemplate(net.minecraft.world.item.ItemStack stack, net.minecraft.world.item.ItemStack template) {
        if (!stack.is(template.getItem())) return false;
        return !filterMatchMetadata || net.minecraft.world.item.ItemStack.isSameItemSameComponents(stack, template);
    }

    public List<net.minecraft.world.item.ItemStack> getFilterTemplates() {
        return Collections.unmodifiableList(filterTemplates);
    }

    public boolean isFilterWhitelist() {
        return filterWhitelist;
    }

    public boolean isFilterMatchMetadata() {
        return filterMatchMetadata;
    }

    public void setFilter(List<net.minecraft.world.item.ItemStack> templates, boolean whitelist, boolean matchMetadata) {
        this.filterTemplates = new ArrayList<>(templates);
        this.filterWhitelist = whitelist;
        this.filterMatchMetadata = matchMetadata;
    }

    public boolean isPlaceSupportBeams() {
        return placeSupportBeams;
    }

    public void setPlaceSupportBeams(boolean placeSupportBeams) {
        this.placeSupportBeams = placeSupportBeams;
    }

    // --- Codec helpers (used only by DominionSavedData to persist/restore the computed request map) ---

    /** For codec use only — directly inserts a previously-persisted request. */
    public void restoreRequest(StructureRequest req) {
        requests.put(req.label(), req);
    }

    /** The bed for resident slot 0 - see {@link #findBedPos(Level, int)}. */
    public BlockPos findBedPos(Level level) {
        return findBedPos(level, 0);
    }

    /**
     * The bed reserved for resident slot {@code slotIndex} (0-indexed), so each resident sleeps in - and
     * has a POI reserved on - their own bed rather than everyone piling into the first one found. Falls
     * back to the marker position if the room doesn't have that many distinct beds.
     */
    public BlockPos findBedPos(Level level, int slotIndex) {
        var cached = cachedBedPosBySlot.get(slotIndex);
        if (cached != null && level.getBlockState(cached).is(BlockTags.BEDS)) {
            return cached;
        }
        var beds = findAllBedPositions(level);
        if (slotIndex >= beds.size()) {
            return markerPos;
        }
        var bedPos = beds.get(slotIndex);
        cachedBedPosBySlot.put(slotIndex, bedPos);
        return bedPos;
    }

    /** Every distinct bed in the room, normalized to each bed's foot position, in scan order. */
    private List<BlockPos> findAllBedPositions(Level level) {
        var beds = new LinkedHashSet<BlockPos>();
        for (var bp : getInteriorAndWalls()) {
            var state = level.getBlockState(bp);
            if (state.is(BlockTags.BEDS)) {
                beds.add(normalizeToBedFoot(level, bp, state));
            }
        }
        return List.copyOf(beds);
    }

    /**
     * The sleeping-position render offset (both vanilla and geckolib) assumes the anchor block is the
     * bed's foot half, with the head extending toward the pillow in the block's facing direction. Both
     * bed halves carry the {@code BEDS} tag, so a scan can land on the head half first; move to the foot
     * half in that case rather than anchoring the sleeper on the wrong end of the bed.
     */
    private static BlockPos normalizeToBedFoot(Level level, BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        if (!(state.getBlock() instanceof net.minecraft.world.level.block.BedBlock)
                || state.getValue(net.minecraft.world.level.block.BedBlock.PART) != net.minecraft.world.level.block.state.properties.BedPart.HEAD) {
            return pos;
        }
        var footPos = pos.relative(state.getValue(net.minecraft.world.level.block.BedBlock.FACING).getOpposite());
        var footState = level.getBlockState(footPos);
        boolean isMatchingFoot = footState.getBlock() instanceof net.minecraft.world.level.block.BedBlock
                && footState.getValue(net.minecraft.world.level.block.BedBlock.PART) == net.minecraft.world.level.block.state.properties.BedPart.FOOT;
        return isMatchingFoot ? footPos : pos;
    }

    /**
     * The position inhabitants should walk to when heading to this workplace. Usually the marker
     * itself, but the marker can be placed embedded in a wall or ceiling (any block face is a valid
     * placement), in which case it's never a standable tile - so this searches outward for the
     * nearest standable floor tile instead, letting inhabitants "arrive" next to it.
     */
    public BlockPos findApproachPos(Level level) {
        if (cachedApproachPos != null && isStandable(level, cachedApproachPos)) {
            return cachedApproachPos;
        }
        if (isStandable(level, markerPos)) {
            cachedApproachPos = markerPos;
            return markerPos;
        }
        BlockPos best = null;
        long bestDistSq = Long.MAX_VALUE;
        for (int dx = -APPROACH_SEARCH_RADIUS; dx <= APPROACH_SEARCH_RADIUS; dx++) {
            for (int dy = -APPROACH_SEARCH_RADIUS; dy <= APPROACH_SEARCH_RADIUS; dy++) {
                for (int dz = -APPROACH_SEARCH_RADIUS; dz <= APPROACH_SEARCH_RADIUS; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    var candidate = markerPos.offset(dx, dy, dz);
                    if (!isStandable(level, candidate)) continue;
                    long distSq = (long) dx * dx + (long) dy * dy + (long) dz * dz;
                    if (distSq < bestDistSq) {
                        bestDistSq = distSq;
                        best = candidate;
                    }
                }
            }
        }
        cachedApproachPos = best != null ? best : markerPos;
        return cachedApproachPos;
    }

    /**
     * Returns the nearest standable floor position adjacent to {@code target} (typically a container
     * block), so an entity can stand beside it rather than trying to path into the solid block itself.
     * Checks the 4 horizontal neighbours first, then the block above and below. Falls back to
     * {@code target} itself if nothing adjacent is standable (caller must handle gracefully).
     */
    public static BlockPos findStandableAdjacentTo(Level level, BlockPos target) {
        BlockPos best = null;
        int bestDist = Integer.MAX_VALUE;
        int[][] offsets = { {1,0,0},{-1,0,0},{0,0,1},{0,0,-1},{0,1,0},{0,-1,0} };
        for (int[] off : offsets) {
            var candidate = target.offset(off[0], off[1], off[2]);
            if (isStandable(level, candidate)) {
                int dist = off[0]*off[0] + off[1]*off[1] + off[2]*off[2];
                if (dist < bestDist) { bestDist = dist; best = candidate; }
            }
        }
        return best != null ? best : target;
    }

    private static boolean isStandable(Level level, BlockPos pos) {
        if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
            return false;
        }
        if (!level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()) {
            return false;
        }
        var below = pos.below();
        return !level.getBlockState(below).getCollisionShape(level, below).isEmpty();
    }

    /**
     * Whether this structure currently holds up to the *block* requirements for {@link #getLevel()} -
     * not just what it took to originally build/level up to it. Re-evaluated fresh every call, so a
     * structure that loses a level-gating block stops counting as functional immediately, even though
     * {@link #getLevel()} doesn't change; the player has to restore what's missing rather than being
     * knocked back down a level. Room size is intentionally excluded here (see {@link #checkRequirements}) -
     * it's tracked for build/level-up progress, not as an ongoing operational gate.
     */
    public boolean isFullySatisfied(Level level, @Nullable Dominion dominion) {
        return checkOperationalRequirements(level, dominion).stream().allMatch(RequirementStatus::satisfied);
    }

    /**
     * The block requirements that gate whether this structure can actually be used right now (excludes
     * room size - see {@link #isFullySatisfied}). Backs {@link #isFullySatisfied} (checked by {@code
     * Dominion#assignHouse} up front, so a player can't move someone into an unfinished house) and
     * {@link #getFunctioningInhabitantSlots} (checked continuously, per slot, by {@link #canWork}/
     * {@link #canReside} - a worker can be assigned to an unfinished workplace, they just won't be able
     * to work until it's ready); a resident's missing-requirement complaint reads this list directly to
     * name what's missing, rather than the full display list from {@link #checkRequirements}.
     */
    public List<RequirementStatus> checkOperationalRequirements(Level level, @Nullable Dominion dominion) {
        return evaluate(operationalRequirementsAt(this.level), level, dominion);
    }

    /** Whether the block requirements hold as though this structure's level were only {@code upToLevel} - see {@link #getFunctioningInhabitantSlots}. */
    private boolean isOperationalAt(int upToLevel, Level level, @Nullable Dominion dominion) {
        return evaluate(operationalRequirementsAt(upToLevel), level, dominion).stream().allMatch(RequirementStatus::satisfied);
    }

    private List<Requirement> operationalRequirementsAt(int upToLevel) {
        return type.getRequirements(upToLevel).stream()
                .filter(r -> !(r instanceof RoomSizeRequirement))
                .toList();
    }

    /**
     * Every requirement (room size included) for {@link #getLevel()}, for display in the Requirements
     * tab - e.g. so a player can see they've dipped below the room size a level asked for, even though
     * {@link #isFullySatisfied} doesn't treat that as broken.
     */
    public List<RequirementStatus> checkRequirements(Level level, @Nullable Dominion dominion) {
        return evaluate(type.getRequirements(this.level), level, dominion);
    }

    private List<RequirementStatus> evaluate(List<Requirement> requirements, Level level, @Nullable Dominion dominion) {
        var ctx = new RequirementContext(level, dominion, this);
        return requirements.stream().map(r -> r.evaluate(ctx)).toList();
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
        for (var bp : getInteriorAndWalls()) {
            var state = level.getBlockState(bp);
            if (state.is(STORAGE_TAG) && level.getBlockEntity(bp) instanceof Container) {
                results.add(bp);
            }
        }
        return results;
    }

    /** First interior/wall block matching {@code matcher} (e.g. the tavern's smoker), if any. */
    public Optional<BlockPos> findBlock(Level level, Predicate<net.minecraft.world.level.block.state.BlockState> matcher) {
        for (var bp : getInteriorAndWalls()) {
            if (matcher.test(level.getBlockState(bp))) {
                return Optional.of(bp);
            }
        }
        return Optional.empty();
    }

    public List<BlockPos> findContainers(Level level) {
        var results = new ArrayList<BlockPos>();
        for (var bp : getInteriorAndWalls()) {
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
        return findTreeInRange(level, Set.of());
    }

    /**
     * Same as {@link #findTreeInRange(Level)}, but skips any trunk whose base is in {@code excludedBases}
     * (trees already known to have no reachable standing spot) so a bad tree doesn't permanently block
     * discovery of other, perfectly good trees in range.
     */
    public Optional<List<BlockPos>> findTreeInRange(Level level, Set<BlockPos> excludedBases) {
        return findTreeShapeInRange(level, excludedBases).map(TreeScanner.TreeShape::trunk);
    }

    /**
     * Same scan as {@link #findTreeInRange(Level, Set)}, but returns the full {@link TreeScanner.TreeShape}
     * (trunk and canopy leaves) instead of just the trunk - callers that need to harvest the canopy's
     * leaf drops (saplings, apples, sticks) alongside the logs use this instead.
     */
    public Optional<TreeScanner.TreeShape> findTreeShapeInRange(Level level, Set<BlockPos> excludedBases) {
        int radius = workAreaRadius;
        int height = TREE_SEARCH_HEIGHT;
        var center = getWorkAreaCenter();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) continue;
                for (int dy = -height; dy <= height; dy++) {
                    var pos = center.offset(dx, dy, dz);
                    if (excludedBases.contains(pos)) continue;
                    if (level.getBlockState(pos).is(BlockTags.LOGS)) {
                        var tree = TreeScanner.findTreeShape(level, pos);
                        if (tree.isPresent()) {
                            return tree;
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Scans the structure's work area for a bare patch of dirt/grass with open air above it, no
     * sapling already growing there, and no other sapling planted within
     * {@link #MIN_SAPLING_SPACING} blocks - a spot a forester can plant a new sapling on, spaced out
     * so the nursery doesn't end up as one solid row. Skips any position in {@code excluded} (spots
     * already known to be unreachable), mirroring {@link #findTreeShapeInRange(Level, Set)}. Picks
     * randomly among every valid candidate rather than always the first found, so plantings scatter
     * across the work area instead of filling it in scan order.
     */
    public Optional<BlockPos> findPlantableSpotInRange(Level level, Set<BlockPos> excluded) {
        int radius = workAreaRadius;
        var center = getWorkAreaCenter();
        var candidates = new ArrayList<BlockPos>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) continue;
                for (int dy = -PLANT_SEARCH_HEIGHT; dy <= PLANT_SEARCH_HEIGHT; dy++) {
                    var pos = center.offset(dx, dy, dz);
                    if (excluded.contains(pos)) continue;
                    if (!isSaplingSoil(level.getBlockState(pos))) continue;
                    var above = pos.above();
                    if (!level.getBlockState(above).is(BlockTags.SAPLINGS) && hasOpenAirAbove(level, pos, 1)
                            && !hasNearbySapling(level, pos)) {
                        candidates.add(pos);
                    }
                }
            }
        }
        if (candidates.isEmpty()) return Optional.empty();
        return Optional.of(candidates.get(level.getRandom().nextInt(candidates.size())));
    }

    /** True if a sapling is already planted within {@link #MIN_SAPLING_SPACING} blocks of {@code pos}. */
    private boolean hasNearbySapling(Level level, BlockPos pos) {
        for (int dx = -MIN_SAPLING_SPACING; dx <= MIN_SAPLING_SPACING; dx++) {
            for (int dz = -MIN_SAPLING_SPACING; dz <= MIN_SAPLING_SPACING; dz++) {
                if (dx == 0 && dz == 0) continue;
                if (dx * dx + dz * dz > MIN_SAPLING_SPACING * MIN_SAPLING_SPACING) continue;
                for (int dy = -1; dy <= 1; dy++) {
                    if (level.getBlockState(pos.offset(dx, dy, dz)).is(BlockTags.SAPLINGS)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Ground a sapling can be planted on - vanilla saplings require this same set (see SaplingBlock's mayPlaceOn), which isn't fully captured by {@link BlockTags#DIRT} (that tag excludes grass/mycelium/podzol). */
    private static boolean isSaplingSoil(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(BlockTags.DIRT)
                || state.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK)
                || state.is(net.minecraft.world.level.block.Blocks.PODZOL)
                || state.is(net.minecraft.world.level.block.Blocks.MYCELIUM)
                || state.is(net.minecraft.world.level.block.Blocks.FARMLAND);
    }

    public Optional<BlockPos> findRangeRequirementPosition(Level level, StructureType.Requirement requirement) {
        return findRangeRequirementPosition(level, requirement, Set.of());
    }

    /**
     * Same scan as {@link #findRangeRequirementPosition(Level, StructureType.Requirement)}, but skips
     * positions in {@code excluded} - lets a caller that already tried a position and found it otherwise
     * unusable (e.g. a water tile with no reachable shore) ask for the next-best match instead of getting
     * the same one back forever.
     */
    public Optional<BlockPos> findRangeRequirementPosition(Level level, StructureType.Requirement requirement, Set<BlockPos> excluded) {
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
                    if (excluded.contains(pos)) continue;
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

    /** Interior room blocks plus their immediate neighbors - the search space for job blocks and level-up requirements. */
    public Set<BlockPos> getInteriorAndWalls() {
        var all = new HashSet<>(roomBlocks);
        for (var bp : roomBlocks) {
            for (var dir : Direction.values()) {
                all.add(bp.relative(dir));
            }
        }
        return all;
    }
}
