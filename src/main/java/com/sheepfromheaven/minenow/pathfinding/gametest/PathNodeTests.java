package com.sheepfromheaven.minenow.pathfinding.gametest;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;

import com.sheepfromheaven.minenow.pathfinding.DominionPathNode;
import com.sheepfromheaven.minenow.dominion.DominionSavedData;
import com.sheepfromheaven.minenow.inhabitant.ModEntities;
import com.sheepfromheaven.minenow.jobs.JobActions;
import com.sheepfromheaven.minenow.pathfinding.PathfindingItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

// Registered into the vanilla test_function registry by MineNow's RegisterEvent listener;
// the matching data/minenow/test_instance/*.json files supply the structure and tick budget.
public class PathNodeTests {

    public static void pathNodeWandTogglesNodeAtClickedPosition(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);

        var level = helper.getLevel();
        var bannerPos = helper.absolutePos(new BlockPos(1, 1, 1));
        var dominion = DominionSavedData.get(level).createDominion(UUID.randomUUID(), "Test Dominion", bannerPos);

        var targetRelPos = new BlockPos(3, 1, 3);
        var targetPos = helper.absolutePos(targetRelPos);

        var player = helper.makeMockServerPlayerInLevel();
        var wand = PathfindingItems.PATH_NODE_WAND.get().getDefaultInstance();
        var hit = new BlockHitResult(Vec3.atCenterOf(targetPos), Direction.UP, targetPos, false);

        wand.useOn(new UseOnContext(level, player, net.minecraft.world.InteractionHand.MAIN_HAND, wand, hit));

        var affectedDominion = DominionSavedData.get(level).findDominionContaining(targetPos.above());
        helper.assertTrue(affectedDominion.getPathNodeAt(targetPos.above()) != null,
                "First click should place a path node above the clicked block");

        wand.useOn(new UseOnContext(level, player, net.minecraft.world.InteractionHand.MAIN_HAND, wand, hit));

        helper.assertTrue(affectedDominion.getPathNodeAt(targetPos.above()) == null,
                "Second click on the same spot should remove the path node");

