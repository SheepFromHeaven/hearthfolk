package com.sheepfromheaven.minenow.jobs.gametest;

import com.sheepfromheaven.minenow.dominion.DominionSavedData;
import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import com.sheepfromheaven.minenow.inhabitant.ModEntities;
import com.sheepfromheaven.minenow.jobs.WorkGoal;
import com.sheepfromheaven.minenow.structure.DominionStructure;
import com.sheepfromheaven.minenow.structure.StructureType;
import com.sheepfromheaven.minenow.structure.field.CropKind;
import com.sheepfromheaven.minenow.structure.field.Field;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

// Registered into the vanilla test_function registry by MineNow's RegisterEvent listener;
// the matching data/minenow/test_instance/*.json files supply the structure and tick budget.
public class FarmerJobTests {

    /**
     * The farmer should harvest a fully-grown wheat crop in its work area, replant it from a seed
     * taken out of its own carried inventory, and end up carrying the harvested wheat.
     */
    public static void farmerHarvestsAndReplantsAMatureCrop(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);

        var markerRelPos = new BlockPos(2, 1, 2);
        var barrelRelPos = new BlockPos(1, 1, 1);
        var farmlandRelPos = new BlockPos(4, 1, 2);
        helper.setBlock(barrelRelPos, Blocks.BARREL);
        helper.setBlock(farmlandRelPos, Blocks.FARMLAND);
        helper.setBlock(farmlandRelPos.above(), ((CropBlock) Blocks.WHEAT).getStateForAge(7));

        var level = helper.getLevel();
        var markerPos = helper.absolutePos(markerRelPos);
        var barrelPos = helper.absolutePos(barrelRelPos);
        var cropPos = helper.absolutePos(farmlandRelPos.above());

        var dominion = DominionSavedData.get(level).createDominion(UUID.randomUUID(), "Test Dominion", markerPos);
        var structure = new DominionStructure("Farm", StructureType.FARM, markerPos, Set.of(markerPos, barrelPos));
        dominion.addStructure(structure);
        // A field marker over the farmland is now the farmer's work condition (replaces the old base work area).
        var fieldPos = helper.absolutePos(farmlandRelPos);
        var field = new Field(fieldPos, Set.of(fieldPos));
        field.setAssignedStructureId(structure.getId());
        dominion.addField(field);

        var inhabitant = helper.spawn(ModEntities.INHABITANT.get(), markerPos);
        inhabitant.setDominionId(dominion.getId());
        inhabitant.setWorkStructureId(structure.getId());
        inhabitant.getInventory().addItem(new ItemStack(Items.WHEAT_SEEDS, 4));

        var workGoal = new WorkGoal(inhabitant);
        workGoal.canUse();
        workGoal.start();

        // GameTest mobs don't actually walk along their path just from calling tick() - mirror the
        // other jobs' tests (e.g. LumberjackLeafHarvestTests) by snapping to each waypoint directly
        // as the job transitions phases, rather than relying on real pathfinding movement.
        inhabitant.snapTo(markerPos.getX() + 0.5, markerPos.getY(), markerPos.getZ() + 0.5);
        workGoal.tick();
        inhabitant.snapTo(cropPos.getX() + 0.5, cropPos.getY(), cropPos.getZ() + 0.5);
        for (int i = 0; i < 20 && !"Returning to hut".equals(workGoal.getStatusDescription()); i++) {
            workGoal.tick();
        }

