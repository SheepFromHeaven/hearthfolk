package com.sheepfromheaven.minenow.pathfinding.gametest;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;

import com.sheepfromheaven.minenow.pathfinding.DominionPathNode;
import com.sheepfromheaven.minenow.dominion.DominionSavedData;
import com.sheepfromheaven.minenow.inhabitant.ModEntities;
import com.sheepfromheaven.minenow.core.ModItems;
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
        var wand = ModItems.PATH_NODE_WAND.get().getDefaultInstance();
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
}
