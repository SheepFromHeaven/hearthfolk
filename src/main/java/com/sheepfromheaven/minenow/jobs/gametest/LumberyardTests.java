package com.sheepfromheaven.minenow.jobs.gametest;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;

import com.sheepfromheaven.minenow.structure.DominionStructure;
import com.sheepfromheaven.minenow.structure.StructureType;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;

import java.util.Set;

// Registered into the vanilla test_function registry by MineNow's RegisterEvent listener;
// the matching data/minenow/test_instance/*.json files supply the structure and tick budget.
public class LumberyardTests {

    public static void findTreeInRangeFindsValidTree(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);

        var markerRelPos = new BlockPos(4, 1, 4);
        var soilRelPos = new BlockPos(4, 0, 6);
        var logRelPos = new BlockPos(4, 1, 6);
        var leafRelPos = new BlockPos(5, 1, 6);

        helper.setBlock(soilRelPos, Blocks.GRASS_BLOCK);
        helper.setBlock(logRelPos, Blocks.OAK_LOG);
        helper.setBlock(leafRelPos, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false));

        var markerPos = helper.absolutePos(markerRelPos);
        var logPos = helper.absolutePos(logRelPos);
        var structure = new DominionStructure("Lumberyard", StructureType.LUMBERYARD, markerPos, Set.of(markerPos));

        var tree = structure.findTreeInRange(helper.getLevel());

        helper.succeedWhen(() -> {
            helper.assertTrue(tree.isPresent(), "A valid tree within range should be found");
            helper.assertTrue(tree.get().contains(logPos), "The found tree should contain the trunk log");
        });
    }

    public static void findTreeInRangeSkipsLogPillarWithoutLeaves(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);

        var markerRelPos = new BlockPos(4, 1, 4);
        var soilRelPos = new BlockPos(4, 0, 6);
        var logRelPos = new BlockPos(4, 1, 6);

        helper.setBlock(soilRelPos, Blocks.GRASS_BLOCK);
        helper.setBlock(logRelPos, Blocks.OAK_LOG);

        var markerPos = helper.absolutePos(markerRelPos);
        var structure = new DominionStructure("Lumberyard", StructureType.LUMBERYARD, markerPos, Set.of(markerPos));

        var tree = structure.findTreeInRange(helper.getLevel());

        helper.succeedWhen(() ->
                helper.assertTrue(tree.isEmpty(), "A bare log pillar with no leaves should not be recognized as a tree"));
    }

    public static void findTreeInRangeIgnoresNearbyLogHouseAndStillFindsRealTree(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);

        var markerRelPos = new BlockPos(4, 1, 4);

        var houseCornerRelPos = new BlockPos(1, 1, 1);
        helper.setBlock(houseCornerRelPos, Blocks.OAK_LOG);

        var soilRelPos = new BlockPos(4, 0, 6);
        var logRelPos = new BlockPos(4, 1, 6);
        var leafRelPos = new BlockPos(5, 1, 6);

        helper.setBlock(soilRelPos, Blocks.GRASS_BLOCK);
        helper.setBlock(logRelPos, Blocks.OAK_LOG);
        helper.setBlock(leafRelPos, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false));

        var markerPos = helper.absolutePos(markerRelPos);
        var logPos = helper.absolutePos(logRelPos);
        var houseCornerPos = helper.absolutePos(houseCornerRelPos);
        var structure = new DominionStructure("Lumberyard", StructureType.LUMBERYARD, markerPos, Set.of(markerPos));

        var tree = structure.findTreeInRange(helper.getLevel());

        helper.succeedWhen(() -> {
            helper.assertTrue(tree.isPresent(), "The real tree should be found even with an unrelated log pillar nearby");
            helper.assertTrue(tree.get().contains(logPos), "The found tree should contain the real trunk log");
            helper.assertTrue(!tree.get().contains(houseCornerPos), "The unrelated log house corner should never be included");
        });
    }
}
