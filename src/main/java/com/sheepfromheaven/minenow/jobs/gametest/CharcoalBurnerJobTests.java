package com.sheepfromheaven.minenow.jobs.gametest;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;

import com.sheepfromheaven.minenow.dominion.DominionSavedData;
import com.sheepfromheaven.minenow.structure.DominionStructure;
import com.sheepfromheaven.minenow.structure.StructureType;
import com.sheepfromheaven.minenow.inhabitant.InhabitantEntity;
import com.sheepfromheaven.minenow.inhabitant.ModEntities;
import com.sheepfromheaven.minenow.jobs.WorkGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.Set;
import java.util.UUID;

// Registered into the vanilla test_function registry by MineNow's RegisterEvent listener;
// the matching data/minenow/test_instance/*.json files supply the structure and tick budget.
public class CharcoalBurnerJobTests {

    /**
     * The charcoal burner should load logs from the kiln's barrel into the furnace's input and
     * fuel slots (logs burn as their own fuel), so vanilla furnace mechanics smelt them into charcoal.
     */
    public static void charcoalBurnerLoadsLogsIntoTheFurnace(GameTestHelper helper) {
        var setup = setUpCharcoalBurnerWithLogs(helper);

        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getLevel().getBlockEntity(setup.furnacePos) instanceof Container furnace
                            && !furnace.getItem(0).isEmpty(),
                    "Logs should be loaded into the furnace's input slot");
            helper.assertTrue(helper.getLevel().getBlockEntity(setup.furnacePos) instanceof Container furnace
                            && !furnace.getItem(1).isEmpty(),
                    "Logs should also be loaded into the furnace's fuel slot");
            helper.assertTrue("Burning charcoal".equals(setup.workGoal.getStatusDescription()),
                    "Job should have moved to the burning phase once logs are loaded into the furnace");
        });
    }

    /**
     * With no logs anywhere in storage, the charcoal burner should report the missing "logs"
     * request on the dominion's request board instead of getting stuck silently.
     */
    public static void missingLogsReportsARequest(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);

        var markerRelPos = new BlockPos(2, 1, 2);
        var barrelRelPos = new BlockPos(1, 1, 1);
        var furnaceRelPos = new BlockPos(3, 1, 1);
        helper.setBlock(barrelRelPos, Blocks.BARREL);
        helper.setBlock(furnaceRelPos, Blocks.FURNACE);

        var level = helper.getLevel();
        var markerPos = helper.absolutePos(markerRelPos);
        var barrelPos = helper.absolutePos(barrelRelPos);
        var furnacePos = helper.absolutePos(furnaceRelPos);

        var dominion = DominionSavedData.get(level).createDominion(UUID.randomUUID(), "Test Dominion", markerPos);
        var structure = new DominionStructure("Charcoal Kiln", StructureType.CHARCOAL_KILN, markerPos, Set.of(markerPos, barrelPos, furnacePos));
        dominion.addStructure(structure);

        var inhabitant = helper.spawn(ModEntities.INHABITANT.get(), markerPos);
        inhabitant.setDominionId(dominion.getId());
        inhabitant.setWorkStructureId(structure.getId());

        var workGoal = new WorkGoal(inhabitant);
        workGoal.canUse();
        workGoal.start();

        inhabitant.snapTo(markerPos.getX() + 0.5, markerPos.getY(), markerPos.getZ() + 0.5);
        workGoal.tick();
        workGoal.tick();

        helper.succeedWhen(() ->
                helper.assertTrue(structure.getRequests().containsKey("logs"),
                        "Missing logs should be requested on the dominion's request board"));
    }

    private record CharcoalBurnerSetup(InhabitantEntity inhabitant, WorkGoal workGoal, BlockPos furnacePos) {}

    private static CharcoalBurnerSetup setUpCharcoalBurnerWithLogs(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);

        var markerRelPos = new BlockPos(2, 1, 2);
        var barrelRelPos = new BlockPos(1, 1, 1);
        var furnaceRelPos = new BlockPos(3, 1, 1);
        helper.setBlock(barrelRelPos, Blocks.BARREL);
        helper.setBlock(furnaceRelPos, Blocks.FURNACE);

        var level = helper.getLevel();
        var markerPos = helper.absolutePos(markerRelPos);
        var barrelPos = helper.absolutePos(barrelRelPos);
        var furnacePos = helper.absolutePos(furnaceRelPos);

        if (level.getBlockEntity(barrelPos) instanceof Container container) {
            container.setItem(0, new ItemStack(Items.OAK_LOG, 8));
        }

        var dominion = DominionSavedData.get(level).createDominion(UUID.randomUUID(), "Test Dominion", markerPos);
        var structure = new DominionStructure("Charcoal Kiln", StructureType.CHARCOAL_KILN, markerPos, Set.of(markerPos, barrelPos, furnacePos));
        dominion.addStructure(structure);

        var inhabitant = helper.spawn(ModEntities.INHABITANT.get(), markerPos);
        inhabitant.setDominionId(dominion.getId());
        inhabitant.setWorkStructureId(structure.getId());

        var workGoal = new WorkGoal(inhabitant);
        workGoal.canUse();
        workGoal.start();

        // Drive the job through GOTO_HUT -> FIND_LOGS -> BURNING. Each log is a pick-up-from-barrel leg
        // and a carry-to-furnace leg, each paced ~1/second, so this needs well more than a couple of ticks.
        inhabitant.snapTo(markerPos.getX() + 0.5, markerPos.getY(), markerPos.getZ() + 0.5);
        for (int i = 0; i < 150 && !"Burning charcoal".equals(workGoal.getStatusDescription()); i++) {
            workGoal.tick();
        }

        return new CharcoalBurnerSetup(inhabitant, workGoal, furnacePos);
    }
}
