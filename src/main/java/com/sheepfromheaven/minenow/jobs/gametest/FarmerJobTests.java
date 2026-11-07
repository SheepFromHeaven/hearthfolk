package com.sheepfromheaven.minenow.jobs.gametest;

import com.sheepfromheaven.minenow.dominion.Dominion;
import com.sheepfromheaven.minenow.dominion.DominionSavedData;
import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import com.sheepfromheaven.minenow.inhabitant.ModEntities;
import com.sheepfromheaven.minenow.jobs.WorkGoal;
import com.sheepfromheaven.minenow.structure.DominionStructure;
import com.sheepfromheaven.minenow.structure.StructureType;
import com.sheepfromheaven.minenow.structure.field.Field;
import com.sheepfromheaven.minenow.structure.field.block.FieldMarkerBlockEntity;
import com.sheepfromheaven.minenow.structure.StructureBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;

import java.util.Set;
import java.util.UUID;

// Registered into the vanilla test_function registry by MineNow's RegisterEvent listener;
// the matching data/minenow/test_instance/*.json files supply the structure and tick budget.
public class FarmerJobTests {

    private record Farm(Dominion dominion, DominionStructure structure) {}

    /**
     * Creates the dominion/structure and returns them directly rather than re-deriving the dominion
     * via a position lookup afterward - the shared gametest world can have other tests' dominions
     * whose default radius overlaps this test's marker position, so a lookup can resolve to the wrong
     * one.
     */
    private static Farm setUpFarm(GameTestHelper helper, BlockPos markerPos, BlockPos barrelPos) {
        var level = helper.getLevel();
        var dominion = DominionSavedData.get(level).createDominion(UUID.randomUUID(), "Test Dominion", markerPos);
        var structure = new DominionStructure("Farm", StructureType.FARM, markerPos, Set.of(markerPos, barrelPos));
        dominion.addStructure(structure);
        return new Farm(dominion, structure);
    }

    /** Enough ticks for one walk-then-act cycle (arrival is instant since tests snap directly onto the
     * action position) - covers FarmerJob's WORK_TICKS wait plus a little slack. */
    private static final int WORK_ACTION_TICKS = 20;

    private static void runUntilReturning(GameTestHelper helper, com.sheepfromheaven.minenow.inhabitant.InhabitantEntity inhabitant,
            WorkGoal workGoal, BlockPos markerPos, BlockPos actionPos) {
        inhabitant.snapTo(markerPos.getX() + 0.5, markerPos.getY(), markerPos.getZ() + 0.5);
        workGoal.tick();
        inhabitant.snapTo(actionPos.getX() + 0.5, actionPos.getY(), actionPos.getZ() + 0.5);
        for (int i = 0; i < 60 && !"Returning to hut".equals(workGoal.getStatusDescription()); i++) {
            workGoal.tick();
        }
    }

    /**
     * The farmer should harvest a fully-grown wheat crop in its assigned field and end up carrying
     * it - replanting is a separate, lower-priority action handled on a later trip (see
     * {@link #farmerPlantsTemplatedSeedOnBareFarmland}).
     */
    public static void farmerHarvestsMatureCrop(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);

        var markerRelPos = new BlockPos(2, 1, 2);
        var barrelRelPos = new BlockPos(1, 1, 1);
        var farmlandRelPos = new BlockPos(4, 1, 2);
        helper.setBlock(barrelRelPos, Blocks.BARREL);
        helper.setBlock(farmlandRelPos, Blocks.FARMLAND);
        helper.setBlock(farmlandRelPos.above(), ((CropBlock) Blocks.WHEAT).getStateForAge(7));

        var markerPos = helper.absolutePos(markerRelPos);
        var barrelPos = helper.absolutePos(barrelRelPos);
        var cropPos = helper.absolutePos(farmlandRelPos.above());
        var fieldPos = helper.absolutePos(farmlandRelPos);

