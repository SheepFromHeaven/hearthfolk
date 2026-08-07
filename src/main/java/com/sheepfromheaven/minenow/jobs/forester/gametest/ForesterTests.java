package com.sheepfromheaven.minenow.jobs.forester.gametest;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import com.sheepfromheaven.minenow.structure.DominionStructure;
import com.sheepfromheaven.minenow.structure.StructureType;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

import java.util.Set;

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
}
