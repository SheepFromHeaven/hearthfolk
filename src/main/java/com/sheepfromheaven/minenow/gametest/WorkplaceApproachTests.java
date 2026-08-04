package com.sheepfromheaven.minenow.gametest;

import com.sheepfromheaven.minenow.dominion.DominionStructure;
import com.sheepfromheaven.minenow.dominion.StructureType;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;

import java.util.Set;

// Registered into the vanilla test_function registry by MineNow's RegisterEvent listener;
// the matching data/minenow/test_instance/*.json files supply the structure and tick budget.
public class WorkplaceApproachTests {

    public static void approachPosIsMarkerWhenMarkerIsOnTheFloor(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);

        // makeFloor() fills the layer one below the test area (relative y = -1), so y = 0 is standing height.
        var markerRelPos = new BlockPos(4, 0, 4);
        var markerPos = helper.absolutePos(markerRelPos);
        var structure = new DominionStructure("Lumberyard", StructureType.LUMBERYARD, markerPos, Set.of(markerPos));

        var approachPos = structure.findApproachPos(helper.getLevel());

        helper.succeedWhen(() ->
                helper.assertTrue(approachPos.equals(markerPos),
                        "A marker already standing on a floor tile should be its own approach position"));
    }

    public static void approachPosFindsFloorWhenMarkerIsEmbeddedHighInAShaft(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);

        // No floor directly under the marker - as if it were placed against a wall or ceiling several
        // blocks above the real floor, with nothing but open air in the column below it.
        var markerRelPos = new BlockPos(4, 3, 4);
        var markerPos = helper.absolutePos(markerRelPos);
        var structure = new DominionStructure("Lumberyard", StructureType.LUMBERYARD, markerPos, Set.of(markerPos));

        var approachPos = structure.findApproachPos(helper.getLevel());

        helper.succeedWhen(() -> {
            helper.assertTrue(!approachPos.equals(markerPos),
                    "A marker with no floor beneath it should not be its own approach position");
            var below = approachPos.below();
            helper.assertTrue(!helper.getLevel().getBlockState(below).getCollisionShape(helper.getLevel(), below).isEmpty(),
                    "The approach position should have solid ground beneath it");
            helper.assertTrue(helper.getLevel().getBlockState(approachPos).getCollisionShape(helper.getLevel(), approachPos).isEmpty(),
                    "The approach position itself should be walkable, not solid");
        });
    }
}
