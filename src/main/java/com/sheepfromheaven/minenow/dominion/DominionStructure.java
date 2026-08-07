package com.sheepfromheaven.minenow.dominion;

import com.sheepfromheaven.minenow.entity.job.TreeScanner;
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

    private final UUID id;
    private String name;
    private StructureType type;
    private final BlockPos markerPos;
    private Set<BlockPos> roomBlocks;
    private UUID assignedInhabitantId;
    private UUID assignedWorkerId;
    private BlockPos cachedBedPos;
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
    private final Set<Item> itemFilter = new HashSet<>();

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

    /** True if this structure will accept {@code item} into storage - always true when no filter is set. */
    public boolean accepts(Item item) {
        return itemFilter.isEmpty() || itemFilter.contains(item);
    }

    public Set<Item> getItemFilter() {
        return Collections.unmodifiableSet(itemFilter);
    }

    public void setItemFilter(Set<Item> items) {
        itemFilter.clear();
        itemFilter.addAll(items);
    }

    // --- Codec helpers (used only by DominionSavedData to persist/restore the computed request map) ---

    /** For codec use only — directly inserts a previously-persisted request. */
    void restoreRequest(StructureRequest req) {
        requests.put(req.label(), req);
    }

    public BlockPos findBedPos(Level level) {
        if (cachedBedPos != null && level.getBlockState(cachedBedPos).is(BlockTags.BEDS)) {
            return cachedBedPos;
        }
        for (var bp : collectInteriorAndWalls()) {
            var state = level.getBlockState(bp);
            if (state.is(BlockTags.BEDS)) {
                cachedBedPos = normalizeToBedFoot(level, bp, state);
                return cachedBedPos;
            }
        }
        return markerPos;
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

    public RequirementStatus getSpaceStatus() {
        return new RequirementStatus("Space", type.getMinSize(), roomBlocks.size());
    }

    public boolean isFullySatisfied(Level level) {
        return checkRequirements(level).stream().allMatch(RequirementStatus::satisfied);
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

    /** First interior/wall block matching {@code matcher} (e.g. the tavern's smoker), if any. */
    public Optional<BlockPos> findBlock(Level level, Predicate<net.minecraft.world.level.block.state.BlockState> matcher) {
        for (var bp : collectInteriorAndWalls()) {
            if (matcher.test(level.getBlockState(bp))) {
                return Optional.of(bp);
            }
        }
        return Optional.empty();
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
