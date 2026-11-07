package com.sheepfromheaven.minenow.inhabitant.gametest;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import com.sheepfromheaven.minenow.inhabitant.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.AABB;

// Registered into the vanilla test_function registry by MineNow's RegisterEvent listener;
// the matching data/minenow/test_instance/*.json files supply the structure and tick budget.
public class InhabitantConfinementTests {

    /**
     * Reproduces the "idle inhabitants wander out of their gametest room and into a neighboring
     * test" bug seen running `/test runmultiple` in bulk: with no walls, an inhabitant's idle
     * WaterAvoidingRandomStrollGoal paths it clean out of the structure's bounds. Barrier-walling
     * the test space (GameTestHelpers.barrierBounds) should keep it inside for the whole run.
     */
    public static void barrierBoundsKeepsIdleInhabitantInsideTestSpace(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);

        var markerRelPos = new BlockPos(4, 1, 4);
        helper.spawn(ModEntities.INHABITANT.get(), markerRelPos);

        var bounds = helper.getBounds();
        var relativeBounds = new AABB(0, 0, 0,
                bounds.maxX - bounds.minX, bounds.maxY - bounds.minY, bounds.maxZ - bounds.minZ);

        helper.runAfterDelay(100, () -> {
            helper.assertEntityPresent(ModEntities.INHABITANT.get(), relativeBounds);
            helper.succeed();
        });
    }
}
