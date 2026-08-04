package com.sheepfromheaven.minenow.gametest;

import com.sheepfromheaven.minenow.dominion.DominionStructure;
import com.sheepfromheaven.minenow.dominion.StructureType;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

import java.util.Set;
import java.util.UUID;

// Registered into the vanilla test_function registry by MineNow's RegisterEvent listener;
// the matching data/minenow/test_instance/*.json files supply the structure and tick budget.
public class FisheryStorageTests {

    public static void storageContainersSkipsWorkstations(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);

        var barrelRelPos = new BlockPos(1, 1, 1);
        var smokerRelPos = new BlockPos(3, 1, 1);
        helper.setBlock(barrelRelPos, Blocks.BARREL);
        helper.setBlock(smokerRelPos, Blocks.SMOKER);

        var barrelPos = helper.absolutePos(barrelRelPos);
        var smokerPos = helper.absolutePos(smokerRelPos);
        var markerPos = helper.absolutePos(new BlockPos(2, 1, 2));

        var structure = new DominionStructure("Fishery", StructureType.FISHERY, markerPos,
                Set.of(barrelPos, smokerPos));

        var containers = structure.findStorageContainers(helper.getLevel());

        helper.succeedWhen(() -> {
            helper.assertTrue(containers.contains(barrelPos),
                    "Barrel should be found as a storage container");
            helper.assertTrue(!containers.contains(smokerPos),
                    "Smoker should not be treated as a storage container");
        });
    }

    public static void storageContainersFindsExtraContainersNotJustRequiredBarrel(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);

        var barrelRelPos = new BlockPos(1, 1, 1);
        var chestRelPos = new BlockPos(3, 1, 1);
        helper.setBlock(barrelRelPos, Blocks.BARREL);
        helper.setBlock(chestRelPos, Blocks.CHEST);

        var barrelPos = helper.absolutePos(barrelRelPos);
        var chestPos = helper.absolutePos(chestRelPos);
        var markerPos = helper.absolutePos(new BlockPos(2, 1, 2));

        var structure = new DominionStructure("Fishery", StructureType.FISHERY, markerPos,
                Set.of(barrelPos, chestPos));

        var containers = structure.findStorageContainers(helper.getLevel());

        helper.succeedWhen(() -> {
            helper.assertTrue(containers.contains(barrelPos),
                    "Required barrel should be found as a storage container");
            helper.assertTrue(containers.contains(chestPos),
                    "Extra chest should also be found as a storage container");
        });
    }
}
