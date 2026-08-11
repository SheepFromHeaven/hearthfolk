package com.sheepfromheaven.minenow.jobs.forester.gametest;

import com.sheepfromheaven.minenow.dominion.DominionSavedData;
import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import com.sheepfromheaven.minenow.inhabitant.ModEntities;
import com.sheepfromheaven.minenow.jobs.WorkGoal;
import com.sheepfromheaven.minenow.structure.DominionStructure;
import com.sheepfromheaven.minenow.structure.StructureType;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.Set;
import java.util.UUID;

// Registered into the vanilla test_function registry by MineNow's RegisterEvent listener;
// the matching data/minenow/test_instance/*.json files supply the structure and tick budget.
public class ForesterTests {

    public static void findPlantableSpotInRangeFindsBareGrass(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);

        var markerRelPos = new BlockPos(4, 1, 4);
        var grassRelPos = new BlockPos(4, 0, 6);

        helper.setBlock(grassRelPos, Blocks.GRASS_BLOCK);

        var markerPos = helper.absolutePos(markerRelPos);
        var grassPos = helper.absolutePos(grassRelPos);
        var structure = new DominionStructure("Nursery", StructureType.NURSERY, markerPos, Set.of(markerPos));

        var spot = structure.findPlantableSpotInRange(helper.getLevel(), Set.of());

