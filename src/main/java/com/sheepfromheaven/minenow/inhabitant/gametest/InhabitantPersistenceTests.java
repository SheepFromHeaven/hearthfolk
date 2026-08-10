package com.sheepfromheaven.minenow.inhabitant.gametest;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import com.sheepfromheaven.minenow.inhabitant.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

// Registered into the vanilla test_function registry by MineNow's RegisterEvent listener;
// the matching data/minenow/test_instance/*.json files supply the structure and tick budget.
public class InhabitantPersistenceTests {

    /**
     * Reproduces the "inhabitant inventory is empty after rejoining a world" bug: the carried
     * general-job inventory was never written to/read from the entity's save data, so a save/load
     * round trip silently dropped everything an inhabitant was carrying.
     */
    public static void inventorySurvivesASaveAndLoadRoundTrip(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);

        var markerRelPos = new BlockPos(2, 1, 2);
        var level = helper.getLevel();

        var inhabitant = helper.spawn(ModEntities.INHABITANT.get(), markerRelPos);
        inhabitant.getInventory().addItem(new ItemStack(Items.OAK_SAPLING, 5));

        var output = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        inhabitant.saveWithoutId(output);
        var savedTag = output.buildResult();

        var reloaded = helper.spawn(ModEntities.INHABITANT.get(), markerRelPos);
        reloaded.getInventory().clearContent();
        var input = TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), savedTag);
        reloaded.load(input);

        helper.succeedWhen(() ->
                helper.assertTrue(reloaded.getInventory().countItem(Items.OAK_SAPLING) == 5,
                        "The reloaded inhabitant should still be carrying the saplings it had before saving"));
    }
}
