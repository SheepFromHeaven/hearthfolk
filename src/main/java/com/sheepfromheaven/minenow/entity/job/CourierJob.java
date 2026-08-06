package com.sheepfromheaven.minenow.entity.job;

import com.sheepfromheaven.minenow.dominion.Dominion;
import com.sheepfromheaven.minenow.dominion.DominionSavedData;
import com.sheepfromheaven.minenow.dominion.DominionStructure;
import com.sheepfromheaven.minenow.dominion.ItemCategory;
import com.sheepfromheaven.minenow.dominion.StructureRequest;
import com.sheepfromheaven.minenow.dominion.StructureType;
import com.sheepfromheaven.minenow.entity.InhabitantEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Courier job: checks the dominion's pending item requests, loads up to one stack per inventory
 * slot from their source containers, then delivers each stack to the requesting structure's storage.
 *
 * Phases: GOTO_WORKPLACE → FIND_REQUEST → GOTO_SOURCE (repeated per task) → GOTO_DEST → IDLE
 */
public class CourierJob implements Job {
    private static final int ARRIVE_DISTANCE_SQ = 4;
    private static final int REPATH_INTERVAL = 60;
    private static final double WALK_SPEED = 0.6;
    private static final int IDLE_WAIT_TICKS = 100;

    private enum Phase { GOTO_WORKPLACE, FIND_REQUEST, GOTO_SOURCE, GOTO_DEST, IDLE }

    private Phase phase = Phase.GOTO_WORKPLACE;
    private int repathCooldown;
    private int idleTicks;
    private final ContainerActionPacer actionPacer = new ContainerActionPacer();

    // Parallel lists — index i describes one delivery task.
    private final List<Item> taskItems = new ArrayList<>();
    private final List<BlockPos> taskSources = new ArrayList<>();
    private final List<BlockPos> taskSourceNavTargets = new ArrayList<>(); // standable pos beside the source container
    private final List<UUID> taskDests = new ArrayList<>();
    // Which task we are currently walking to the source for.
    private int taskIndex = 0;

    // Re-resolved each delivery leg in GOTO_DEST.
    @Nullable private BlockPos destPos;
    @Nullable private BlockPos destNavTarget; // standable pos beside the dest container

    @Override
    public boolean canStart(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        return true;
    }

