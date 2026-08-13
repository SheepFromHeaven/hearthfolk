package com.sheepfromheaven.minenow.structure.field.gametest;

import com.sheepfromheaven.minenow.core.Config;
import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import com.sheepfromheaven.minenow.structure.field.FieldAreaDetector;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

// Registered into the vanilla test_function registry by MineNow's RegisterEvent listener;
// the matching data/minenow/test_instance/*.json files supply the structure and tick budget.
public class FieldAreaDetectorTests {

    /**
     * A 3x3 pit dug into flat ground, with the marker sitting on the pit floor: the flood fill should
     * spread sideways across the whole 3x3 floor (the marker's own tile plus its 8 neighbors) and stop
     * at the surrounding 2-tall walls, rather than needing the patch to already be an enclosed room.
     */
    public static void detectsFlat3x3Pit(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);

        // Pit floor at (1..3, 1, 1..3); 2-tall stone walls ring the outside so sideways spread stops there.
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 4; z++) {
                boolean insidePit = x >= 1 && x <= 3 && z >= 1 && z <= 3;
                if (insidePit) {
                    helper.setBlock(new BlockPos(x, 1, z), Blocks.DIRT);
                } else {
                    helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
                    helper.setBlock(new BlockPos(x, 2, z), Blocks.STONE);
                }
            }
        }

        var level = helper.getLevel();
        var markerPos = helper.absolutePos(new BlockPos(2, 2, 2));
        var below = helper.absolutePos(new BlockPos(2, 1, 2));

        var result = FieldAreaDetector.detect(level, below, markerPos, Config.maxRoomSize);

        helper.succeedWhen(() -> {
            switch (result) {
                case FieldAreaDetector.Result.Success(var blocks) ->
                        helper.assertTrue(blocks.size() == 9, "Expected all 9 pit floor tiles, got " + blocks.size());
                case FieldAreaDetector.Result.TooLarge ignored -> helper.fail("Fill unexpectedly leaked past the pit walls (too large)");
                case FieldAreaDetector.Result.NotEnclosed ignored -> helper.fail("Marker's own floor tile should have been walkable ground");
            }
        });
    }

    /**
     * Same 3x3 pit, but the center tile has been dug one block deeper than the other 8 - like water
     * poured into a deeper spot, the fill should claim both the empty tile it flows through on the way
     * down and the new landing at the bottom, not just the final landing. 8 ring tiles + the center's
     * two levels = 10.
     */
    public static void detectsFlat3x3PitWithDeeperCenter(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);

        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 4; z++) {
                boolean insidePit = x >= 1 && x <= 3 && z >= 1 && z <= 3;
                boolean isCenter = x == 2 && z == 2;
                if (insidePit && isCenter) {
                    helper.setBlock(new BlockPos(x, 0, z), Blocks.DIRT);
                    helper.setBlock(new BlockPos(x, 1, z), Blocks.AIR);
                    helper.setBlock(new BlockPos(x, 2, z), Blocks.AIR);
                } else if (insidePit) {
                    // Solid below the ring floor so the extra depth at the center doesn't leak sideways.
                    helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                    helper.setBlock(new BlockPos(x, 1, z), Blocks.DIRT);
                    helper.setBlock(new BlockPos(x, 2, z), Blocks.AIR);
                } else {
                    helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                    helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
                    helper.setBlock(new BlockPos(x, 2, z), Blocks.STONE);
                }
            }
        }

        var level = helper.getLevel();
        var markerPos = helper.absolutePos(new BlockPos(1, 2, 1));
        var below = helper.absolutePos(new BlockPos(1, 1, 1));

        var result = FieldAreaDetector.detect(level, below, markerPos, Config.maxRoomSize);

        helper.succeedWhen(() -> {
            switch (result) {
                case FieldAreaDetector.Result.Success(var blocks) ->
                        helper.assertTrue(blocks.size() == 10, "Expected 10 tiles (8 ring + 2 center levels), got " + blocks.size());
                case FieldAreaDetector.Result.TooLarge ignored -> helper.fail("Fill unexpectedly leaked past the pit walls (too large)");
                case FieldAreaDetector.Result.NotEnclosed ignored -> helper.fail("Marker's own floor tile should have been walkable ground");
            }
        });
    }
}