        var farm = setUpFarm(helper, markerPos, barrelPos);
        var field = new Field(fieldPos, Set.of(fieldPos));
        field.setAssignedStructureId(farm.structure().getId());
        farm.dominion().addField(field);

        var inhabitant = helper.spawn(ModEntities.INHABITANT.get(), markerPos);
        inhabitant.setDominionId(farm.dominion().getId());
        inhabitant.setWorkStructureId(farm.structure().getId());

        var workGoal = new WorkGoal(inhabitant);
        workGoal.canUse();
        workGoal.start();
        runUntilReturning(helper, inhabitant, workGoal, markerPos, cropPos);

        helper.succeedWhen(() -> {
            var state = helper.getLevel().getBlockState(cropPos);
            helper.assertTrue(state.isAir(), "Harvested crop should be gone");
            boolean carryingWheat = false;
            var carried = inhabitant.getInventory();
            for (int i = 0; i < carried.getContainerSize(); i++) {
                if (carried.getItem(i).is(Items.WHEAT)) carryingWheat = true;
            }
            helper.assertTrue(carryingWheat, "Farmer should be carrying the harvested wheat");
        });
    }

    /**
     * Tilling takes priority over planting/harvesting: a bare, already-irrigated dirt tile inside the
     * field's footprint gets turned into farmland, as long as the farmer is carrying a hoe. (Tilling
     * unirrigated ground is covered separately - see {@code farmerIrrigatesBeforeTilling} - since
     * irrigation now outranks tilling.)
     */
    public static void farmerTillsBareGroundInField(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);

        var markerRelPos = new BlockPos(2, 1, 2);
        var barrelRelPos = new BlockPos(1, 1, 1);
        var dirtRelPos = new BlockPos(4, 1, 2);
        var waterRelPos = new BlockPos(5, 1, 2);
        helper.setBlock(barrelRelPos, Blocks.BARREL);
        helper.setBlock(dirtRelPos, Blocks.DIRT);
        // Walled on the sides not already blocked by the dirt tile, so the water can't flow out into
        // the room and flood tiles the farmer needs to walk across.
        helper.setBlock(waterRelPos.north(), Blocks.STONE);
        helper.setBlock(waterRelPos.south(), Blocks.STONE);
        helper.setBlock(waterRelPos.east(), Blocks.STONE);
        helper.setBlock(waterRelPos, Blocks.WATER);

        var markerPos = helper.absolutePos(markerRelPos);
        var barrelPos = helper.absolutePos(barrelRelPos);
        var dirtPos = helper.absolutePos(dirtRelPos);
        var waterPos = helper.absolutePos(waterRelPos);

        var farm = setUpFarm(helper, markerPos, barrelPos);
        // The water tile is already irrigating the dirt (within range) so tilling can proceed directly.
        var field = new Field(dirtPos, Set.of(dirtPos, waterPos));
        field.setAssignedStructureId(farm.structure().getId());
        farm.dominion().addField(field);

        var inhabitant = helper.spawn(ModEntities.INHABITANT.get(), markerPos);
        inhabitant.setDominionId(farm.dominion().getId());
        inhabitant.setWorkStructureId(farm.structure().getId());
        // Tools live in the equipped mainhand slot, same as every other job's tool - not the farmer's
        // carried inventory (see FarmerJob#hasHoe).
        inhabitant.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_HOE));

        var workGoal = new WorkGoal(inhabitant);
        workGoal.canUse();
        workGoal.start();
        runUntilReturning(helper, inhabitant, workGoal, markerPos, dirtPos);

        helper.succeedWhen(() -> {
            var state = helper.getLevel().getBlockState(dirtPos);
            helper.assertTrue(state.is(Blocks.FARMLAND), "Dirt should have been tilled into farmland");
            var tool = inhabitant.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND);
            helper.assertTrue(tool.getItem() instanceof net.minecraft.world.item.HoeItem && tool.getDamageValue() == 1,
                    "Tilling should have cost the hoe 1 durability, same as vanilla");
        });
    }

    /**
     * A grass block with tall grass (or fern, etc.) still growing on top of it isn't ready to till
     * yet - vanilla's hoe refuses to till under standing vegetation, so the farmer has to break it
     * first (see {@code FieldWorkScanner#findVegetationClearTarget}), then till the same tile on a
     * later scan.
     */
    public static void farmerClearsGrassBeforeTillingGrassBlock(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);

        var markerRelPos = new BlockPos(2, 1, 2);
        var barrelRelPos = new BlockPos(1, 1, 1);
        var grassRelPos = new BlockPos(4, 1, 2);
        var tallGrassRelPos = new BlockPos(4, 2, 2);
        var waterRelPos = new BlockPos(5, 1, 2);
        helper.setBlock(barrelRelPos, Blocks.BARREL);
        helper.setBlock(grassRelPos, Blocks.GRASS_BLOCK);
        helper.setBlock(tallGrassRelPos, Blocks.SHORT_GRASS);
        // Walled on the sides not already blocked by the grass tile, so the water can't flow out into
        // the room and flood tiles the farmer needs to walk across.
        helper.setBlock(waterRelPos.north(), Blocks.STONE);
        helper.setBlock(waterRelPos.south(), Blocks.STONE);
        helper.setBlock(waterRelPos.east(), Blocks.STONE);
        helper.setBlock(waterRelPos, Blocks.WATER);

        var markerPos = helper.absolutePos(markerRelPos);
        var barrelPos = helper.absolutePos(barrelRelPos);
        var grassPos = helper.absolutePos(grassRelPos);
        var waterPos = helper.absolutePos(waterRelPos);

        var farm = setUpFarm(helper, markerPos, barrelPos);
        // The water tile is already irrigating the grass block (within range) so tilling can proceed
        // once the grass on top of it is cleared.
        var field = new Field(grassPos, Set.of(grassPos, waterPos));
        field.setAssignedStructureId(farm.structure().getId());
        farm.dominion().addField(field);

        var inhabitant = helper.spawn(ModEntities.INHABITANT.get(), markerPos);
        inhabitant.setDominionId(farm.dominion().getId());
        inhabitant.setWorkStructureId(farm.structure().getId());
        inhabitant.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_HOE));

        var workGoal = new WorkGoal(inhabitant);
        workGoal.canUse();
        workGoal.start();
        runUntilReturning(helper, inhabitant, workGoal, markerPos, grassPos);

        helper.succeedWhen(() -> {
            var level = helper.getLevel();
            helper.assertTrue(level.getBlockState(grassPos).is(Blocks.FARMLAND),
                    "Grass block should have been tilled into farmland once the grass on top was cleared");
            helper.assertTrue(level.getBlockState(grassPos.above()).isAir(),
                    "Tall grass on top should have been broken before tilling");
        });
    }

    /**
     * With no hoe equipped, the farmer should fetch one from the hut's storage (a barrel) before
     * tilling, mirroring how every other job equips its tool - not from its own carried inventory.
     */
    public static void farmerFetchesHoeFromStorageBeforeTilling(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);

        var markerRelPos = new BlockPos(2, 1, 2);
        var barrelRelPos = new BlockPos(1, 1, 1);
        var dirtRelPos = new BlockPos(4, 1, 2);
        var waterRelPos = new BlockPos(5, 1, 2);
        helper.setBlock(barrelRelPos, Blocks.BARREL);
        helper.setBlock(dirtRelPos, Blocks.DIRT);
        // Walled on the sides not already blocked by the dirt tile, so the water can't flow out into
        // the room and flood tiles the farmer needs to walk across.
        helper.setBlock(waterRelPos.north(), Blocks.STONE);
        helper.setBlock(waterRelPos.south(), Blocks.STONE);
        helper.setBlock(waterRelPos.east(), Blocks.STONE);
        helper.setBlock(waterRelPos, Blocks.WATER);

        var markerPos = helper.absolutePos(markerRelPos);
        var barrelPos = helper.absolutePos(barrelRelPos);
        var dirtPos = helper.absolutePos(dirtRelPos);
        var waterPos = helper.absolutePos(waterRelPos);

        var farm = setUpFarm(helper, markerPos, barrelPos);
        var field = new Field(dirtPos, Set.of(dirtPos, waterPos));
        field.setAssignedStructureId(farm.structure().getId());
        farm.dominion().addField(field);

        if (helper.getLevel().getBlockEntity(barrelPos) instanceof net.minecraft.world.Container barrel) {
            barrel.setItem(0, new ItemStack(Items.WOODEN_HOE));
        }

        var inhabitant = helper.spawn(ModEntities.INHABITANT.get(), markerPos);
        inhabitant.setDominionId(farm.dominion().getId());
        inhabitant.setWorkStructureId(farm.structure().getId());

        var workGoal = new WorkGoal(inhabitant);
        workGoal.canUse();
        workGoal.start();
        inhabitant.snapTo(markerPos.getX() + 0.5, markerPos.getY(), markerPos.getZ() + 0.5);

        // Fetching the hoe from the barrel requires genuinely walking there and back - unlike the
        // other farmer tests (which snap directly onto the action position, needing no real travel),
        // ticking the goal in a tight synchronous loop here would never let the entity's own aiStep
        // actually move it. Tick it once per real game tick instead (succeedWhen's callback runs once
        // per tick), so real navigation/movement happens between calls.
        helper.succeedWhen(() -> {
            workGoal.tick();
            var state = helper.getLevel().getBlockState(dirtPos);
            helper.assertTrue(state.is(Blocks.FARMLAND), "Dirt should have been tilled into farmland using the fetched hoe");
            helper.assertTrue(inhabitant.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND).getItem() instanceof net.minecraft.world.item.HoeItem,
                    "Farmer should have equipped the hoe fetched from storage");
        });
    }

    /**
     * Irrigation outranks tilling: with no water anywhere in the field, the farmer should place one
     * from a carried bucket at the walled-in tile (solid field ground on all 4 cardinal sides, so the
     * water stays a still pool) that irrigates the most ground - here a 3x3 patch of dirt where only
     * the very center tile qualifies - rather than tilling any of the 9 dirt tiles first.
     */
    public static void farmerIrrigatesBeforeTillingUnwateredField(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);

        var markerRelPos = new BlockPos(2, 1, 2);
        var barrelRelPos = new BlockPos(1, 1, 1);
        helper.setBlock(barrelRelPos, Blocks.BARREL);

        var fieldPositions = new java.util.HashSet<BlockPos>();
        for (int x = 4; x <= 6; x++) {
            for (int z = 1; z <= 3; z++) {
                var rel = new BlockPos(x, 1, z);
                helper.setBlock(rel, Blocks.DIRT);
                fieldPositions.add(helper.absolutePos(rel));
            }
        }
        var centerPos = helper.absolutePos(new BlockPos(5, 1, 2));

        var markerPos = helper.absolutePos(markerRelPos);
        var barrelPos = helper.absolutePos(barrelRelPos);

        var farm = setUpFarm(helper, markerPos, barrelPos);
        var field = new Field(centerPos, fieldPositions);
        field.setAssignedStructureId(farm.structure().getId());
        farm.dominion().addField(field);

        var inhabitant = helper.spawn(ModEntities.INHABITANT.get(), markerPos);
        inhabitant.setDominionId(farm.dominion().getId());
        inhabitant.setWorkStructureId(farm.structure().getId());
        inhabitant.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_HOE));
        inhabitant.getInventory().addItem(new ItemStack(Items.WATER_BUCKET));

        var workGoal = new WorkGoal(inhabitant);
        workGoal.canUse();
        workGoal.start();
        inhabitant.snapTo(markerPos.getX() + 0.5, markerPos.getY(), markerPos.getZ() + 0.5);
        workGoal.tick();
        inhabitant.snapTo(centerPos.getX() + 0.5, centerPos.getY(), centerPos.getZ() + 0.5);
        // Only the FIRST action is checked here - irrigating and tilling both loop straight back to
        // scanning (no need to walk home in between, only harvesting deposits anything), so a longer
        // run would go on to till the now-irrigated tiles too. SCAN's status text ("Tending the
        // field") doesn't change between "still picking a target" and "back to idle after acting", so
        // there's no status-based signal to stop on - just run exactly enough ticks for one full
        // walk-then-act cycle instead of running until "Returning to hut" (which only ever happens
        // after a harvest).
        for (int i = 0; i < WORK_ACTION_TICKS; i++) {
            workGoal.tick();
        }

        helper.succeedWhen(() -> {
            var state = helper.getLevel().getBlockState(centerPos);
            helper.assertTrue(state.is(Blocks.WATER),
                    "Farmer should have placed water at the walled-in center tile before tilling anything");
            boolean carryingEmptyBucket = false;
            var carried = inhabitant.getInventory();
            for (int i = 0; i < carried.getContainerSize(); i++) {
                if (carried.getItem(i).is(Items.BUCKET)) carryingEmptyBucket = true;
            }
            helper.assertTrue(carryingEmptyBucket, "Farmer should be carrying the emptied bucket after placing water");
            int farmlandCount = 0;
            for (var pos : fieldPositions) {
                if (helper.getLevel().getBlockState(pos).is(Blocks.FARMLAND)) farmlandCount++;
            }
            helper.assertTrue(farmlandCount == 0, "No tilling should happen until irrigation is placed");
        });
    }

    /**
     * Refilling an empty bucket at the farm's required nearby water source (see
     * {@code StructureRequirementsConfig}'s FARM level-1 "Water" range requirement) is preferred over
     * fetching a whole new full bucket from storage.
     */
    public static void farmerRefillsEmptyBucketFromNearbyWaterSource(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);

        // ShoreScanner (unlike a plain container fetch) requires genuinely solid, standable ground
        // beside the water - makeFloor's y=-1 slab alone isn't enough since these tests otherwise rely
        // on the barrel/dirt/water sitting at y=1 with nothing explicit underneath.
        for (int x = 0; x <= 8; x++) {
            for (int z = 0; z <= 8; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }

        var markerRelPos = new BlockPos(2, 1, 2);
        var barrelRelPos = new BlockPos(1, 1, 1);
        var waterRelPos = new BlockPos(1, 1, 3);
        helper.setBlock(barrelRelPos, Blocks.BARREL);
        // Wall the water source in on every horizontal side so it can't flow out into the room and
        // spawn flowing-water blocks that would confuse ShoreScanner's standable-ground check.
        helper.setBlock(waterRelPos.north(), Blocks.STONE);
        helper.setBlock(waterRelPos.south(), Blocks.STONE);
        helper.setBlock(waterRelPos.east(), Blocks.STONE);
        helper.setBlock(waterRelPos.west(), Blocks.STONE);
        helper.setBlock(waterRelPos, Blocks.WATER);

        var fieldPositions = new java.util.HashSet<BlockPos>();
        for (int x = 4; x <= 6; x++) {
            for (int z = 1; z <= 3; z++) {
                var rel = new BlockPos(x, 1, z);
                helper.setBlock(rel, Blocks.DIRT);
                fieldPositions.add(helper.absolutePos(rel));
            }
        }
        var centerPos = helper.absolutePos(new BlockPos(5, 1, 2));

        var markerPos = helper.absolutePos(markerRelPos);
        var barrelPos = helper.absolutePos(barrelRelPos);

        var farm = setUpFarm(helper, markerPos, barrelPos);
        var field = new Field(centerPos, fieldPositions);
        field.setAssignedStructureId(farm.structure().getId());
        farm.dominion().addField(field);

        var inhabitant = helper.spawn(ModEntities.INHABITANT.get(), markerPos);
        inhabitant.setDominionId(farm.dominion().getId());
        inhabitant.setWorkStructureId(farm.structure().getId());
        inhabitant.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_HOE));
        // Only an empty bucket, no full one - should be refilled at the farm's water source rather
        // than asking storage for a brand new water bucket.
        inhabitant.getInventory().addItem(new ItemStack(Items.BUCKET));

        var workGoal = new WorkGoal(inhabitant);
        workGoal.canUse();
        workGoal.start();
        inhabitant.snapTo(markerPos.getX() + 0.5, markerPos.getY(), markerPos.getZ() + 0.5);

        // Refilling requires genuinely walking to the water source and back, same as fetching a hoe
        // from storage - tick once per real game tick so real navigation happens between calls.
        helper.succeedWhen(() -> {
            workGoal.tick();
            boolean carryingFullBucket = false;
            var carried = inhabitant.getInventory();
            for (int i = 0; i < carried.getContainerSize(); i++) {
                if (carried.getItem(i).is(Items.WATER_BUCKET)) carryingFullBucket = true;
            }
            helper.assertTrue(carryingFullBucket, "Farmer should have refilled its empty bucket at the nearby water source");
        });
    }

    /**
     * When multiple water sources are in range, the farmer should prefer scooping from a
     * self-refilling one (a still water source with 2+ still water neighbors, like an infinite pool or
     * natural spring) over a lone decorative puddle - the puddle would just vanish, leaving a
     * permanent hole, while the pool immediately looks whole again.
     */
    public static void farmerPrefersSelfRefillingWaterSourceOverPuddle(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);

        for (int x = 0; x <= 8; x++) {
            for (int z = 0; z <= 8; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }

        var markerRelPos = new BlockPos(2, 1, 2);
        var barrelRelPos = new BlockPos(1, 1, 1);
        helper.setBlock(barrelRelPos, Blocks.BARREL);

        // A lone puddle with no water neighbors - should be left untouched. Walled in on every
        // horizontal side (like a well) so it can't flow out into the room and flood the tiles a
        // shore search would otherwise use to stand beside it.
        var puddleRelPos = new BlockPos(1, 1, 6);
        helper.setBlock(puddleRelPos.north(), Blocks.STONE);
        helper.setBlock(puddleRelPos.south(), Blocks.STONE);
        helper.setBlock(puddleRelPos.east(), Blocks.STONE);
        helper.setBlock(puddleRelPos.west(), Blocks.STONE);
        helper.setBlock(puddleRelPos, Blocks.WATER);

        // A 3-in-a-row pool; only the middle tile has 2 water-source neighbors and self-refills. Kept
        // well clear of the room's edge (x=8 is the wall) so placing water there doesn't punch a hole
        // in the boundary and confuse pathfinding. Walled on the north/south/outer-end sides - every
        // side that isn't already another pool tile - so the pool can't flood outward either.
        var poolWestRelPos = new BlockPos(5, 1, 6);
        var poolMiddleRelPos = new BlockPos(6, 1, 6);
        var poolEastRelPos = new BlockPos(7, 1, 6);
        helper.setBlock(poolWestRelPos.north(), Blocks.STONE);
        helper.setBlock(poolWestRelPos.south(), Blocks.STONE);
        helper.setBlock(poolWestRelPos.west(), Blocks.STONE);
        helper.setBlock(poolMiddleRelPos.north(), Blocks.STONE);
        helper.setBlock(poolMiddleRelPos.south(), Blocks.STONE);
        helper.setBlock(poolEastRelPos.north(), Blocks.STONE);
        helper.setBlock(poolEastRelPos.south(), Blocks.STONE);
        helper.setBlock(poolEastRelPos.east(), Blocks.STONE);
        helper.setBlock(poolWestRelPos, Blocks.WATER);
        helper.setBlock(poolMiddleRelPos, Blocks.WATER);
        helper.setBlock(poolEastRelPos, Blocks.WATER);

        var puddlePos = helper.absolutePos(puddleRelPos);

        var fieldPositions = new java.util.HashSet<BlockPos>();
        for (int x = 3; x <= 5; x++) {
            for (int z = 3; z <= 5; z++) {
                var rel = new BlockPos(x, 1, z);
                helper.setBlock(rel, Blocks.DIRT);
                fieldPositions.add(helper.absolutePos(rel));
            }
        }
        var centerPos = helper.absolutePos(new BlockPos(4, 1, 4));

        var markerPos = helper.absolutePos(markerRelPos);
        var barrelPos = helper.absolutePos(barrelRelPos);

        var farm = setUpFarm(helper, markerPos, barrelPos);
        var field = new Field(centerPos, fieldPositions);
        field.setAssignedStructureId(farm.structure().getId());
        farm.dominion().addField(field);

        var inhabitant = helper.spawn(ModEntities.INHABITANT.get(), markerPos);
        inhabitant.setDominionId(farm.dominion().getId());
        inhabitant.setWorkStructureId(farm.structure().getId());
        inhabitant.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_HOE));
        inhabitant.getInventory().addItem(new ItemStack(Items.BUCKET));

        var workGoal = new WorkGoal(inhabitant);
        workGoal.canUse();
        workGoal.start();
        inhabitant.snapTo(markerPos.getX() + 0.5, markerPos.getY(), markerPos.getZ() + 0.5);

        helper.succeedWhen(() -> {
            workGoal.tick();
            boolean carryingFullBucket = false;
            var carried = inhabitant.getInventory();
            for (int i = 0; i < carried.getContainerSize(); i++) {
                if (carried.getItem(i).is(Items.WATER_BUCKET)) carryingFullBucket = true;
            }
            helper.assertTrue(carryingFullBucket, "Farmer should have refilled its empty bucket");
            helper.assertTrue(helper.getLevel().getBlockState(puddlePos).is(Blocks.WATER),
                    "Farmer should not have scooped the lone puddle, leaving it untouched");
        });
    }

    /**
     * With no upkeep left, the farmer plants bare farmland using a seed matching whatever's templated
     * into the field marker's seed slots (see {@link FieldMarkerBlockEntity}), taken from its own
     * carried inventory.
     */
    public static void farmerPlantsTemplatedSeedOnBareFarmland(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);

        var markerRelPos = new BlockPos(2, 1, 2);
        var barrelRelPos = new BlockPos(1, 1, 1);
        var farmlandRelPos = new BlockPos(4, 1, 2);
        helper.setBlock(barrelRelPos, Blocks.BARREL);
        helper.setBlock(markerRelPos, StructureBlocks.FIELD_MARKER.get());
        helper.setBlock(farmlandRelPos, Blocks.FARMLAND);

        var level = helper.getLevel();
        var markerPos = helper.absolutePos(markerRelPos);
        var barrelPos = helper.absolutePos(barrelRelPos);
        var farmlandPos = helper.absolutePos(farmlandRelPos);
        var cropPos = farmlandPos.above();

        var farm = setUpFarm(helper, markerPos, barrelPos);
        var field = new Field(markerPos, Set.of(farmlandPos));
        field.setAssignedStructureId(farm.structure().getId());
        farm.dominion().addField(field);

        if (level.getBlockEntity(markerPos) instanceof FieldMarkerBlockEntity be) {
            be.setTemplates(java.util.List.of(new ItemStack(Items.WHEAT_SEEDS)));
        }

        var inhabitant = helper.spawn(ModEntities.INHABITANT.get(), markerPos);
        inhabitant.setDominionId(farm.dominion().getId());
        inhabitant.setWorkStructureId(farm.structure().getId());
        inhabitant.getInventory().addItem(new ItemStack(Items.WHEAT_SEEDS, 4));

        var workGoal = new WorkGoal(inhabitant);
        workGoal.canUse();
        workGoal.start();
        runUntilReturning(helper, inhabitant, workGoal, markerPos, cropPos);

        helper.succeedWhen(() -> {
            var state = helper.getLevel().getBlockState(cropPos);
            helper.assertTrue(state.is(Blocks.WHEAT) && state.getValue(CropBlock.AGE) == 0,
                    "Bare farmland should have been planted with the templated seed");
        });
    }

    /**
     * With no seed carried, the farmer should fetch the templated crop's seed from the hut's storage
     * (a barrel) before planting - mirroring how it fetches a missing hoe/bucket - rather than
     * reporting the seed missing while it's sitting right there in storage.
     */
    public static void farmerFetchesSeedFromStorageBeforePlanting(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);

        var markerRelPos = new BlockPos(2, 1, 2);
        var barrelRelPos = new BlockPos(1, 1, 1);
        var farmlandRelPos = new BlockPos(4, 1, 2);
        helper.setBlock(barrelRelPos, Blocks.BARREL);
        helper.setBlock(markerRelPos, StructureBlocks.FIELD_MARKER.get());
        helper.setBlock(farmlandRelPos, Blocks.FARMLAND);

        var level = helper.getLevel();
        var markerPos = helper.absolutePos(markerRelPos);
        var barrelPos = helper.absolutePos(barrelRelPos);
        var farmlandPos = helper.absolutePos(farmlandRelPos);
        var cropPos = farmlandPos.above();

        var farm = setUpFarm(helper, markerPos, barrelPos);
        var field = new Field(markerPos, Set.of(farmlandPos));
        field.setAssignedStructureId(farm.structure().getId());
        farm.dominion().addField(field);

        if (level.getBlockEntity(markerPos) instanceof FieldMarkerBlockEntity be) {
            be.setTemplates(java.util.List.of(new ItemStack(Items.CARROT)));
        }
        if (level.getBlockEntity(barrelPos) instanceof net.minecraft.world.Container barrel) {
            barrel.setItem(0, new ItemStack(Items.CARROT, 4));
        }

        var inhabitant = helper.spawn(ModEntities.INHABITANT.get(), markerPos);
        inhabitant.setDominionId(farm.dominion().getId());
        inhabitant.setWorkStructureId(farm.structure().getId());

        var workGoal = new WorkGoal(inhabitant);
        workGoal.canUse();
        workGoal.start();
        // Same snap-driven approach as runUntilReturning (real navigation isn't the point of this
        // test): settle into FETCH_SEED first, snap onto the barrel so the extraction happens
        // immediately, then snap onto the plant target for the rest of the cycle.
        inhabitant.snapTo(markerPos.getX() + 0.5, markerPos.getY(), markerPos.getZ() + 0.5);
        workGoal.tick();
        workGoal.tick();
        inhabitant.snapTo(barrelPos.getX() + 0.5, barrelPos.getY(), barrelPos.getZ() + 0.5);
        workGoal.tick();
        inhabitant.snapTo(cropPos.getX() + 0.5, cropPos.getY(), cropPos.getZ() + 0.5);
        for (int i = 0; i < 30 && !"Returning to hut".equals(workGoal.getStatusDescription()); i++) {
            workGoal.tick();
        }

        helper.succeedWhen(() -> {
            var state = helper.getLevel().getBlockState(cropPos);
            helper.assertTrue(state.is(Blocks.CARROTS) && state.getValue(CropBlock.AGE) == 0,
                    "Bare farmland should have been planted with the seed fetched from storage");
        });
    }
}