    @Override
    public void start(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {}

    @Override
    public void stop(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        inhabitant.getNavigation().stop();
        DominionSavedData.get(level).setDirty();
    }

    @Override
    public void onInterrupted(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        depositCarriedItems(inhabitant, level, workplace);
        resetTasks();
    }

    @Override
    public boolean tick(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        return switch (phase) {
            case GOTO_WORKPLACE -> tickGotoWorkplace(inhabitant, level, workplace);
            case FIND_REQUEST   -> tickFindRequest(inhabitant, level, workplace);
            case GOTO_SOURCE    -> tickGotoSource(inhabitant, level, workplace);
            case GOTO_DEST      -> tickGotoDest(inhabitant, level, workplace);
            case IDLE           -> tickIdle();
        };
    }

    private boolean tickGotoWorkplace(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        if (moveTowardsAndCheckArrival(inhabitant, workplace.findApproachPos(level))) {
            phase = Phase.FIND_REQUEST;
        }
        return true;
    }

    private boolean tickFindRequest(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        Dominion dominion = DominionSavedData.get(level).findDominionContaining(workplace.getMarkerPos());
        if (dominion == null) {
            goIdle();
            return true;
        }

        taskItems.clear();
        taskSources.clear();
        taskDests.clear();

        int slots = inhabitant.getInventory().getContainerSize();

        for (StructureRequest req : dominion.getPendingRequests()) {
            if (taskItems.size() >= slots) break;

            DominionStructure dest = dominion.getStructureById(req.structureId());
            if (dest == null) continue;

            Item item = req.item();
            if (item == null) {
                if (req.category() == null) continue;
                item = findItemForCategory(dominion, level, dest, req.category());
                if (item == null) continue;
            }

            // Skip if we already planned a pickup of this item (one task per item type per trip).
            final Item finalItem = item;
            if (taskItems.stream().anyMatch(t -> t == finalItem)) continue;

            BlockPos src = findSource(dominion, level, dest, item);
            if (src == null) continue;

            BlockPos dp = ContainerUtil.findContainerAccepting(level, dest.findStorageContainers(level), item.getDefaultInstance());
            if (dp == null) continue;

            taskItems.add(item);
            taskSources.add(src);
            taskSourceNavTargets.add(DominionStructure.findStandableAdjacentTo(level, src));
            taskDests.add(req.structureId());
        }

        if (taskItems.isEmpty()) {
            goIdle();
            return true;
        }

        taskIndex = 0;
        phase = Phase.GOTO_SOURCE;
        return true;
    }

    private boolean tickGotoSource(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        // Advance past tasks already picked up (item already in inventory).
        while (taskIndex < taskItems.size()) {
            Item needed = taskItems.get(taskIndex);
            boolean alreadyCarrying = false;
            var inv = inhabitant.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                if (inv.getItem(i).is(needed)) { alreadyCarrying = true; break; }
            }
            if (!alreadyCarrying) break;
            taskIndex++;
        }

        if (taskIndex >= taskItems.size()) {
            destPos = null;
            phase = Phase.GOTO_DEST;
            return true;
        }

        BlockPos src = taskSources.get(taskIndex);
        BlockPos srcNav = taskSourceNavTargets.get(taskIndex);
        Item item = taskItems.get(taskIndex);

        if (!moveTowardsAndCheckArrival(inhabitant, srcNav)) return true;
        if (!actionPacer.isReady(src)) return true;

        lookAtBlock(inhabitant, src);
        if (level.getBlockEntity(src) instanceof Container container) {
            ItemStack extracted = ContainerUtil.extractStack(container, inhabitant, item);
            if (!extracted.isEmpty()) {
                inhabitant.getInventory().addItem(extracted);
                actionPacer.recordAction(src);
                taskIndex++;
                return true;
            }
        }
        // Item gone — drop this task, try next.
        taskItems.remove(taskIndex);
        taskSources.remove(taskIndex);
        taskSourceNavTargets.remove(taskIndex);
        taskDests.remove(taskIndex);
        // taskIndex stays, now points to the next task.
        return true;
    }

    private boolean tickGotoDest(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        var carried = inhabitant.getInventory();

        // Find the first non-empty slot.
        int slotToDeliver = -1;
        for (int i = 0; i < carried.getContainerSize(); i++) {
            if (!carried.getItem(i).isEmpty()) { slotToDeliver = i; break; }
        }

        if (slotToDeliver == -1) {
            // All delivered.
            resetTasks();
            return false;
        }

        ItemStack stack = carried.getItem(slotToDeliver);

        // Resolve dest container if not already done.
        if (destPos == null) {
            UUID destId = findDestForItem(stack.getItem());
            if (destId != null) {
                Dominion dominion = DominionSavedData.get(level).findDominionContaining(workplace.getMarkerPos());
                if (dominion != null) {
                    DominionStructure dest = dominion.getStructureById(destId);
                    if (dest != null) {
                        destPos = ContainerUtil.findContainerAccepting(level, dest.findStorageContainers(level), stack);
                        if (destPos != null) {
                            destNavTarget = DominionStructure.findStandableAdjacentTo(level, destPos);
                        }
                    }
                }
            }
            if (destPos == null) {
                // Nowhere to deliver — dump back at workplace.
                depositCarriedItems(inhabitant, level, workplace);
                resetTasks();
                return true;
            }
        }

        if (!moveTowardsAndCheckArrival(inhabitant, destNavTarget != null ? destNavTarget : destPos)) return true;
        if (!actionPacer.isReady(destPos)) return true;

        lookAtBlock(inhabitant, destPos);
        if (level.getBlockEntity(destPos) instanceof Container container) {
            carried.setItem(slotToDeliver, ContainerUtil.insertItem(container, inhabitant, stack));
            actionPacer.recordAction(destPos);
        }

        destPos = null; // Re-resolve next tick in case the slot filled up.
        destNavTarget = null;
        return true;
    }

