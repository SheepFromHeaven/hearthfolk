package com.sheepfromheaven.minenow.jobs.gametest;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;

import com.sheepfromheaven.minenow.dominion.DominionSavedData;
import com.sheepfromheaven.minenow.structure.DominionStructure;
import com.sheepfromheaven.minenow.structure.StructureType;
import com.sheepfromheaven.minenow.inhabitant.ModEntities;
import com.sheepfromheaven.minenow.jobs.WorkGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Set;
import java.util.UUID;

// Registered into the vanilla test_function registry by MineNow's RegisterEvent listener;
// the matching data/minenow/test_instance/*.json files supply the structure and tick budget.
public class LumberjackToolIssueTests {

    /**
     * Reproduces the "still reports 'need a wooden axe' despite holding a working axe" bug:
     * once {@link com.sheepfromheaven.minenow.jobs.lumberjack.LumberjackJob} has recorded a missing-axe
     * issue (no axe found anywhere), handing the inhabitant an axe directly should clear that issue
     * as soon as the job notices it - not leave the stale complaint around forever even though the
     * job proceeds to work normally from then on.
     */
    public static void handingLumberjackAnAxeClearsTheMissingAxeIssue(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);

        var markerRelPos = new BlockPos(2, 1, 2);
        var level = helper.getLevel();
        var markerPos = helper.absolutePos(markerRelPos);

        // No barrel, no axe anywhere - forces the job into MISSING_TOOL and records the issue.
        var dominion = DominionSavedData.get(level).createDominion(UUID.randomUUID(), "Test Dominion", markerPos);
        var structure = new DominionStructure("Lumberyard", StructureType.LUMBERYARD, markerPos, Set.of(markerPos));
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

        helper.assertTrue("Needs an Axe".equals(workGoal.getStatusDescription()),
                "Job should be stuck waiting for an axe before one is handed to the inhabitant");
        helper.assertTrue(workGoal.getMissingItemLabel() != null,
                "Missing-axe issue should be recorded while no axe is available");

        // The player hands the inhabitant an axe directly (equips it), same as it would be after the
        // player fetches/crafts one and gives it - not via the storage-container pickup path.
        inhabitant.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_AXE));

        // Drive the job past its MISSING_TOOL retry wait so it rechecks for an axe.
        for (int i = 0; i < 105; i++) {
            workGoal.tick();
        }

        helper.assertTrue(!"Needs an Axe".equals(workGoal.getStatusDescription()),
                "Job should have moved on from MISSING_TOOL now that an axe is held");
        helper.assertTrue(workGoal.getMissingItemLabel() == null,
                "Missing-axe issue should be cleared once the inhabitant actually has an axe, but got: "
                        + workGoal.getMissingItemLabel());
        helper.succeed();
    }
}
