package com.sheepfromheaven.minenow.gametest;

import com.sheepfromheaven.minenow.dominion.DominionStructure;
import com.sheepfromheaven.minenow.dominion.StructureType;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;

import java.util.Set;

// Registered into the vanilla test_function registry by MineNow's RegisterEvent listener;
// the matching data/minenow/test_instance/*.json files supply the structure and tick budget.
public class WorkAreaTests {

    public static void defaultWorkAreaIsCenteredOnMarkerWithDefaultRadius(GameTestHelper helper) {
        helper.makeFloor();

        var markerPos = helper.absolutePos(new BlockPos(4, 1, 4));
        var structure = new DominionStructure("Lumberyard", StructureType.LUMBERYARD, markerPos, Set.of(markerPos));

        helper.succeedWhen(() -> {
            helper.assertTrue(structure.getWorkAreaCenter().equals(markerPos),
                    "A freshly created structure's work area should default to being centered on the marker");
            helper.assertTrue(structure.getWorkAreaRadius() == DominionStructure.DEFAULT_WORK_AREA_RADIUS,
                    "A freshly created structure's work area radius should default to DEFAULT_WORK_AREA_RADIUS");
        });
    }

    public static void settingWorkAreaCenterAndRadiusIsRespected(GameTestHelper helper) {
        helper.makeFloor();

        var markerPos = helper.absolutePos(new BlockPos(4, 1, 4));
        var newCenter = helper.absolutePos(new BlockPos(4, 1, 8));
        var structure = new DominionStructure("Lumberyard", StructureType.LUMBERYARD, markerPos, Set.of(markerPos));

        structure.setWorkAreaCenter(newCenter);
        structure.setWorkAreaRadius(5);

        helper.succeedWhen(() -> {
            helper.assertTrue(structure.getWorkAreaCenter().equals(newCenter),
                    "The work area center should be exactly what was set");
            helper.assertTrue(structure.getWorkAreaRadius() == 5,
                    "The work area radius should be exactly what was set");
        });
    }

    public static void workAreaRadiusIsClampedToValidRange(GameTestHelper helper) {
        helper.makeFloor();

        var markerPos = helper.absolutePos(new BlockPos(4, 1, 4));
        var structure = new DominionStructure("Lumberyard", StructureType.LUMBERYARD, markerPos, Set.of(markerPos));

        structure.setWorkAreaRadius(1);
        boolean clampedToMin = structure.getWorkAreaRadius() == DominionStructure.MIN_WORK_AREA_RADIUS;

        structure.setWorkAreaRadius(1000);
        boolean clampedToMax = structure.getWorkAreaRadius() == DominionStructure.MAX_WORK_AREA_RADIUS;

        helper.succeedWhen(() -> {
            helper.assertTrue(clampedToMin, "Setting a radius below the minimum should clamp to MIN_WORK_AREA_RADIUS");
            helper.assertTrue(clampedToMax, "Setting a radius above the maximum should clamp to MAX_WORK_AREA_RADIUS");
        });
    }

    public static void findTreeInRangeIgnoresTreeOutsideMovedWorkArea(GameTestHelper helper) {
        helper.makeFloor();

        var markerRelPos = new BlockPos(4, 1, 4);
        var soilRelPos = new BlockPos(4, 0, 6);
        var logRelPos = new BlockPos(4, 1, 6);
        var leafRelPos = new BlockPos(5, 1, 6);

        helper.setBlock(soilRelPos, Blocks.GRASS_BLOCK);
        helper.setBlock(logRelPos, Blocks.OAK_LOG);
        helper.setBlock(leafRelPos, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false));

        var markerPos = helper.absolutePos(markerRelPos);
        var farAwayCenter = markerPos.offset(0, 0, -100);
        var structure = new DominionStructure("Lumberyard", StructureType.LUMBERYARD, markerPos, Set.of(markerPos));
        structure.setWorkAreaCenter(farAwayCenter);
        structure.setWorkAreaRadius(DominionStructure.MIN_WORK_AREA_RADIUS);

        var tree = structure.findTreeInRange(helper.getLevel());

        helper.succeedWhen(() ->
                helper.assertTrue(tree.isEmpty(),
                        "A tree within the structure's default range but outside the configured work area should not be found"));
    }

    public static void findTreeInRangeFindsTreeAfterMovingWorkAreaCenter(GameTestHelper helper) {
        helper.makeFloor();

        var markerRelPos = new BlockPos(4, 1, 4);
        var soilRelPos = new BlockPos(4, 0, 7);
        var logRelPos = new BlockPos(4, 1, 7);
        var leafRelPos = new BlockPos(5, 1, 7);

        helper.setBlock(soilRelPos, Blocks.GRASS_BLOCK);
        helper.setBlock(logRelPos, Blocks.OAK_LOG);
        helper.setBlock(leafRelPos, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false));

        var markerPos = helper.absolutePos(markerRelPos);
        var logPos = helper.absolutePos(logRelPos);
        var structure = new DominionStructure("Lumberyard", StructureType.LUMBERYARD, markerPos, Set.of(markerPos));
        // The default work area radius (MIN_WORK_AREA_RADIUS) is too small to reach the tree from the
        // marker's original center - only moving the work area center onto the tree makes it discoverable.
        structure.setWorkAreaCenter(logPos);
        structure.setWorkAreaRadius(DominionStructure.MIN_WORK_AREA_RADIUS);

        var tree = structure.findTreeInRange(helper.getLevel());

        helper.succeedWhen(() -> {
            helper.assertTrue(tree.isPresent(), "The tree should be found once the work area center is moved close to it");
            helper.assertTrue(tree.get().contains(logPos), "The found tree should contain the trunk log");
        });
    }
}