    /** Returns the planned dest structure id for the given item, or null if unrecognised. */
    @Nullable
    private UUID findDestForItem(Item item) {
        for (int i = 0; i < taskItems.size(); i++) {
            if (taskItems.get(i) == item) return taskDests.get(i);
        }
        return null;
    }

    private boolean tickIdle() {
        if (--idleTicks <= 0) {
            phase = Phase.GOTO_WORKPLACE;
        }
        return true;
    }

    private void goIdle() {
        phase = Phase.IDLE;
        idleTicks = IDLE_WAIT_TICKS;
    }

    private void resetTasks() {
        taskItems.clear();
        taskSources.clear();
        taskSourceNavTargets.clear();
        taskDests.clear();
        taskIndex = 0;
        destPos = null;
        destNavTarget = null;
        phase = Phase.GOTO_WORKPLACE;
    }

    /** Dump anything the courier is carrying back into the workplace storage. */
    private void depositCarriedItems(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        var carried = inhabitant.getInventory();
        for (int i = 0; i < carried.getContainerSize(); i++) {
            var stack = carried.getItem(i);
            if (stack.isEmpty()) continue;
            BlockPos pos = ContainerUtil.findContainerAccepting(level, workplace.findStorageContainers(level), stack);
            if (pos != null && level.getBlockEntity(pos) instanceof Container container) {
                carried.setItem(i, ContainerUtil.insertItem(container, inhabitant, stack));
            }
        }
    }

    /**
     * Scans all storehouses in the dominion for any item matching {@code category} that could be
     * delivered to {@code dest}, and returns the first concrete item found. Returns null if nothing
     * matching is in stock.
     */
    @Nullable
    public static Item findItemForCategory(Dominion dominion, ServerLevel level, DominionStructure dest, ItemCategory category) {
        for (DominionStructure structure : dominion.getStructures()) {
            if (structure.getId().equals(dest.getId())) continue;
            for (BlockPos pos : structure.findStorageContainers(level)) {
                if (level.getBlockEntity(pos) instanceof Container container) {
                    for (int i = 0; i < container.getContainerSize(); i++) {
                        var stack = container.getItem(i);
                        if (category.matches(stack, level)) return stack.getItem();
                    }
                }
            }
        }
        return null;
    }

    /**
     * Picks the container to fetch {@code item} from for a delivery to {@code dest}: prefers the
     * nearest storehouse (by marker distance to {@code dest}) that both stocks the item and accepts it
     * per its item filter, falling back to any other structure currently holding the item.
     */
    public static BlockPos findSource(Dominion dominion, ServerLevel level, DominionStructure dest, Item item) {
        var storehouse = dominion.findNearestStructureOfType(StructureType.STOREHOUSE, dest.getMarkerPos(),
                s -> !s.getId().equals(dest.getId()) && s.accepts(item)
                        && ContainerUtil.findContainerWith(level, s.findStorageContainers(level), st -> st.is(item)) != null);
        if (storehouse.isPresent()) {
            return ContainerUtil.findContainerWith(level, storehouse.get().findStorageContainers(level), st -> st.is(item));
        }

        for (DominionStructure structure : dominion.getStructures()) {
            if (structure.getId().equals(dest.getId())) continue;
            BlockPos src = ContainerUtil.findContainerWith(level, structure.findStorageContainers(level), st -> st.is(item));
            if (src != null) return src;
        }
        return null;
    }

