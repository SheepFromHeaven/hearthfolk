package com.sheepfromheaven.minenow.jobs.farmer;

import com.sheepfromheaven.minenow.dominion.DominionSavedData;
import com.sheepfromheaven.minenow.inhabitant.InhabitantEntity;
import com.sheepfromheaven.minenow.jobs.ContainerActionPacer;
import com.sheepfromheaven.minenow.jobs.ContainerUtil;
import com.sheepfromheaven.minenow.jobs.Job;
import com.sheepfromheaven.minenow.jobs.JobActions;
import com.sheepfromheaven.minenow.jobs.fisher.ShoreScanner;
import com.sheepfromheaven.minenow.structure.DominionStructure;
import com.sheepfromheaven.minenow.structure.field.CropKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Goes to the farm and works its assigned fields (see {@link FieldWorkScanner}) in priority order:
 * first irrigating any tillable/farmland ground that isn't within range of a water source, then
 * tilling irrigated bare ground into farmland, then planting bare farmland with whichever crop the
 * field's seed template allows, and only once there's no upkeep left does it harvest mature crops
 * (see {@link CropScanner}). One action is taken per trip to the field before returning to the hut to
 * deposit whatever was collected.
 */
public class FarmerJob implements Job {
    /** ~0.5s at 20 TPS - fast, but the action still reads as a distinct visible action. */
    private static final int WORK_TICKS = 10;
    private static final int NO_WORK_RETRY_TICKS = 100;
    /** Matches vanilla's hoe durability cost per till. */
    private static final int HOE_DAMAGE_PER_TILL = 1;

    private enum Phase { GOTO_HUT, SCAN, FETCH_HOE, FETCH_BUCKET, REFILL_BUCKET, FETCH_SEED, ACTING, RETURN_TO_HUT, DEPOSIT }
    private enum ActionType { IRRIGATE, CLEAR_VEGETATION, TILL, PLANT, HARVEST }

