package com.sheepfromheaven.minenow.jobs.forester;

import com.sheepfromheaven.minenow.structure.DominionStructure;
import com.sheepfromheaven.minenow.inhabitant.InhabitantEntity;
import com.sheepfromheaven.minenow.jobs.ContainerActionPacer;
import com.sheepfromheaven.minenow.jobs.ContainerUtil;
import com.sheepfromheaven.minenow.jobs.Job;
import com.sheepfromheaven.minenow.jobs.JobActions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Goes to the nursery, fetches a sapling from a nearby storage container, finds a bare patch of
 * dirt/grass within the structure's work area (see {@link DominionStructure#findPlantableSpotInRange}),
 * and plants it there before heading home for the next one.
 */
public class ForesterJob implements Job {
    private static final int PLANT_TICKS = 10;
    private static final int MISSING_SAPLING_RETRY_TICKS = 100;
    private static final int NO_SPOT_RETRY_TICKS = 100;

    private enum Phase { GOTO_HUT, FIND_SAPLING, GOTO_SPOT, PLANTING, MISSING_SAPLING }

    private Phase phase = Phase.GOTO_HUT;
    @Nullable
    private BlockPos plantSpot;
    private int waitTicks;
    private int noSpotCooldown;
    private final ContainerActionPacer actionPacer = new ContainerActionPacer();
    private final JobActions.Nav nav = new JobActions.Nav();
    private final JobActions.ContainerTarget containerTarget = new JobActions.ContainerTarget();
    /** Spots found to have no valid sapling planted at them (already occupied, blocked, ...) recently. */
    private final Set<BlockPos> unreachableSpots = new HashSet<>();
    @Nullable
    private Item missingItem;

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
    }

    @Override
    public boolean tick(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        return switch (phase) {
            case GOTO_HUT -> tickGotoHut(inhabitant, level, workplace);
            case FIND_SAPLING -> tickFindSapling(inhabitant, level, workplace);
            case GOTO_SPOT -> tickGotoSpot(inhabitant, level, workplace);
            case PLANTING -> tickPlanting(inhabitant, level);
            case MISSING_SAPLING -> tickMissingSapling();
        };
    }

    private boolean tickGotoHut(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        if (JobActions.goToJobsite(inhabitant, level, workplace, nav)) {
            phase = hasSapling(inhabitant) ? Phase.GOTO_SPOT : Phase.FIND_SAPLING;
        }
        return true;
    }

    private boolean tickFindSapling(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        if (hasSapling(inhabitant)) {
            missingItem = null;
            phase = Phase.GOTO_SPOT;
            return true;
        }
        boolean wasSearching = containerTarget.pos == null;
        var sapling = JobActions.takeFromContainer(inhabitant, level, workplace, actionPacer, nav, containerTarget,
                stack -> stack.is(ItemTags.SAPLINGS), true);
        if (!sapling.isEmpty()) {
            inhabitant.getInventory().addItem(sapling);
            missingItem = null;
            phase = Phase.GOTO_SPOT;
            return true;
        }
        if (wasSearching && containerTarget.pos == null) {
            missingItem = Items.OAK_SAPLING;
            phase = Phase.MISSING_SAPLING;
            waitTicks = MISSING_SAPLING_RETRY_TICKS;
        }
        return true;
    }

    private boolean tickGotoSpot(InhabitantEntity inhabitant, ServerLevel level, DominionStructure workplace) {
        if (plantSpot == null) {
            // Returning false here would make WorkGoal stop/restart every tick (replaying the goal-switch
            // sound in a loop) while no spot is available - park and retry on a cooldown instead.
            if (--noSpotCooldown > 0) {
                return true;
            }
            var found = workplace.findPlantableSpotInRange(level, unreachableSpots).orElse(null);
            if (found == null) {
                // Nothing plantable right now - drop the exclusion set rather than let it grow stale
                // forever (a spot can become plantable again once whatever occupied it is gone).
                unreachableSpots.clear();
                noSpotCooldown = NO_SPOT_RETRY_TICKS;
                inhabitant.playGoalSwitchSound();
                return true;
            }
            plantSpot = found;
        }
        if (JobActions.walkTo(inhabitant, plantSpot.above(), nav)) {
            phase = Phase.PLANTING;
            waitTicks = PLANT_TICKS;
        }
        return true;
    }

    private boolean tickPlanting(InhabitantEntity inhabitant, ServerLevel level) {
        if (plantSpot == null) {
            phase = Phase.GOTO_HUT;
            return true;
        }
        var progress = new JobActions.SwingProgress();
        progress.remainingTicks = waitTicks;
        boolean done = JobActions.tickSwing(inhabitant, plantSpot, progress, PLANT_TICKS, PLANT_TICKS, () -> {
            var above = plantSpot.above();
            var sapling = ContainerUtil.extractOne(inhabitant.getInventory(), inhabitant, stack -> stack.is(ItemTags.SAPLINGS));
            if (!sapling.isEmpty() && level.getBlockState(above).isAir()) {
                var block = sapling.getItem() instanceof net.minecraft.world.item.BlockItem blockItem ? blockItem.getBlock() : null;
                if (block != null) {
                    level.setBlockAndUpdate(above, block.defaultBlockState());
                    var soundType = level.getBlockState(above).getSoundType();
                    level.playSound(null, above, soundType.getPlaceSound(), net.minecraft.sounds.SoundSource.BLOCKS,
                            soundType.getVolume(), soundType.getPitch());
                }
            } else if (!sapling.isEmpty()) {
                // Spot got taken (another sapling grew, a player built there, ...) while we waited - put the
                // sapling back rather than losing it.
                inhabitant.getInventory().addItem(sapling);
            }
        });
        waitTicks = progress.remainingTicks;
        if (!done) {
            return true;
        }
        unreachableSpots.add(plantSpot);
        plantSpot = null;
        // Keep working the same trip out while saplings remain in the carried stack, rather than
        // walking all the way back to the hut just to immediately turn around for the next one.
        phase = hasSapling(inhabitant) ? Phase.GOTO_SPOT : Phase.GOTO_HUT;
        return false;
    }

    private boolean tickMissingSapling() {
        if (--waitTicks <= 0) {
            phase = Phase.FIND_SAPLING;
        }
        return true;
    }

    private boolean hasSapling(InhabitantEntity inhabitant) {
        var carried = inhabitant.getInventory();
        for (int i = 0; i < carried.getContainerSize(); i++) {
            if (carried.getItem(i).is(ItemTags.SAPLINGS)) return true;
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
            case FIND_SAPLING -> "Getting a sapling";
            case GOTO_SPOT -> "Heading to plant";
            case PLANTING -> "Planting a sapling";
            case MISSING_SAPLING -> "Needs a Sapling";
        };
    }

    @Override
    @Nullable
    public Item getMissingItem() {
        return missingItem;
    }

    @Nullable
    @Override
    public String getMissingItemLabel() {
        return missingItem != null ? "a Sapling" : null;
    }

    /** True while parked in {@link Phase#GOTO_SPOT} with nothing found yet - see {@link #tickGotoSpot}. */
    private boolean noSpotFound() {
        return plantSpot == null && noSpotCooldown > 0;
    }

    @Override
    public List<String> getWarnings() {
        return noSpotFound() ? List.of("I can't find anywhere to plant") : List.of();
    }

    @Override
    public List<String> getDebugLines() {
        return List.of(
                "Phase: " + phase,
                "Plant spot: " + plantSpot,
                "Wait ticks: " + waitTicks,
                "No-spot: " + noSpotCooldown,
                "Unreachable spots skipped: " + unreachableSpots.size()
        );
    }

    @Override
    public void save(CompoundTag tag) {
        tag.putString("Phase", phase.name());
        tag.putInt("WaitTicks", waitTicks);
        actionPacer.save(tag, "Action");
        if (plantSpot != null) tag.putLong("PlantSpot", plantSpot.asLong());
        if (containerTarget.pos != null) tag.putLong("ContainerTarget", containerTarget.pos.asLong());
        if (missingItem != null) tag.putString("MissingItem", BuiltInRegistries.ITEM.getKey(missingItem).toString());
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
        plantSpot = tag.contains("PlantSpot") ? BlockPos.of(tag.getLongOr("PlantSpot", 0)) : null;
        containerTarget.pos = tag.contains("ContainerTarget") ? BlockPos.of(tag.getLongOr("ContainerTarget", 0)) : null;
        missingItem = tag.contains("MissingItem")
                ? BuiltInRegistries.ITEM.getValue(Identifier.parse(tag.getStringOr("MissingItem", "")))
                : null;
    }
}
