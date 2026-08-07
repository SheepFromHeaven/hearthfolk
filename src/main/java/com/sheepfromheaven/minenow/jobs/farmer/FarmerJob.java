package com.sheepfromheaven.minenow.jobs.farmer;

import com.sheepfromheaven.minenow.dominion.DominionSavedData;
import com.sheepfromheaven.minenow.inhabitant.InhabitantEntity;
import com.sheepfromheaven.minenow.jobs.ContainerActionPacer;
import com.sheepfromheaven.minenow.jobs.ContainerUtil;
import com.sheepfromheaven.minenow.jobs.Job;
import com.sheepfromheaven.minenow.structure.DominionStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.Block;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Goes to the farm, finds a mature vanilla crop within the structure's work area (see
 * {@link CropScanner}), harvests it, immediately replants it from a seed taken out of its own
 * inventory (drops from the harvest itself, or restocked from the farm's storage), and deposits
 * everything else it's carrying back at the hut before heading home at night.
 */
public class FarmerJob implements Job {
    private static final int ARRIVE_DISTANCE_SQ = 4;
    private static final int REPATH_INTERVAL = 60;
    private static final double WALK_SPEED = 0.6;
    /** ~0.5s at 20 TPS - fast, but the harvest still reads as a distinct visible action. */
    private static final int HARVEST_TICKS = 10;
    private static final int NO_CROP_RETRY_TICKS = 100;

    private enum Phase { GOTO_HUT, GOTO_FIELD, HARVESTING, RETURN_TO_HUT, DEPOSIT }

    private Phase phase = Phase.GOTO_HUT;
    @Nullable
    private BlockPos targetCrop;
    private int waitTicks;
    private int repathCooldown;
    private int noCropCooldown;
    private final ContainerActionPacer actionPacer = new ContainerActionPacer();
    @Nullable
    private BlockPos containerTarget;
    /** Crop positions found to have no valid replant this cycle, so they're skipped in future scans until harvested. */
    private final Set<BlockPos> skippedCrops = new HashSet<>();

    @Override
    public boolean canStart(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        return isDaytime(level) || phase != Phase.GOTO_HUT;
    }

    @Override
    public void start(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        // Resume wherever the last cycle left off; state persists across interruptions.
    }

    @Override
    public void stop(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        inhabitant.getNavigation().stop();
    }

    @Override
    public void onInterrupted(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        phase = Phase.GOTO_HUT;
        containerTarget = null;
        targetCrop = null;
    }

    @Override
    public boolean tick(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        return switch (phase) {
            case GOTO_HUT -> tickGotoHut(inhabitant, level, workplace);
            case GOTO_FIELD -> tickGotoField(inhabitant, level, workplace);
            case HARVESTING -> tickHarvesting(inhabitant, level, workplace);
            case RETURN_TO_HUT -> tickReturnToHut(inhabitant, level, workplace);
            case DEPOSIT -> tickDeposit(inhabitant, level, workplace);
        };
    }

    private boolean tickGotoHut(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        if (moveTowardsAndCheckArrival(inhabitant, workplace.findApproachPos(level))) {
            phase = Phase.GOTO_FIELD;
        }
        return true;
    }

    private boolean tickGotoField(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        if (targetCrop == null) {
            if (--noCropCooldown > 0) {
                return true;
            }
            var dominion = DominionSavedData.get(level).getDominionById(inhabitant.getDominionId());
            var fields = dominion != null ? dominion.getFieldsAssignedTo(workplace.getId()) : java.util.List.<com.sheepfromheaven.minenow.structure.field.Field>of();
            var found = fields.isEmpty()
                    ? CropScanner.findMatureCropInRange(level, workplace.getWorkAreaCenter(), workplace.getWorkAreaRadius(), workplace.getWorkAreaRadius(), skippedCrops).orElse(null)
                    : CropScanner.findMatureCropInFields(level, fields, skippedCrops).orElse(null);
            if (found == null) {
                skippedCrops.clear();
                noCropCooldown = NO_CROP_RETRY_TICKS;
                inhabitant.playGoalSwitchSound();
                return true;
            }
            targetCrop = found;
        }
        if (moveTowardsAndCheckArrival(inhabitant, targetCrop)) {
            inhabitant.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            phase = Phase.HARVESTING;
            waitTicks = HARVEST_TICKS;
        }
        return true;
    }

    private boolean tickHarvesting(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        if (targetCrop == null) {
            phase = Phase.RETURN_TO_HUT;
            return true;
        }
        lookAtBlock(inhabitant, targetCrop);
        if (--waitTicks > 0) {
            return true;
        }

        var pos = targetCrop;
        var state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof net.minecraft.world.level.block.CropBlock cropBlock) || !cropBlock.isMaxAge(state)) {
            // Already harvested by a player or another worker since the scan - just move on.
            targetCrop = null;
            phase = Phase.RETURN_TO_HUT;
            return true;
        }

        var soundType = state.getSoundType();
        level.playSound(null, pos, soundType.getBreakSound(), net.minecraft.sounds.SoundSource.BLOCKS,
                soundType.getVolume(), soundType.getPitch());
        for (var drop : Block.getDrops(state, level, pos, level.getBlockEntity(pos), inhabitant, net.minecraft.world.item.ItemStack.EMPTY)) {
            inhabitant.getInventory().addItem(drop);
        }
        level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());

        var seedItem = CropScanner.seedItemFor(state);
        if (seedItem != null) {
            var seed = ContainerUtil.extractOne(inhabitant.getInventory(), inhabitant, stack -> stack.is(seedItem));
            if (!seed.isEmpty()) {
                level.setBlockAndUpdate(pos, cropBlock.defaultBlockState());
            } else {
                // No seed on hand to replant with right now - leave the farmland bare and skip this
                // tile on future scans until it's been walked past again (cleared with skippedCrops).
                skippedCrops.add(pos);
            }
        }

        targetCrop = null;
        phase = Phase.RETURN_TO_HUT;
        return true;
    }

    private boolean tickReturnToHut(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        if (moveTowardsAndCheckArrival(inhabitant, workplace.findApproachPos(level))) {
            phase = Phase.DEPOSIT;
        }
        return true;
    }

    private boolean tickDeposit(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        var carried = inhabitant.getInventory();
        for (int i = 0; i < carried.getContainerSize(); i++) {
            var stack = carried.getItem(i);
            if (stack.isEmpty()) continue;
            if (containerTarget == null) {
                containerTarget = ContainerUtil.findContainerAccepting(level, workplace.findStorageContainers(level), stack);
                if (containerTarget == null) {
                    // No room anywhere right now - stay put and retry next tick rather than getting stuck.
                    return true;
                }
            }
            if (!moveTowardsAndCheckArrival(inhabitant, containerTarget)) {
                return true;
            }
            if (!actionPacer.isReady(containerTarget)) {
                return true;
            }
            lookAtBlock(inhabitant, containerTarget);
            if (level.getBlockEntity(containerTarget) instanceof Container container) {
                carried.setItem(i, ContainerUtil.insertItem(container, inhabitant, stack));
            }
            actionPacer.recordAction(containerTarget);
            containerTarget = null;
            return true;
        }
        phase = Phase.GOTO_FIELD;
        skippedCrops.clear();
        return false;
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

    private boolean isDaytime(ServerLevel level) {
        long dayTime = level.getDayTime() % 24000;
        return dayTime < 12542 || dayTime >= 23460;
    }

    @Override
    public String getStatusDescription() {
        return switch (phase) {
            case GOTO_HUT -> "Heading to work";
            case GOTO_FIELD -> "Tending the field";
            case HARVESTING -> "Harvesting crops";
            case RETURN_TO_HUT -> "Returning to hut";
            case DEPOSIT -> "Storing harvest";
        };
    }

    @Override
    @Nullable
    public net.minecraft.world.item.Item getMissingItem() {
        return null;
    }

    /** True while parked in {@link Phase#GOTO_FIELD} with nothing found yet - see {@link #tickGotoField}. */
    private boolean noCropFound() {
        return targetCrop == null && noCropCooldown > 0 && phase == Phase.GOTO_FIELD;
    }

    @Override
    public List<String> getWarnings() {
        return noCropFound() ? List.of("I can't find any mature crop") : List.of();
    }

    @Override
    public List<String> getDebugLines() {
        var lines = new ArrayList<String>();
        lines.add("Phase: " + phase);
        lines.add("Target crop: " + targetCrop);
        lines.add("Wait ticks: " + waitTicks + " / " + HARVEST_TICKS);
        lines.add("Repath: " + repathCooldown + ", no-crop: " + noCropCooldown);
        lines.add("Skipped crops: " + skippedCrops.size());
        return lines;
    }

    @Override
    public void save(CompoundTag tag) {
        tag.putString("Phase", phase.name());
        tag.putInt("WaitTicks", waitTicks);
        actionPacer.save(tag, "Action");
        if (targetCrop != null) tag.putLong("TargetCrop", targetCrop.asLong());
        if (containerTarget != null) tag.putLong("ContainerTarget", containerTarget.asLong());
        tag.putLongArray("SkippedCrops", skippedCrops.stream().mapToLong(BlockPos::asLong).toArray());
    }

    @Override
    public void load(CompoundTag tag) {
        try {
            phase = Phase.valueOf(tag.getStringOr("Phase", Phase.GOTO_HUT.name()));
        } catch (IllegalArgumentException e) {
            phase = Phase.GOTO_HUT;
        }
        waitTicks = tag.getIntOr("WaitTicks", 0);
        actionPacer.load(tag, "Action");
        targetCrop = tag.contains("TargetCrop") ? BlockPos.of(tag.getLongOr("TargetCrop", 0)) : null;
        containerTarget = tag.contains("ContainerTarget") ? BlockPos.of(tag.getLongOr("ContainerTarget", 0)) : null;
        skippedCrops.clear();
        tag.getLongArray("SkippedCrops")
                .ifPresent(arr -> skippedCrops.addAll(Arrays.stream(arr).mapToObj(BlockPos::of).collect(Collectors.toList())));
    }
}
