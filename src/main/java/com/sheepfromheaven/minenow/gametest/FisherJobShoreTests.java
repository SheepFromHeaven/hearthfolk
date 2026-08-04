package com.sheepfromheaven.minenow.gametest;

import com.sheepfromheaven.minenow.entity.job.ShoreScanner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

// Registered into the vanilla test_function registry by MineNow's RegisterEvent listener;
// the matching data/minenow/test_instance/*.json files supply the structure and tick budget.
public class FisherJobShoreTests {

    /**
     * Reproduces the "fisher always ends up standing IN a 1-block pond instead of beside it" bug: a
     * well-shaped pond (water sunk one block below its walkable rim, walled in laterally at the water's
     * own level - a shape as common as the vanilla village well) left every same-level neighbor of the
     * water blocked, so the old shore search fell back to "just stand in the water". The shore scan
     * should instead find a dry tile on the rim one block up.
     */
    public static void findShorePositionUsesRimOfSunkenWellInsteadOfTheWaterItself(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);

        var waterRelPos = new BlockPos(4, 1, 4);
        // A well: water sunk into a ring of solid walls at its own level, with the rim (one block up,
        // directly above each wall block) left open to stand on.
        for (var dir : Direction.Plane.HORIZONTAL) {
            helper.setBlock(waterRelPos.relative(dir), Blocks.STONE_BRICKS);
        }
        helper.setBlock(waterRelPos, Blocks.WATER);

        var level = helper.getLevel();
        var waterPos = helper.absolutePos(waterRelPos);

        var shorePos = ShoreScanner.findShorePosition(level, waterPos);

        helper.succeedWhen(() -> {
            helper.assertTrue(shorePos.isPresent(), "Shore search should find the well's rim");
            helper.assertTrue(shorePos.get().getY() == waterPos.getY() + 1,
                    "Shore search should find a tile on the well's rim, one block above the water");
        });
    }

    /**
     * A normal puddle flush with the surrounding ground (both the water and its neighbors sit on the
     * same solid floor, with nothing walling the water in at its own level) should resolve to the
     * same-level ring, not the rim - the rim fallback must not take priority over an ordinary shore.
     */
    public static void findShorePositionPrefersSameLevelRingOverRim(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);

        // makeFloor() fills the layer one below the test area (relative y = -1), so y = 0 is standing
        // height - the water and its neighbors need to sit there to be flush with the ground.
        var waterRelPos = new BlockPos(4, 0, 4);
        helper.setBlock(waterRelPos, Blocks.WATER);

        var level = helper.getLevel();
        var waterPos = helper.absolutePos(waterRelPos);

        var shorePos = ShoreScanner.findShorePosition(level, waterPos);

        helper.succeedWhen(() -> {
            helper.assertTrue(shorePos.isPresent(), "Shore search should find a same-level shore tile");
            helper.assertTrue(shorePos.get().getY() == waterPos.getY(),
                    "An ordinary flush-with-ground puddle should resolve to a same-level shore tile, not the rim");
        });
    }
}
