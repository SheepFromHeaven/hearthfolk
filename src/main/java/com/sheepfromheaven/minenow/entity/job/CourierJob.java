package com.sheepfromheaven.minenow.entity.job;

import com.sheepfromheaven.minenow.dominion.Dominion;
import com.sheepfromheaven.minenow.dominion.DominionSavedData;
import com.sheepfromheaven.minenow.dominion.DominionStructure;
import com.sheepfromheaven.minenow.dominion.StructureRequest;
import com.sheepfromheaven.minenow.entity.InhabitantEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Courier job: checks the dominion's pending item requests, picks up a matching item from any
 * structure that has it, and delivers it to the requesting structure's storage.
 *
 * Phases: GOTO_WORKPLACE → FIND_REQUEST → GOTO_SOURCE → GOTO_DEST → IDLE (brief pause when nothing to do)
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

    @Nullable private BlockPos sourcePos;
    @Nullable private BlockPos destPos;
    @Nullable private UUID destStructureId;
    @Nullable private Item requestedItem;
    @Nullable private String requestLabel;

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
        phase = Phase.GOTO_WORKPLACE;
        sourcePos = null;
        destPos = null;
        destStructureId = null;
        requestedItem = null;
        requestLabel = null;
    }

    @Override
    public boolean tick(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        return switch (phase) {
            case GOTO_WORKPLACE -> tickGotoWorkplace(inhabitant, level, workplace);
            case FIND_REQUEST  -> tickFindRequest(inhabitant, level, workplace);
            case GOTO_SOURCE   -> tickGotoSource(inhabitant, level, workplace);
            case GOTO_DEST     -> tickGotoDest(inhabitant, level, workplace);
            case IDLE          -> tickIdle();
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
            phase = Phase.IDLE;
            idleTicks = IDLE_WAIT_TICKS;
            return true;
        }

        for (StructureRequest req : dominion.getPendingRequests()) {
            if (req.item() == null) continue; // skip open-ended category requests
            Item item = req.item();

            BlockPos src = null;
            for (DominionStructure structure : dominion.getStructures()) {
                if (structure.getId().equals(req.structureId())) continue; // don't pull from the requester itself
                src = ContainerUtil.findContainerWith(level, structure.findStorageContainers(level), s -> s.is(item));
                if (src != null) break;
            }
            if (src == null) continue;

            DominionStructure dest = dominion.getStructureById(req.structureId());
            if (dest == null) continue;
            BlockPos dp = ContainerUtil.findContainerAccepting(level, dest.findStorageContainers(level), item.getDefaultInstance());
            if (dp == null) continue;

            sourcePos = src;
            destPos = dp;
            destStructureId = req.structureId();
            requestedItem = item;
            requestLabel = req.label();
            phase = Phase.GOTO_SOURCE;
            return true;
        }

        phase = Phase.IDLE;
        idleTicks = IDLE_WAIT_TICKS;
        return true;
    }

    private boolean tickGotoSource(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        if (sourcePos == null) {
            resetRequest();
            return true;
        }
        if (!moveTowardsAndCheckArrival(inhabitant, sourcePos)) return true;
        if (!actionPacer.isReady(sourcePos)) return true;

        lookAtBlock(inhabitant, sourcePos);
        if (level.getBlockEntity(sourcePos) instanceof Container container && requestedItem != null) {
            ItemStack extracted = ContainerUtil.extractOne(container, inhabitant, s -> s.is(requestedItem));
            if (!extracted.isEmpty()) {
                inhabitant.getInventory().addItem(extracted);
                actionPacer.recordAction(sourcePos);
                phase = Phase.GOTO_DEST;
                return true;
            }
        }
        // item was gone by the time we arrived; start over
        resetRequest();
        return true;
    }

    private boolean tickGotoDest(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        // Re-locate dest pos in case the container moved or request changed
        if (destStructureId != null && destPos == null) {
            Dominion dominion = DominionSavedData.get(level).findDominionContaining(workplace.getMarkerPos());
            if (dominion != null) {
                DominionStructure dest = dominion.getStructureById(destStructureId);
                if (dest != null) {
                    var carried = inhabitant.getInventory();
                    for (int i = 0; i < carried.getContainerSize(); i++) {
                        var stack = carried.getItem(i);
                        if (!stack.isEmpty()) {
                            destPos = ContainerUtil.findContainerAccepting(level, dest.findStorageContainers(level), stack);
                            break;
                        }
                    }
                }
            }
        }

        if (destPos == null) {
            depositCarriedItems(inhabitant, level, workplace);
            resetRequest();
            return true;
        }

        if (!moveTowardsAndCheckArrival(inhabitant, destPos)) return true;
        if (!actionPacer.isReady(destPos)) return true;

        lookAtBlock(inhabitant, destPos);
        if (level.getBlockEntity(destPos) instanceof Container container) {
            var carried = inhabitant.getInventory();
            for (int i = 0; i < carried.getContainerSize(); i++) {
                var stack = carried.getItem(i);
                if (stack.isEmpty()) continue;
                carried.setItem(i, ContainerUtil.insertItem(container, inhabitant, stack));
                actionPacer.recordAction(destPos);
                break;
            }
        }

        boolean stillCarrying = false;
        var carried = inhabitant.getInventory();
        for (int i = 0; i < carried.getContainerSize(); i++) {
            if (!carried.getItem(i).isEmpty()) { stillCarrying = true; break; }
        }

        if (!stillCarrying) {
            resetRequest();
            return false; // cycle complete
        }
        destPos = null; // find a new slot next tick
        return true;
    }

    private boolean tickIdle() {
        if (--idleTicks <= 0) {
            phase = Phase.GOTO_WORKPLACE;
        }
        return true;
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

    private void resetRequest() {
        sourcePos = null;
        destPos = null;
        destStructureId = null;
        requestedItem = null;
        requestLabel = null;
        phase = Phase.GOTO_WORKPLACE;
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
            case GOTO_SOURCE    -> requestLabel != null ? "Picking up " + requestLabel : "Picking up item";
            case GOTO_DEST      -> requestLabel != null ? "Delivering " + requestLabel : "Delivering item";
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
        actionPacer.save(tag, "Action");
        if (sourcePos != null) tag.putLong("SourcePos", sourcePos.asLong());
        if (destPos != null) tag.putLong("DestPos", destPos.asLong());
        if (destStructureId != null) tag.putString("DestStructureId", destStructureId.toString());
        if (requestedItem != null)
            tag.putString("RequestedItem", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(requestedItem).toString());
        if (requestLabel != null) tag.putString("RequestLabel", requestLabel);
    }

    @Override
    public void load(CompoundTag tag) {
        try {
            phase = Phase.valueOf(tag.getStringOr("Phase", Phase.GOTO_WORKPLACE.name()));
        } catch (IllegalArgumentException e) {
            phase = Phase.GOTO_WORKPLACE;
        }
        idleTicks = tag.getIntOr("IdleTicks", 0);
        actionPacer.load(tag, "Action");
        sourcePos = tag.contains("SourcePos") ? BlockPos.of(tag.getLongOr("SourcePos", 0)) : null;
        destPos = tag.contains("DestPos") ? BlockPos.of(tag.getLongOr("DestPos", 0)) : null;
        destStructureId = tag.contains("DestStructureId") ? UUID.fromString(tag.getStringOr("DestStructureId", "")) : null;
        if (tag.contains("RequestedItem")) {
            var key = net.minecraft.resources.Identifier.parse(tag.getStringOr("RequestedItem", ""));
            requestedItem = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(key);
        }
        requestLabel = tag.contains("RequestLabel") ? tag.getStringOr("RequestLabel", "") : null;
    }
}