        helper.succeed();
    }

    public static void inhabitantWandersTowardNearbyStreetNode(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);

        var level = helper.getLevel();
        var bannerPos = helper.absolutePos(new BlockPos(1, 1, 1));
        var dominion = DominionSavedData.get(level).createDominion(UUID.randomUUID(), "Test Dominion", bannerPos);

        var nodeRelPos = new BlockPos(7, 1, 4);
        var nodePos = helper.absolutePos(nodeRelPos);
        dominion.addPathNode(new DominionPathNode(nodePos, DominionPathNode.Kind.STREET));

        var inhabitant = helper.spawn(ModEntities.INHABITANT.get(), new BlockPos(1, 1, 4));
        inhabitant.setDominionId(dominion.getId());

        // Exercise the goal exactly as the real AI does (registered in InhabitantEntity's own
        // goalSelector), rather than driving a second, unregistered instance in parallel - that
        // would fight the real one for control of the shared navigation and flake.
        var startDistSq = inhabitant.distanceToSqr(nodePos.getX() + 0.5, nodePos.getY(), nodePos.getZ() + 0.5);

        helper.succeedWhen(() -> {
            var distSq = inhabitant.distanceToSqr(nodePos.getX() + 0.5, nodePos.getY(), nodePos.getZ() + 0.5);
            helper.assertTrue(distSq < startDistSq / 2,
                    "Inhabitant should have walked substantially closer to the street node");
        });
    }

    public static void inhabitantClimbsLadderToReachElevatedTarget(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);

        // Backing wall for the ladder to attach to, then a 3-block ladder climbing from floor
        // level (relative y=0, on top of makeFloor's slab) up to a platform reachable only by
        // climbing.
        for (int y = 0; y <= 2; y++) {
            helper.setBlock(new BlockPos(4, y, 5), Blocks.STONE);
            helper.setBlock(new BlockPos(4, y, 4),
                    Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
        }
        // Landing platform beside the top of the ladder.
        helper.setBlock(new BlockPos(3, 1, 4), Blocks.STONE);

        var inhabitant = helper.spawn(ModEntities.INHABITANT.get(), new BlockPos(1, 0, 4));
        var targetRelPos = new BlockPos(3, 2, 4);
        var targetPos = helper.absolutePos(targetRelPos);

        helper.runAfterDelay(5, () ->
                inhabitant.getNavigation().moveTo(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5, 1.0));

        helper.succeedWhen(() -> {
            var distSq = inhabitant.distanceToSqr(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5);
            helper.assertTrue(distSq < 1.5, "Inhabitant should have climbed the ladder to reach the platform");
        });
    }

    public static void inhabitantOpensFenceGateToReachTarget(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);

        // A fully enclosed fence yard (walls on all 4 sides, matching GoHomeGoalTests' fenced-yard
        // obstacle) with a closed gate as the only opening - a single row of fence with open ends
        // isn't a real obstacle, since the pathfinder just routes around it instead of ever reaching
        // the gate. makeFloor's stone slab (at y=-1) is the walking surface; the fence sits at y=1,
        // one block above wherever the entity actually stands.
        for (int x = 2; x <= 6; x++) {
            helper.setBlock(new BlockPos(x, 1, 2), x == 4
                    ? Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(net.minecraft.world.level.block.FenceGateBlock.FACING, Direction.NORTH)
                    : Blocks.OAK_FENCE.defaultBlockState());
            helper.setBlock(new BlockPos(x, 1, 6), Blocks.OAK_FENCE.defaultBlockState());
        }
        for (int z = 2; z <= 6; z++) {
            helper.setBlock(new BlockPos(2, 1, z), Blocks.OAK_FENCE.defaultBlockState());
            helper.setBlock(new BlockPos(6, 1, z), Blocks.OAK_FENCE.defaultBlockState());
        }

        // Spawned well clear of the gate (behind the yard, mirroring GoHomeGoalTests' equivalent
        // fixture) rather than immediately adjacent to it - too short a path here left too few nodes
        // for the gate-detection window to ever include the gate itself.
        var inhabitant = helper.spawn(ModEntities.INHABITANT.get(), new BlockPos(4, 1, 7));
        var targetRelPos = new BlockPos(4, 1, 4);
        var targetPos = helper.absolutePos(targetRelPos);

        // Use the same walk helper the farmer/every other job actually drives movement with (repath
        // cooldown + arrival check), rather than hand-rolled navigation calls in the test itself -
        // a raw one-shot or fixed-interval moveTo() fights with OpenFenceGateGoal's own path-based
        // detection window in ways the real, well-exercised helper doesn't.
        var nav = new JobActions.Nav();
        helper.succeedWhen(() -> {
            JobActions.walkTo(inhabitant, targetPos, nav);
            var distSq = inhabitant.distanceToSqr(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5);
            // Matches walkTo's own arrival threshold (distance <= 2) - it calls navigation.stop() once
            // it considers itself arrived, so asserting anything tighter than that can never be met.
            helper.assertTrue(distSq <= 4.0, "Inhabitant should have opened the fence gate to reach the other side");
        });
    }

    /**
     * Reproduces a reported real-world case: the yard's ground is one block higher than the
     * approach side (a step up right at the gate, common when a field/yard is built on raised
     * ground), so the gate sits at {@code raisedGroundY + 1} rather than the approach-side ground's
     * {@code + 1} - a plain "current ground + 1" check misses it if it ever samples the lower,
     * pre-step ground level at the moment of collision.
     */
    public static void inhabitantOpensFenceGateOnRaisedGroundToReachTarget(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);

        // Approach side (z=0..1) is flat at y=1; the yard (z=2..6, including the gate's own row) is
        // one block higher at y=2 - a single-block step the inhabitant can walk up onto.
        for (int x = 0; x <= 8; x++) {
            for (int z = 0; z <= 1; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
            for (int z = 2; z <= 8; z++) {
                helper.setBlock(new BlockPos(x, 2, z), Blocks.STONE);
            }
        }

        for (int x = 2; x <= 6; x++) {
            helper.setBlock(new BlockPos(x, 3, 2), x == 4
                    ? Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(net.minecraft.world.level.block.FenceGateBlock.FACING, Direction.NORTH)
                    : Blocks.OAK_FENCE.defaultBlockState());
            helper.setBlock(new BlockPos(x, 3, 6), Blocks.OAK_FENCE.defaultBlockState());
        }
        for (int z = 2; z <= 6; z++) {
            helper.setBlock(new BlockPos(2, 3, z), Blocks.OAK_FENCE.defaultBlockState());
            helper.setBlock(new BlockPos(6, 3, z), Blocks.OAK_FENCE.defaultBlockState());
        }

        var inhabitant = helper.spawn(ModEntities.INHABITANT.get(), new BlockPos(4, 2, 1));
        var targetRelPos = new BlockPos(4, 3, 4);
        var targetPos = helper.absolutePos(targetRelPos);

        var nav = new JobActions.Nav();
        helper.succeedWhen(() -> {
            JobActions.walkTo(inhabitant, targetPos, nav);
            var distSq = inhabitant.distanceToSqr(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5);
            helper.assertTrue(distSq <= 4.0, "Inhabitant should have opened the gate on raised ground to reach the other side");
        });
    }
}