        helper.succeedWhen(() -> {
            var state = helper.getLevel().getBlockState(cropPos);
            helper.assertTrue(state.is(Blocks.WHEAT) && state.getValue(CropBlock.AGE) == 0,
                    "Harvested crop should have been replanted at age 0");
            boolean carryingWheat = false;
            var carried = inhabitant.getInventory();
            for (int i = 0; i < carried.getContainerSize(); i++) {
                if (carried.getItem(i).is(Items.WHEAT)) carryingWheat = true;
            }
            helper.assertTrue(carryingWheat, "Farmer should be carrying the harvested wheat");
        });
    }

    /**
     * A farm can have multiple fields assigned to it, and each field's own crop allowance list is
     * respected independently: a mature crop of a type this field doesn't allow should be left alone,
     * while a mature crop of an allowed type on a different assigned field gets harvested.
     */
    public static void farmerRespectsFieldCropAllowanceAcrossMultipleFields(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);

        var markerRelPos = new BlockPos(2, 1, 2);
        var barrelRelPos = new BlockPos(1, 1, 1);
        var wheatFarmlandRelPos = new BlockPos(4, 1, 2);
        var carrotFarmlandRelPos = new BlockPos(6, 1, 2);
        helper.setBlock(barrelRelPos, Blocks.BARREL);
        helper.setBlock(wheatFarmlandRelPos, Blocks.FARMLAND);
        helper.setBlock(wheatFarmlandRelPos.above(), ((CropBlock) Blocks.WHEAT).getStateForAge(7));
        helper.setBlock(carrotFarmlandRelPos, Blocks.FARMLAND);
        helper.setBlock(carrotFarmlandRelPos.above(), ((CropBlock) Blocks.CARROTS).getStateForAge(7));

        var level = helper.getLevel();
        var markerPos = helper.absolutePos(markerRelPos);
        var barrelPos = helper.absolutePos(barrelRelPos);
        var wheatFarmlandPos = helper.absolutePos(wheatFarmlandRelPos);
        var wheatCropPos = helper.absolutePos(wheatFarmlandRelPos.above());
        var carrotFarmlandPos = helper.absolutePos(carrotFarmlandRelPos);
        var carrotCropPos = helper.absolutePos(carrotFarmlandRelPos.above());

        var dominion = DominionSavedData.get(level).createDominion(UUID.randomUUID(), "Test Dominion", markerPos);
        var structure = new DominionStructure("Farm", StructureType.FARM, markerPos, Set.of(markerPos, barrelPos));
        dominion.addStructure(structure);

        // Wheat is explicitly disallowed on this field - only carrots may be planted here.
        var wheatField = new Field(UUID.randomUUID(), markerPos, Set.of(wheatFarmlandPos), EnumSet.of(CropKind.CARROTS), structure.getId());
        // Second, separately-assigned field where carrots (and everything else) is allowed.
        var carrotField = new Field(carrotFarmlandPos, Set.of(carrotFarmlandPos));
        carrotField.setAssignedStructureId(structure.getId());
        dominion.addField(wheatField);
        dominion.addField(carrotField);

        var inhabitant = helper.spawn(ModEntities.INHABITANT.get(), markerPos);
        inhabitant.setDominionId(dominion.getId());
        inhabitant.setWorkStructureId(structure.getId());
        inhabitant.getInventory().addItem(new ItemStack(Items.CARROT, 4));

        var workGoal = new WorkGoal(inhabitant);
        workGoal.canUse();
        workGoal.start();

        inhabitant.snapTo(markerPos.getX() + 0.5, markerPos.getY(), markerPos.getZ() + 0.5);
        workGoal.tick();
        inhabitant.snapTo(carrotCropPos.getX() + 0.5, carrotCropPos.getY(), carrotCropPos.getZ() + 0.5);
        for (int i = 0; i < 20 && !"Returning to hut".equals(workGoal.getStatusDescription()); i++) {
            workGoal.tick();
        }

        helper.succeedWhen(() -> {
            var wheatState = helper.getLevel().getBlockState(wheatCropPos);
            helper.assertTrue(wheatState.is(Blocks.WHEAT) && wheatState.getValue(CropBlock.AGE) == 7,
                    "Wheat should be left untouched - this field doesn't allow it");

            var carrotState = helper.getLevel().getBlockState(carrotCropPos);
            helper.assertTrue(carrotState.is(Blocks.CARROTS) && carrotState.getValue(CropBlock.AGE) == 0,
                    "Carrots on the other assigned field should have been harvested and replanted");

            boolean carryingCarrot = false;
            var carried = inhabitant.getInventory();
            for (int i = 0; i < carried.getContainerSize(); i++) {
                if (carried.getItem(i).is(Items.CARROT)) carryingCarrot = true;
            }
            helper.assertTrue(carryingCarrot, "Farmer should be carrying the harvested carrot");
        });
    }
}
