package com.sheepfromheaven.minenow.gametest;

import com.sheepfromheaven.minenow.dominion.DominionSavedData;
import com.sheepfromheaven.minenow.dominion.DominionStructure;
import com.sheepfromheaven.minenow.dominion.StructureType;
import com.sheepfromheaven.minenow.entity.InhabitantEntity;
import com.sheepfromheaven.minenow.entity.ModEntities;
import com.sheepfromheaven.minenow.entity.job.LumberjackJob;
import com.sheepfromheaven.minenow.entity.job.WorkGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;

import java.util.Set;
import java.util.UUID;

// Registered into the vanilla test_function registry by MineNow's RegisterEvent listener;
// the matching data/minenow/test_instance/*.json files supply the structure and tick budget.
public class JobInterruptionTests {

    /**
     * Reproduces the "wakes up and immediately starts working far from the job site" bug: an
     * inhabitant interrupted mid-chop (e.g. by being sent home to sleep) should re-approach the
     * lumberyard before resuming, rather than resuming the chopping phase wherever the interruption
     * left it. Drives {@link WorkGoal} directly and teleports past each travel leg instead of
     * waiting on real pathfinding, so the test stays deterministic and fast.
     */
    public static void interruptedLumberjackReturnsToHutInsteadOfResumingChopping(GameTestHelper helper) {
        var setup = setUpChoppingLumberjack(helper);
        var workGoal = setup.workGoal;
        var markerPos = setup.markerPos;

        helper.assertTrue("Chopping wood".equals(workGoal.getStatusDescription()),
                "Job should have reached the chopping phase before being interrupted");

        // Something takes control away mid-chop (e.g. GoHomeGoal pulling the inhabitant home for the
        // night) and walks them far from the tree.
        workGoal.stop();
        setup.inhabitant.snapTo(markerPos.getX() + 20, markerPos.getY(), markerPos.getZ() + 20);

        helper.succeedWhen(() -> helper.assertTrue("Heading to work".equals(workGoal.getStatusDescription()),
                "Interrupted job should reset to heading-to-work instead of resuming the chopping phase in place"));
    }

    /**
     * Reproduces the "unassigning a job leaves the old goal/status hanging around" bug: unassigning
     * an inhabitant mid-chop (workStructureId cleared to null, as Dominion.unassignWorker does) should
     * drop the stale job entirely rather than leaving its last phase and status text pointing at a
     * job site the inhabitant is no longer assigned to.
     */
    public static void unassigningLumberjackMidChopDropsStaleJob(GameTestHelper helper) {
        var setup = setUpChoppingLumberjack(helper);
        var workGoal = setup.workGoal;

        helper.assertTrue("Chopping wood".equals(workGoal.getStatusDescription()),
                "Job should have reached the chopping phase before being unassigned");

        // Mirrors Dominion.unassignWorker: only the data field is cleared, nothing calls into the goal.
        setup.inhabitant.setWorkStructureId(null);
        workGoal.stop();

        helper.succeedWhen(() -> {
            helper.assertTrue("Idle".equals(workGoal.getStatusDescription()),
                    "Unassigning mid-job should drop the stale job instead of leaving old status text behind");
            helper.assertTrue(workGoal.getJobType() == null,
                    "Unassigning mid-job should leave no job type behind once the goal has stopped");
        });
    }

    /**
     * Reproduces the "structure requirements are ignored" bug: a lumberyard whose only requirement
     * (a barrel) is missing should not let its assigned worker (re-)start the job, even once the job
     * is mid-cycle and would otherwise always report itself startable (phase != GOTO_HUT skips the
     * day/night gate in {@link LumberjackJob#canStart}). The inhabitant is given an axe directly so
     * phase progression doesn't depend on a barrel existing at all - only the requirement check does.
     */
    public static void jobDoesNotStartWhenStructureRequirementsAreUnmet(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);