    private Phase phase = Phase.GOTO_HUT;
    @Nullable
    private ActionType actionType;
    @Nullable
    private BlockPos targetPos;
    @Nullable
    private CropKind plantKind;
    @Nullable
    private Item missingItem;
    private int waitTicks;
    private int noWorkCooldown;
    /** Set once a hut-storage search turns up no hoe/bucket anywhere, so that action is skipped in
     * favor of lower-priority work instead of blocking the whole cycle on a fruitless retry every
     * tick; cleared on the next hut trip (or once nothing else is found either) so newly-stocked
     * supplies get noticed again. */
    private boolean hoeUnavailable;
    private boolean bucketUnavailable;
    /** Crop kinds whose seed has already turned up missing from storage this trip - skipped in favor
     * of other plantable spots/crops instead of retrying a fruitless search every tick. Cleared
     * alongside {@link #hoeUnavailable}/{@link #bucketUnavailable}. */
    private final Set<CropKind> seedUnavailable = EnumSet.noneOf(CropKind.class);
    /** The farm's own "Water" range requirement source and the dry tile to stand on to reach it, used
     * to refill an empty bucket (see {@link #tickRefillBucket}) - same approach pattern as the fisher
     * uses to reach its fishing spot. */
    @Nullable
    private BlockPos refillWaterPos;
    @Nullable
    private BlockPos refillStandPos;
    private final Set<BlockPos> unreachableRefillWater = new HashSet<>();
    private final ContainerActionPacer actionPacer = new ContainerActionPacer();
    private final JobActions.Nav nav = new JobActions.Nav();
    private final JobActions.ContainerTarget containerTarget = new JobActions.ContainerTarget();
    /** Positions that couldn't be worked this cycle (missing tool/item), skipped until the next hut trip. */
    private final Set<BlockPos> skippedPositions = new HashSet<>();

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
        containerTarget.pos = null;
        targetPos = null;
        actionType = null;
        plantKind = null;
        refillWaterPos = null;
        refillStandPos = null;
    }

    @Override
    public boolean tick(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        return switch (phase) {
            case GOTO_HUT -> tickGotoHut(inhabitant, level, workplace);
            case SCAN -> tickScan(inhabitant, level, workplace);
            case FETCH_HOE -> tickFetchHoe(inhabitant, level, workplace);
            case FETCH_BUCKET -> tickFetchBucket(inhabitant, level, workplace);
            case REFILL_BUCKET -> tickRefillBucket(inhabitant, level, workplace);
            case FETCH_SEED -> tickFetchSeed(inhabitant, level, workplace);
            case ACTING -> tickActing(inhabitant, level, workplace);
            case RETURN_TO_HUT -> tickReturnToHut(inhabitant, level, workplace);
            case DEPOSIT -> tickDeposit(inhabitant, level, workplace);
        };
    }

    private boolean tickGotoHut(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        if (JobActions.goToJobsite(inhabitant, level, workplace, nav)) {
            phase = Phase.SCAN;
        }
        return true;
    }

    private boolean tickScan(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        if (targetPos == null) {
            if (--noWorkCooldown > 0) {
                return true;
            }
            var dominion = DominionSavedData.get(level).getDominionById(inhabitant.getDominionId());
            var fields = dominion != null ? dominion.getFieldsAssignedTo(workplace.getId()) : List.<com.sheepfromheaven.minenow.structure.field.Field>of();
            // A field marker is a level-1 work condition; with none assigned there's nothing to tend.
            if (fields.isEmpty()) {
                noWorkCooldown = NO_WORK_RETRY_TICKS;
                inhabitant.playGoalSwitchSound();
                return true;
            }

            var irrigate = bucketUnavailable ? Optional.<BlockPos>empty() : FieldWorkScanner.findIrrigationTarget(level, fields, skippedPositions);
            var clearVeg = irrigate.isEmpty()
                    ? FieldWorkScanner.findVegetationClearTarget(level, fields, skippedPositions) : Optional.<BlockPos>empty();
            var till = irrigate.isEmpty() && clearVeg.isEmpty() && !hoeUnavailable
                    ? FieldWorkScanner.findTillTarget(level, fields, skippedPositions) : Optional.<BlockPos>empty();
            var plant = irrigate.isEmpty() && clearVeg.isEmpty() && till.isEmpty()
                    ? FieldWorkScanner.findPlantTarget(level, fields, skippedPositions, seedUnavailable) : Optional.<FieldWorkScanner.PlantTarget>empty();
            var harvest = irrigate.isEmpty() && clearVeg.isEmpty() && till.isEmpty() && plant.isEmpty()
                    ? CropScanner.findMatureCropInFields(level, fields, skippedPositions) : Optional.<BlockPos>empty();

            if (irrigate.isPresent()) {
                targetPos = irrigate.get();
                actionType = ActionType.IRRIGATE;
                if (!hasBucket(inhabitant)) {
                    // An empty bucket just needs refilling at the farm's required water source, not a
                    // whole new full one from storage.
                    phase = hasEmptyBucket(inhabitant) ? Phase.REFILL_BUCKET : Phase.FETCH_BUCKET;
                    return true;
                }
                missingItem = null;
            } else if (clearVeg.isPresent()) {
                targetPos = clearVeg.get();
                actionType = ActionType.CLEAR_VEGETATION;
            } else if (till.isPresent()) {
                targetPos = till.get();
                actionType = ActionType.TILL;
                if (!hasHoe(inhabitant)) {
                    phase = Phase.FETCH_HOE;
                    return true;
                }
                missingItem = null;
            } else if (plant.isPresent()) {
                targetPos = plant.get().pos();
                plantKind = plant.get().kind();
                actionType = ActionType.PLANT;
                if (!hasSeed(inhabitant, plantKind)) {
                    phase = Phase.FETCH_SEED;
                    return true;
                }
                missingItem = null;
            } else if (harvest.isPresent()) {
                targetPos = harvest.get();
                actionType = ActionType.HARVEST;
            } else {
                skippedPositions.clear();
                hoeUnavailable = false;
                bucketUnavailable = false;
                seedUnavailable.clear();
                noWorkCooldown = NO_WORK_RETRY_TICKS;
                inhabitant.playGoalSwitchSound();
                return true;
            }
        }
        if (JobActions.walkTo(inhabitant, targetPos, nav)) {
            inhabitant.setDeltaMovement(Vec3.ZERO);
            phase = Phase.ACTING;
            waitTicks = WORK_TICKS;
        }
        return true;
    }

    /** Mirrors {@code LumberjackJob}'s axe-fetching: search the hut's storage for a hoe and equip it
     * to the mainhand slot, same as any other job tool - {@link #doTill} never draws from the
     * farmer's carried inventory, only the equipped tool. */
    private boolean tickFetchHoe(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        if (hasHoe(inhabitant)) {
            missingItem = null;
            phase = Phase.SCAN;
            return true;
        }
        boolean wasSearching = containerTarget.pos == null;
        var hoe = JobActions.takeFromContainer(inhabitant, level, workplace, actionPacer, nav, containerTarget,
                stack -> stack.getItem() instanceof HoeItem, true);
        if (!hoe.isEmpty()) {
            inhabitant.setItemSlot(EquipmentSlot.MAINHAND, hoe);
            missingItem = null;
            phase = Phase.SCAN;
            return true;
        }
        if (wasSearching && containerTarget.pos == null) {
            // No hoe anywhere in storage - skip tilling this trip rather than retrying every tick
            // and blocking irrigation/planting/harvesting from ever getting a turn.
            missingItem = Items.WOODEN_HOE;
            hoeUnavailable = true;
            targetPos = null;
            actionType = null;
            phase = Phase.SCAN;
        }
        return true;
    }

    /** Same idea as {@link #tickFetchHoe}, but a water bucket is a consumable, not an equipped tool -
     * it goes into the farmer's carried inventory, same as seeds. */
    private boolean tickFetchBucket(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        if (hasBucket(inhabitant)) {
            missingItem = null;
            phase = Phase.SCAN;
            return true;
        }
        boolean wasSearching = containerTarget.pos == null;
        var bucket = JobActions.takeFromContainer(inhabitant, level, workplace, actionPacer, nav, containerTarget,
                stack -> stack.is(Items.WATER_BUCKET), true);
        if (!bucket.isEmpty()) {
            inhabitant.getInventory().addItem(bucket);
            missingItem = null;
            phase = Phase.SCAN;
            return true;
        }
        if (wasSearching && containerTarget.pos == null) {
            missingItem = Items.WATER_BUCKET;
            bucketUnavailable = true;
            targetPos = null;
            actionType = null;
            phase = Phase.SCAN;
        }
        return true;
    }

    /** Same idea as {@link #tickFetchBucket}, but for whichever crop seed {@link #plantKind} needs -
     * {@link #doPlant} never draws from anywhere but the farmer's own carried inventory. */
    private boolean tickFetchSeed(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        if (plantKind == null) {
            phase = Phase.SCAN;
            return true;
        }
        if (hasSeed(inhabitant, plantKind)) {
            missingItem = null;
            phase = Phase.SCAN;
            return true;
        }
        var seedItem = plantKind.getSeedItem();
        boolean wasSearching = containerTarget.pos == null;
        var seed = JobActions.takeFromContainer(inhabitant, level, workplace, actionPacer, nav, containerTarget,
                stack -> stack.is(seedItem), true);
        if (!seed.isEmpty()) {
            inhabitant.getInventory().addItem(seed);
            missingItem = null;
            phase = Phase.SCAN;
            return true;
        }
        if (wasSearching && containerTarget.pos == null) {
            missingItem = seedItem;
            seedUnavailable.add(plantKind);
            targetPos = null;
            actionType = null;
            plantKind = null;
            phase = Phase.SCAN;
        }
        return true;
    }

    /** Walks to the farm's required nearby water source (see
     * {@code StructureRequirementsConfig}'s FARM "Water" range requirement) and fills an empty
     * bucket from it, mirroring how the fisher
     * reaches its own fishing spot - refilling is always preferred over fetching a whole new full
     * bucket from storage (see {@link #tickScan}). */
    private boolean tickRefillBucket(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        if (hasBucket(inhabitant)) {
            missingItem = null;
            phase = Phase.SCAN;
            return true;
        }
        if (!hasEmptyBucket(inhabitant)) {
            // Lost its empty bucket somehow since this phase started - fall back to buying a full one.
            phase = Phase.FETCH_BUCKET;
            return true;
        }
        if (refillWaterPos == null) {
            var waterReq = workplace.getType().getRangeRequirements().stream()
                    .filter(r -> r.displayName().equals("Water")).findFirst().orElse(null);
            // Prefer a self-refilling source (a still water block with 2+ still water neighbors, like
            // an infinite water pool or a natural spring) over a lone decorative puddle - scooping from
            // one of those just leaves a permanent hole instead of refilling itself.
            refillWaterPos = waterReq != null
                    ? workplace.findRangeRequirementPosition(level, waterReq, unreachableRefillWater, FarmerJob::isSelfRefillingSource)
                            .orElse(null)
                    : null;
            if (refillWaterPos == null) {
                // No water in the farm's range at all - shouldn't normally happen once the level-1
                // requirement is met, but don't get stuck here if it somehow dried up.
                missingItem = Items.WATER_BUCKET;
                bucketUnavailable = true;
                targetPos = null;
                actionType = null;
                phase = Phase.SCAN;
                return true;
            }
            refillStandPos = ShoreScanner.findShorePosition(level, refillWaterPos).orElse(null);
            if (refillStandPos == null) {
                unreachableRefillWater.add(refillWaterPos);
                refillWaterPos = null;
                return true;
            }
        }
        if (JobActions.walkTo(inhabitant, refillStandPos, nav)) {
            var state = level.getBlockState(refillWaterPos);
            if (state.getBlock() instanceof BucketPickup pickup) {
                var emptyBucket = ContainerUtil.extractOne(inhabitant.getInventory(), inhabitant, stack -> stack.is(Items.BUCKET));
                if (!emptyBucket.isEmpty()) {
                    var filled = pickup.pickupBlock(inhabitant, level, refillWaterPos, state);
                    inhabitant.getInventory().addItem(!filled.isEmpty() ? filled : new ItemStack(Items.WATER_BUCKET));
                }
            } else {
                // Water's gone since we found it (drained, frozen, ...) - try a different source next tick.
                unreachableRefillWater.add(refillWaterPos);
            }
            refillWaterPos = null;
            refillStandPos = null;
            missingItem = null;
            phase = Phase.SCAN;
        }
        return true;
    }

    private boolean tickActing(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        if (targetPos == null || actionType == null) {
            phase = Phase.RETURN_TO_HUT;
            return true;
        }
        lookAtBlock(inhabitant, targetPos);
        if (--waitTicks > 0) {
            return true;
        }

        // Irrigating/tilling/planting only ever consume from the farmer's own inventory or equipped
        // tool - no need to haul anything home for those, so only a successful harvest sends it back
        // to the hut to deposit; everything else goes straight back to scanning for the
        // next-highest-priority task.
        boolean shouldReturnHome = switch (actionType) {
            case IRRIGATE -> { doIrrigate(inhabitant, level, targetPos); yield false; }
            case CLEAR_VEGETATION -> { doClearVegetation(inhabitant, level, targetPos); yield false; }
            case TILL -> { doTill(inhabitant, level, targetPos); yield false; }
            case PLANT -> { doPlant(inhabitant, level, targetPos, plantKind); yield false; }
            case HARVEST -> doHarvest(inhabitant, level, targetPos);
        };

        targetPos = null;
        actionType = null;
        plantKind = null;
        phase = shouldReturnHome ? Phase.RETURN_TO_HUT : Phase.SCAN;
        return true;
    }

    private void doIrrigate(InhabitantEntity inhabitant, ServerLevel level, BlockPos pos) {
        if (!FieldWorkScanner.isTillableOrBareFarmland(level, pos)) {
            return; // Already handled by a player or another worker since the scan.
        }
        var bucket = ContainerUtil.extractOne(inhabitant.getInventory(), inhabitant, stack -> stack.is(Items.WATER_BUCKET));
        if (bucket.isEmpty()) {
            missingItem = Items.WATER_BUCKET;
            bucketUnavailable = true;
            skippedPositions.add(pos);
            return;
        }
        level.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
        inhabitant.getInventory().addItem(new ItemStack(Items.BUCKET));
    }

    /** Breaks whatever grass/fern/etc. is growing on top of {@code pos} so the ground below counts as
     * ready to till (see {@link FieldWorkScanner#findVegetationClearTarget}) - vanilla's hoe refuses
     * to till under standing vegetation, so this always has to happen before {@link #doTill}. */
    private void doClearVegetation(InhabitantEntity inhabitant, ServerLevel level, BlockPos pos) {
        var above = pos.above();
        var state = level.getBlockState(above);
        if (!FieldWorkScanner.isVegetation(state.getBlock())) {
            return; // Already cleared by a player or another worker since the scan.
        }
        var soundType = state.getSoundType();
        level.playSound(null, above, soundType.getBreakSound(), SoundSource.BLOCKS, soundType.getVolume(), soundType.getPitch());
        for (var drop : Block.getDrops(state, level, above, level.getBlockEntity(above), inhabitant, ItemStack.EMPTY)) {
            inhabitant.getInventory().addItem(drop);
        }
        level.setBlockAndUpdate(above, Blocks.AIR.defaultBlockState());
    }

    private void doTill(InhabitantEntity inhabitant, ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        if (!isTillable(state)) {
            return; // Already handled by a player or another worker since the scan.
        }
        // Defensive re-check - FETCH_HOE should already guarantee this, but the tool could have broken
        // or been unequipped by a player in the meantime.
        var tool = inhabitant.getItemBySlot(EquipmentSlot.MAINHAND);
        if (!(tool.getItem() instanceof HoeItem)) {
            missingItem = Items.WOODEN_HOE;
            hoeUnavailable = true;
            skippedPositions.add(pos);
            return;
        }
        var soundType = state.getSoundType();
        level.playSound(null, pos, soundType.getPlaceSound(), SoundSource.BLOCKS, soundType.getVolume(), soundType.getPitch());
        level.setBlockAndUpdate(pos, Blocks.FARMLAND.defaultBlockState());
        tool.hurtAndBreak(HOE_DAMAGE_PER_TILL, inhabitant, EquipmentSlot.MAINHAND);
    }

    private void doPlant(InhabitantEntity inhabitant, ServerLevel level, BlockPos pos, @Nullable CropKind kind) {
        if (kind == null || !level.getBlockState(pos).isAir() || !level.getBlockState(pos.below()).is(Blocks.FARMLAND)) {
            return; // Already handled since the scan.
        }
        var seed = ContainerUtil.extractOne(inhabitant.getInventory(), inhabitant, stack -> stack.is(kind.getSeedItem()));
        if (seed.isEmpty()) {
            missingItem = kind.getSeedItem();
            skippedPositions.add(pos);
            return;
        }
        level.setBlockAndUpdate(pos, kind.getCropBlock().defaultBlockState());
    }

    private boolean doHarvest(InhabitantEntity inhabitant, ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof net.minecraft.world.level.block.CropBlock cropBlock) || !cropBlock.isMaxAge(state)) {
            return false; // Already harvested by a player or another worker since the scan.
        }
        var soundType = state.getSoundType();
        level.playSound(null, pos, soundType.getBreakSound(), SoundSource.BLOCKS, soundType.getVolume(), soundType.getPitch());
        for (var drop : Block.getDrops(state, level, pos, level.getBlockEntity(pos), inhabitant, ItemStack.EMPTY)) {
            inhabitant.getInventory().addItem(drop);
        }
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        return true;
    }

    private static boolean isTillable(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT_PATH) || state.is(Blocks.COARSE_DIRT);
    }

    /** Like every other job's tool, the hoe lives in the equipped mainhand slot (see
     * {@link #tickFetchHoe}), not the farmer's carried inventory. Any hoe material works. */
    private static boolean hasHoe(InhabitantEntity inhabitant) {
        return inhabitant.getItemBySlot(EquipmentSlot.MAINHAND).getItem() instanceof HoeItem;
    }

    /** A bucket is a consumable material, carried the same way seeds are - not an equipped tool. */
    private static boolean hasBucket(InhabitantEntity inhabitant) {
        var inventory = inhabitant.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(Items.WATER_BUCKET)) return true;
        }
        return false;
    }

    /** Whether the farmer is already carrying at least one seed of {@code kind} - see {@link #tickFetchSeed}. */
    private static boolean hasSeed(InhabitantEntity inhabitant, CropKind kind) {
        var inventory = inhabitant.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(kind.getSeedItem())) return true;
        }
        return false;
    }

    /** An empty bucket just needs a refill (see {@link #tickRefillBucket}), not a whole new one. */
    private static boolean hasEmptyBucket(InhabitantEntity inhabitant) {
        var inventory = inhabitant.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(Items.BUCKET)) return true;
        }
        return false;
    }

    /** A still water source with 2 or more still water sources orthogonally next to it - scooping one
     * out of a pool like this (an "infinite" water source, or a natural spring) leaves the hole
     * immediately refilled by its neighbors, unlike a single decorative puddle that would just vanish. */
    private static boolean isSelfRefillingSource(Level level, BlockPos pos) {
        var state = level.getBlockState(pos);
        if (!state.is(Blocks.WATER) || !state.getFluidState().isSource()) {
            return false;
        }
        int sourceNeighbors = 0;
        for (var dir : Direction.Plane.HORIZONTAL) {
            var neighbor = level.getBlockState(pos.relative(dir));
            if (neighbor.is(Blocks.WATER) && neighbor.getFluidState().isSource()) {
                sourceNeighbors++;
            }
        }
        return sourceNeighbors >= 2;
    }

    private boolean tickReturnToHut(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        if (JobActions.goToJobsite(inhabitant, level, workplace, nav)) {
            phase = Phase.DEPOSIT;
        }
        return true;
    }

    private boolean tickDeposit(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        if (!JobActions.depositCarriedItems(inhabitant, level, workplace, actionPacer, nav, containerTarget)) {
            return true;
        }
        phase = Phase.SCAN;
        missingItem = null;
        hoeUnavailable = false;
        bucketUnavailable = false;
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
            case SCAN -> "Tending the field";
            case FETCH_HOE -> "Fetching a hoe";
            case FETCH_BUCKET -> "Fetching a water bucket";
            case REFILL_BUCKET -> "Refilling water bucket";
            case FETCH_SEED -> "Fetching seeds";
            case ACTING -> switch (actionType == null ? ActionType.HARVEST : actionType) {
                case IRRIGATE -> "Irrigating field";
                case CLEAR_VEGETATION -> "Clearing grass";
                case TILL -> "Tilling soil";
                case PLANT -> "Planting seeds";
                case HARVEST -> "Harvesting crops";
            };
            case RETURN_TO_HUT -> "Returning to hut";
            case DEPOSIT -> "Storing harvest";
        };
    }

    @Override
    @Nullable
    public Item getMissingItem() {
        return missingItem;
    }

    /** True while parked in {@link Phase#SCAN} with nothing found yet - see {@link #tickScan}. */
    private boolean noWorkFound() {
        return targetPos == null && noWorkCooldown > 0 && phase == Phase.SCAN;
    }

    @Override
    public List<String> getWarnings() {
        return noWorkFound() ? List.of("I can't find any work in my fields") : List.of();
    }

    @Override
    public List<String> getDebugLines() {
        var lines = new ArrayList<String>();
        lines.add("Phase: " + phase);
        lines.add("Action: " + actionType);
        lines.add("Target: " + targetPos);
        lines.add("Wait ticks: " + waitTicks + " / " + WORK_TICKS);
        lines.add("No-work: " + noWorkCooldown);
        lines.add("Skipped: " + skippedPositions.size());
        lines.add("Hoe unavailable: " + hoeUnavailable);
        lines.add("Bucket unavailable: " + bucketUnavailable);
        return lines;
    }

    @Override
    public void save(CompoundTag tag) {
        tag.putString("Phase", phase.name());
        if (actionType != null) tag.putString("ActionType", actionType.name());
        if (plantKind != null) tag.putString("PlantKind", plantKind.name());
        tag.putInt("WaitTicks", waitTicks);
        actionPacer.save(tag, "Action");
        if (targetPos != null) tag.putLong("TargetPos", targetPos.asLong());
        if (containerTarget.pos != null) tag.putLong("ContainerTarget", containerTarget.pos.asLong());
        tag.putLongArray("SkippedPositions", skippedPositions.stream().mapToLong(BlockPos::asLong).toArray());
    }

    @Override
    public void load(CompoundTag tag) {
        try {
            phase = Phase.valueOf(tag.getStringOr("Phase", Phase.GOTO_HUT.name()));
        } catch (IllegalArgumentException e) {
            phase = Phase.GOTO_HUT;
        }
        try {
            actionType = tag.contains("ActionType") ? ActionType.valueOf(tag.getStringOr("ActionType", "")) : null;
        } catch (IllegalArgumentException e) {
            actionType = null;
        }
        try {
            plantKind = tag.contains("PlantKind") ? CropKind.valueOf(tag.getStringOr("PlantKind", "")) : null;
        } catch (IllegalArgumentException e) {
            plantKind = null;
        }
        waitTicks = tag.getIntOr("WaitTicks", 0);
        actionPacer.load(tag, "Action");
        targetPos = tag.contains("TargetPos") ? BlockPos.of(tag.getLongOr("TargetPos", 0)) : null;
        containerTarget.pos = tag.contains("ContainerTarget") ? BlockPos.of(tag.getLongOr("ContainerTarget", 0)) : null;
        skippedPositions.clear();
        tag.getLongArray("SkippedPositions")
                .ifPresent(arr -> skippedPositions.addAll(Arrays.stream(arr).mapToObj(BlockPos::of).collect(Collectors.toList())));
    }
}