    private boolean moveTowardsAndCheckArrival(InhabitantEntity inhabitant, BlockPos target) {
        boolean arrived = inhabitant.distanceToSqr(target.getX() + 0.5, target.getY(), target.getZ() + 0.5) <= ARRIVE_DISTANCE_SQ;
        if (arrived) {
            inhabitant.getNavigation().stop();
            return true;
        }
        if (inhabitant.getNavigation().isDone() || --repathCooldown <= 0) {
            inhabitant.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, WALK_SPEED);
            repathCooldown = REPATH_INTERVAL;
        }
        return false;
    }

    @Override
    public String getStatusDescription() {
        return switch (phase) {
            case GOTO_WORKPLACE -> "Heading to storehouse";
            case FIND_REQUEST   -> "Looking for deliveries";
            case GOTO_SOURCE    -> taskIndex < taskItems.size()
                    ? "Picking up " + taskItems.get(taskIndex).getDefaultInstance().getHoverName().getString()
                    : "Picking up items";
            case GOTO_DEST      -> "Delivering items";
            case IDLE           -> "Waiting for requests";
        };
    }

    @Override
    @Nullable
    public Item getMissingItem() {
        return null;
    }

    @Override
    public void save(CompoundTag tag) {
        tag.putString("Phase", phase.name());
        tag.putInt("IdleTicks", idleTicks);
        tag.putInt("TaskIndex", taskIndex);
        actionPacer.save(tag, "Action");
        if (destPos != null) tag.putLong("DestPos", destPos.asLong());

        var itemsTag = new ListTag();
        var sourcesTag = new ListTag();
        var destsTag = new ListTag();
        for (int i = 0; i < taskItems.size(); i++) {
            var entry = new CompoundTag();
            entry.putString("item", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(taskItems.get(i)).toString());
            itemsTag.add(entry);

            var src = new CompoundTag();
            src.putLong("pos", taskSources.get(i).asLong());
            src.putLong("navPos", taskSourceNavTargets.get(i).asLong());
            sourcesTag.add(src);

            var dest = new CompoundTag();
            dest.putString("id", taskDests.get(i).toString());
            destsTag.add(dest);
        }
        tag.put("TaskItems", itemsTag);
        tag.put("TaskSources", sourcesTag);
        tag.put("TaskDests", destsTag);
    }

    @Override
    public void load(CompoundTag tag) {
        try {
            phase = Phase.valueOf(tag.getStringOr("Phase", Phase.GOTO_WORKPLACE.name()));
        } catch (IllegalArgumentException e) {
            phase = Phase.GOTO_WORKPLACE;
        }
        idleTicks = tag.getIntOr("IdleTicks", 0);
        taskIndex = tag.getIntOr("TaskIndex", 0);
        actionPacer.load(tag, "Action");
        destPos = tag.contains("DestPos") ? BlockPos.of(tag.getLongOr("DestPos", 0)) : null;

        taskItems.clear();
        taskSources.clear();
        taskDests.clear();
        var itemsTag = tag.getListOrEmpty("TaskItems");
        var sourcesTag = tag.getListOrEmpty("TaskSources");
        var destsTag = tag.getListOrEmpty("TaskDests");
        for (int i = 0; i < itemsTag.size(); i++) {
            var key = net.minecraft.resources.Identifier.parse(((CompoundTag) itemsTag.get(i)).getStringOr("item", ""));
            var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(key);
            taskItems.add(item);
            var srcTag = (CompoundTag) sourcesTag.get(i);
            var srcPos = BlockPos.of(srcTag.getLongOr("pos", 0));
            taskSources.add(srcPos);
            // navPos may be absent in saves from before this field was added; fall back to srcPos.
            taskSourceNavTargets.add(srcTag.contains("navPos") ? BlockPos.of(srcTag.getLongOr("navPos", 0)) : srcPos);
            taskDests.add(UUID.fromString(((CompoundTag) destsTag.get(i)).getStringOr("id", "")));
        }
    }
}