        helper.succeedWhen(() -> {
            helper.assertTrue(spot.isPresent(), "A bare patch of grass within range should be found");
            helper.assertTrue(spot.get().equals(grassPos), "The found spot should be the bare grass block");
        });
    }

    public static void findPlantableSpotInRangeSkipsSpotAlreadyHoldingASapling(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);

        var markerRelPos = new BlockPos(4, 1, 4);
        var grassRelPos = new BlockPos(4, 0, 6);
        var saplingRelPos = new BlockPos(4, 1, 6);

        helper.setBlock(grassRelPos, Blocks.GRASS_BLOCK);
        helper.setBlock(saplingRelPos, Blocks.OAK_SAPLING);

        var markerPos = helper.absolutePos(markerRelPos);
        var structure = new DominionStructure("Nursery", StructureType.NURSERY, markerPos, Set.of(markerPos));

        var spot = structure.findPlantableSpotInRange(helper.getLevel(), Set.of());

        helper.succeedWhen(() ->
                helper.assertTrue(spot.isEmpty(), "A grass block already growing a sapling should not be treated as plantable"));
    }

    public static void findPlantableSpotInRangeSkipsSpotsWithinMinSpacingOfExistingSapling(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);

        var markerRelPos = new BlockPos(4, 1, 4);
        var nearBareGrassRelPos = new BlockPos(4, 0, 6);
        var existingSaplingGroundRelPos = new BlockPos(5, 0, 6);
        var farBareGrassRelPos = new BlockPos(4, 0, 2);

        helper.setBlock(nearBareGrassRelPos, Blocks.GRASS_BLOCK);
        helper.setBlock(existingSaplingGroundRelPos, Blocks.GRASS_BLOCK);
        helper.setBlock(existingSaplingGroundRelPos.above(), Blocks.OAK_SAPLING);
        helper.setBlock(farBareGrassRelPos, Blocks.GRASS_BLOCK);

        var markerPos = helper.absolutePos(markerRelPos);
        var farBareGrassPos = helper.absolutePos(farBareGrassRelPos);
        var structure = new DominionStructure("Nursery", StructureType.NURSERY, markerPos, Set.of(markerPos));

        var spot = structure.findPlantableSpotInRange(helper.getLevel(), Set.of());

        helper.succeedWhen(() -> {
            helper.assertTrue(spot.isPresent(), "The far bare patch should still be found");
            helper.assertTrue(spot.get().equals(farBareGrassPos),
                    "The spot within min spacing of the existing sapling should be skipped in favour of the far one");
        });
    }

    /**
     * A forester carrying multiple saplings should plant them all on the same trip, moving directly
     * between plant spots rather than walking back to the hut after every single sapling.
     */
    public static void foresterPlantsMultipleSaplingsOnTheSameTripWithoutReturningToTheHut(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);

        var markerRelPos = new BlockPos(4, 1, 1);
        var grass1RelPos = new BlockPos(4, 1, 4);
        var grass2RelPos = new BlockPos(4, 1, 7);

        helper.setBlock(grass1RelPos, Blocks.GRASS_BLOCK);
        helper.setBlock(grass2RelPos, Blocks.GRASS_BLOCK);

        var level = helper.getLevel();
        var markerPos = helper.absolutePos(markerRelPos);
        var planted1Pos = helper.absolutePos(grass1RelPos).above();
        var planted2Pos = helper.absolutePos(grass2RelPos).above();

        var dominion = DominionSavedData.get(level).createDominion(UUID.randomUUID(), "Test Dominion", markerPos);
        var structure = new DominionStructure("Nursery", StructureType.NURSERY, markerPos, Set.of(markerPos));
        dominion.addStructure(structure);

        var inhabitant = helper.spawn(ModEntities.INHABITANT.get(), markerPos);
        inhabitant.setDominionId(dominion.getId());
        inhabitant.setWorkStructureId(structure.getId());
        inhabitant.getInventory().addItem(new ItemStack(Items.OAK_SAPLING, 8));

        var workGoal = new WorkGoal(inhabitant);
        workGoal.canUse();
        workGoal.start();

        inhabitant.snapTo(markerPos.getX() + 0.5, markerPos.getY(), markerPos.getZ() + 0.5);
        workGoal.tick();

        // Which of the two grass spots gets planted first is now picked at random, so rather than
        // guessing the order, snap to the midpoint between them each tick - close enough (1.5 blocks)
        // to either plant spot to register as "arrived" no matter which one the job is aiming for.
        var midX = (planted1Pos.getX() + planted2Pos.getX()) / 2.0 + 0.5;
        var midY = planted1Pos.getY();
        var midZ = (planted1Pos.getZ() + planted2Pos.getZ()) / 2.0 + 0.5;

        var returnedToHutBetweenPlantings = new boolean[] {false};
        for (int i = 0; i < 60; i++) {
            inhabitant.snapTo(midX, midY, midZ);
            workGoal.tick();
            if ("Heading to work".equals(workGoal.getStatusDescription())
                    && level.getBlockState(planted1Pos).is(Blocks.OAK_SAPLING)
                    && !level.getBlockState(planted2Pos).is(Blocks.OAK_SAPLING)) {
                returnedToHutBetweenPlantings[0] = true;
            }
            if (level.getBlockState(planted1Pos).is(Blocks.OAK_SAPLING) && level.getBlockState(planted2Pos).is(Blocks.OAK_SAPLING)) {
                break;
            }
        }

        helper.succeedWhen(() -> {
            helper.assertTrue(level.getBlockState(planted1Pos).is(Blocks.OAK_SAPLING), "First spot should have a sapling planted");
            helper.assertTrue(level.getBlockState(planted2Pos).is(Blocks.OAK_SAPLING), "Second spot should have a sapling planted");
            helper.assertTrue(!returnedToHutBetweenPlantings[0],
                    "The forester should carry on to the next spot instead of walking back to the hut between plantings");
        });
    }

    /**
     * Planting should consume exactly one sapling from the forester's carried stack, leaving the
     * rest for the next planting cycle rather than dropping the whole stack.
     */
    public static void plantingASaplingConsumesOnlyOneFromTheStack(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);

        var markerRelPos = new BlockPos(2, 1, 2);
        var grassRelPos = new BlockPos(4, 1, 2);

        helper.setBlock(grassRelPos, Blocks.GRASS_BLOCK);

        var level = helper.getLevel();
        var markerPos = helper.absolutePos(markerRelPos);
        var grassPos = helper.absolutePos(grassRelPos);
        var plantedPos = grassPos.above();

        var dominion = DominionSavedData.get(level).createDominion(UUID.randomUUID(), "Test Dominion", markerPos);
        var structure = new DominionStructure("Nursery", StructureType.NURSERY, markerPos, Set.of(markerPos));
        dominion.addStructure(structure);

        var inhabitant = helper.spawn(ModEntities.INHABITANT.get(), markerPos);
        inhabitant.setDominionId(dominion.getId());
        inhabitant.setWorkStructureId(structure.getId());
        inhabitant.getInventory().addItem(new ItemStack(Items.OAK_SAPLING, 8));

        var workGoal = new WorkGoal(inhabitant);
        workGoal.canUse();
        workGoal.start();

        inhabitant.snapTo(markerPos.getX() + 0.5, markerPos.getY(), markerPos.getZ() + 0.5);
        workGoal.tick();
        inhabitant.snapTo(plantedPos.getX() + 0.5, plantedPos.getY(), plantedPos.getZ() + 0.5);
        for (int i = 0; i < 20 && !"Heading to work".equals(workGoal.getStatusDescription()); i++) {
            workGoal.tick();
        }

        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getLevel().getBlockState(plantedPos).is(Blocks.OAK_SAPLING),
                    "The bare grass spot should have a sapling planted on it");
            int remaining = 0;
            var carried = inhabitant.getInventory();
            for (int i = 0; i < carried.getContainerSize(); i++) {
                var stack = carried.getItem(i);
                if (stack.is(Items.OAK_SAPLING)) remaining += stack.getCount();
            }
            helper.assertTrue(remaining == 7, "Planting one sapling should leave 7 in the stack, but found " + remaining);
        });
    }
}
