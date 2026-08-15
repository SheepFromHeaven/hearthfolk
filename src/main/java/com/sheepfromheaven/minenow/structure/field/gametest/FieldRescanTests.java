package com.sheepfromheaven.minenow.structure.field.gametest;

import com.sheepfromheaven.minenow.dominion.DominionSavedData;
import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import com.sheepfromheaven.minenow.structure.field.Field;
import com.sheepfromheaven.minenow.structure.field.FieldAreaDetector;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

import java.util.Set;
import java.util.UUID;

// Registered into the vanilla test_function registry by MineNow's RegisterEvent listener;
// the matching data/minenow/test_instance/*.json files supply the structure and tick budget.
public class FieldRescanTests {

    /**
     * A field's claimed area isn't fixed at placement time - a player can dig out more ground after
     * the fact (removing a wall block that used to stop the flood-fill), and the periodic rescan (see
     * {@code DominionSavedData#tickFieldRescan}) should pick up the newly-opened tiles on its own,
     * without the marker being replaced.
     */
    public static void periodicRescanGrowsFieldAfterWallRemoved(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);

        // A 3x3 pit (x1-3, z1-3) plus a 1-wide corridor (x5, z1-3) sitting right next to it, separated
        // by a standing wall block at head height only (x4, z1-3, y=2) - the ground itself (y=1) is
        // continuous dirt the whole way across, same as a player digging away a free-standing wall
        // rather than the floor. A full-height stone ring bounds the whole 7x5 area so the fill can't
        // leak anywhere else.
        for (int x = 0; x <= 6; x++) {
            for (int z = 0; z <= 4; z++) {
                boolean boundary = x == 0 || x == 6 || z == 0 || z == 4;
                if (boundary) {
                    helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
                    helper.setBlock(new BlockPos(x, 2, z), Blocks.STONE);
                } else {
                    helper.setBlock(new BlockPos(x, 1, z), Blocks.DIRT);
                    if (x == 4) {
                        helper.setBlock(new BlockPos(x, 2, z), Blocks.STONE);
                    }
                }
            }
        }

        var level = helper.getLevel();
        var markerPos = helper.absolutePos(new BlockPos(2, 2, 2));
        var below = helper.absolutePos(new BlockPos(2, 1, 2));

        var initial = FieldAreaDetector.detect(level, below, markerPos, com.sheepfromheaven.minenow.core.Config.maxRoomSize);
        if (!(initial instanceof FieldAreaDetector.Result.Success(var initialBlocks))) {
            helper.fail("Setup should have detected the initial 3x3 pit");
            return;
        }

        var data = DominionSavedData.get(level);
        var dominion = data.createDominion(UUID.randomUUID(), "Test Dominion", markerPos);
        var field = new Field(markerPos, initialBlocks);
        dominion.addField(field);

        // Knock down the standing wall - the field should grow to include the corridor once rescanned,
        // the same way it would if a player mined it out mid-game.
        for (int z = 1; z <= 3; z++) {
            level.setBlockAndUpdate(helper.absolutePos(new BlockPos(4, 2, z)), Blocks.AIR.defaultBlockState());
        }

        helper.assertTrue(field.getArea().size() == 9, "Field shouldn't have grown before any rescan happened");

        data.tickFieldRescan(level);

        helper.succeedWhen(() -> helper.assertTrue(field.getArea().size() == 15,
                "Field should have grown to include the 3-tile wall column and 3-tile corridor after a rescan, got " + field.getArea().size()));
    }
}