        var markerRelPos = new BlockPos(2, 1, 2);
        var soilRelPos = new BlockPos(6, 0, 6);
        var logRelPos = new BlockPos(6, 1, 6);
        var leafRelPos = new BlockPos(7, 1, 6);
        // Deliberately no barrel placed - the lumberyard's only requirement is left unmet.

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                helper.setBlock(soilRelPos.offset(dx, 0, dz), Blocks.GRASS_BLOCK);
            }
        }
        helper.setBlock(logRelPos, Blocks.OAK_LOG);
        helper.setBlock(leafRelPos, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false));

        var level = helper.getLevel();
        var markerPos = helper.absolutePos(markerRelPos);
        var logPos = helper.absolutePos(logRelPos);

        var dominion = DominionSavedData.get(level).createDominion(UUID.randomUUID(), "Test Dominion", markerPos);
        var structure = new DominionStructure("Lumberyard", StructureType.LUMBERYARD, markerPos, Set.of(markerPos));
        dominion.addStructure(structure);

        var inhabitant = helper.spawn(ModEntities.INHABITANT.get(), markerPos);
        inhabitant.setDominionId(dominion.getId());
        inhabitant.setWorkStructureId(structure.getId());
        inhabitant.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));

        var workGoal = new WorkGoal(inhabitant);
        workGoal.canUse();
        workGoal.start();

        // Drive past GOTO_HUT (skipped straight to GOTO_TREE since the axe is already held) into
        // CHOPPING, at which point LumberjackJob.canStart always returns true regardless of day/night -
        // isolating the assertion below to the structure-requirements gate this test is about.
        inhabitant.snapTo(markerPos.getX() + 0.5, markerPos.getY(), markerPos.getZ() + 0.5);
        workGoal.tick();
        workGoal.tick();
        inhabitant.snapTo(logPos.getX() + 0.5, logPos.getY(), logPos.getZ() + 0.5);
        workGoal.tick();

        helper.assertTrue("Chopping wood".equals(workGoal.getStatusDescription()),
                "Job should have reached the chopping phase before the requirements check is exercised");
        helper.assertTrue(!workGoal.canUse(),
                "Job should refuse to (re-)start while the structure's requirements (a barrel) are unmet");
        helper.succeed();
    }

    private record ChoppingLumberjackSetup(InhabitantEntity inhabitant, WorkGoal workGoal, BlockPos markerPos) {}

    /** Spawns a lumberjack, equips an axe, and drives the job to the chopping phase via teleport-driven ticks. */
    private static ChoppingLumberjackSetup setUpChoppingLumberjack(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);

        var markerRelPos = new BlockPos(2, 1, 2);
        var barrelRelPos = new BlockPos(1, 1, 1);
        var soilRelPos = new BlockPos(6, 0, 6);
        var logRelPos = new BlockPos(6, 1, 6);
        var leafRelPos = new BlockPos(7, 1, 6);

        helper.setBlock(barrelRelPos, Blocks.BARREL);
        // A patch of solid ground around the trunk's base, not just directly under it - otherwise
        // every tile adjacent to the trunk is a one-block-deep pit with nothing to stand on, and there
        // is no reachable neighbor to chop from (see LumberjackJob.findStandableNeighbor).
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                helper.setBlock(soilRelPos.offset(dx, 0, dz), Blocks.GRASS_BLOCK);
            }
        }
        helper.setBlock(logRelPos, Blocks.OAK_LOG);
        helper.setBlock(leafRelPos, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false));

        var level = helper.getLevel();
        var markerPos = helper.absolutePos(markerRelPos);
        var barrelPos = helper.absolutePos(barrelRelPos);
        var logPos = helper.absolutePos(logRelPos);

        if (level.getBlockEntity(barrelPos) instanceof Container container) {
            container.setItem(0, new ItemStack(Items.IRON_AXE));
        }

        var dominion = DominionSavedData.get(level).createDominion(UUID.randomUUID(), "Test Dominion", markerPos);
        var structure = new DominionStructure("Lumberyard", StructureType.LUMBERYARD, markerPos, Set.of(markerPos, barrelPos));
        dominion.addStructure(structure);

        var inhabitant = helper.spawn(ModEntities.INHABITANT.get(), markerPos);
        inhabitant.setDominionId(dominion.getId());
        inhabitant.setWorkStructureId(structure.getId());

        var workGoal = new WorkGoal(inhabitant);
        // canUse() creates the job as a side effect regardless of its return value (which depends on
        // the shared world's current day/night clock - other concurrently-running gametests depend on
        // that clock too, so this test must not mutate it via setDayTime).
        workGoal.canUse();
        workGoal.start();

        // Drive the job through GOTO_HUT -> FIND_AXE -> GOTO_TREE -> CHOPPING, teleporting past
        // each travel leg's arrival check instead of waiting on real navigation ticks.
        inhabitant.snapTo(markerPos.getX() + 0.5, markerPos.getY(), markerPos.getZ() + 0.5);
        workGoal.tick();
        workGoal.tick();
        inhabitant.snapTo(logPos.getX() + 0.5, logPos.getY(), logPos.getZ() + 0.5);
        workGoal.tick();

        return new ChoppingLumberjackSetup(inhabitant, workGoal, markerPos);
    }
}
