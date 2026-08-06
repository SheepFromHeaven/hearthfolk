package com.sheepfromheaven.minenow.gametest;

import com.sheepfromheaven.minenow.dominion.DominionSavedData;
import com.sheepfromheaven.minenow.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

// Registered into the vanilla test_function registry by MineNow's RegisterEvent listener;
// the matching data/minenow/test_instance/*.json file supplies the (larger, room_12x8x14) structure
// and tick budget - the wand's building no longer fits inside the smaller rooms used by other tests.
public class StructureWandTests {

    /**
     * Reproduces the "structure wand ignores the player's facing direction" bug: the door should be
     * placed on the wall the player is facing (and rotated to match), instead of always ending up on
     * the same fixed south wall no matter which way the player was looking when they used the wand.
     */
    public static void structureWandOrientsDoorToPlayerFacing(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);

        int originalRadius = com.sheepfromheaven.minenow.Config.defaultDominionRadius;
        com.sheepfromheaven.minenow.Config.defaultDominionRadius = 6;
        try {
            var level = helper.getLevel();
            var floorCornerRelPos = new BlockPos(1, 1, 1);
            var floorCorner = helper.absolutePos(floorCornerRelPos);
            var bannerPos = floorCorner.offset(4, 1, 5);

            var player = helper.makeMockServerPlayerInLevel();
            DominionSavedData.get(level).createDominion(player.getUUID(), "Test Dominion", bannerPos);

            // Face west instead of the wand's fixed default (south).
            player.setYRot(90.0f);
            var facing = player.getDirection();
            helper.assertTrue(facing == Direction.WEST, "Test setup sanity check: yaw 90 should resolve to west");

            var wand = ModItems.STRUCTURE_WAND.get().getDefaultInstance();
            var hit = new BlockHitResult(Vec3.atCenterOf(floorCorner), Direction.UP, floorCorner, false);
            wand.useOn(new UseOnContext(level, player, InteractionHand.MAIN_HAND, wand, hit));

            // Facing west should put the door on the west wall (x = corner), at mid-depth.
            var expectedDoorPos = floorCorner.offset(0, 1, 10 / 2);
            var doorState = level.getBlockState(expectedDoorPos);

            helper.succeedWhen(() -> {
                helper.assertTrue(doorState.is(Blocks.OAK_DOOR),
                        "Door should be on the wall the player was facing (west), not the fixed south wall");
                helper.assertTrue(doorState.getValue(DoorBlock.FACING) == facing.getOpposite(),
                        "Door should be rotated to match the player's facing direction");
            });
        } finally {
            com.sheepfromheaven.minenow.Config.defaultDominionRadius = originalRadius;
        }
    }

    public static void structureWandBuildsBuildingWithFixed8x10x6Dimensions(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);

        // GameTest structures share one overworld DominionSavedData, so a neighboring test's dominion
        // banner can otherwise fall within this dominion's (default 64-block) claimed radius and get
        // matched instead of the one just created here - shrink the (global) radius for the duration
        // of this test to avoid that (same workaround used by DominionGrowthTests).
        int originalRadius = com.sheepfromheaven.minenow.Config.defaultDominionRadius;
        // Small enough to avoid bleeding into a neighboring test's dominion, but still big enough
        // (Chebyshev distance banner-to-farthest-building-corner is 5) to cover the whole building.
        com.sheepfromheaven.minenow.Config.defaultDominionRadius = 6;
        try {
            var level = helper.getLevel();
            var floorCornerRel = new BlockPos(1, 1, 1);
            var floorCorner = helper.absolutePos(floorCornerRel);
            // Put the banner at the building's center so the small claim radius above covers it.
            var bannerPos = floorCorner.offset(4, 1, 5);
            var dominion = DominionSavedData.get(level).createDominion(UUID.randomUUID(), "Test Dominion", bannerPos);

            var player = helper.makeMockServerPlayerInLevel();
            player.setUUID(dominion.getOwnerUuid());
            var wand = ModItems.STRUCTURE_WAND.get().getDefaultInstance();
            var hit = new BlockHitResult(Vec3.atCenterOf(floorCorner), Direction.UP, floorCorner, false);
            wand.useOn(new UseOnContext(level, player, InteractionHand.MAIN_HAND, wand, hit));

            // Fixed building footprint: 8 wide (x), 10 deep (z), 6 high (y), corner-anchored at the
            // clicked block. Floor/ceiling stone bricks should span exactly that box - one past the
            // edge in each direction should NOT have been touched by the wand.
            int width = 8;
            int depth = 10;
            int height = 6;

            boolean floorMinCorner = level.getBlockState(floorCorner).is(Blocks.STONE_BRICKS);
            boolean floorMaxCorner = level.getBlockState(floorCorner.offset(width - 1, 0, depth - 1)).is(Blocks.STONE_BRICKS);
            boolean ceilingMinCorner = level.getBlockState(floorCorner.offset(0, height - 1, 0)).is(Blocks.STONE_BRICKS);
            boolean ceilingMaxCorner = level.getBlockState(floorCorner.offset(width - 1, height - 1, depth - 1)).is(Blocks.STONE_BRICKS);

            boolean beyondWidth = level.getBlockState(floorCorner.offset(width, 0, 0)).is(Blocks.STONE_BRICKS);
            boolean beyondDepth = level.getBlockState(floorCorner.offset(0, 0, depth)).is(Blocks.STONE_BRICKS);
            boolean beyondHeight = level.getBlockState(floorCorner.offset(0, height, 0)).is(Blocks.STONE_BRICKS);

            var structure = dominion.getStructureContaining(floorCorner.offset(width / 2, height / 2, depth / 2));

            helper.succeedWhen(() -> {
                helper.assertTrue(floorMinCorner, "Floor should be built at the wand's clicked (minimum) corner");
                helper.assertTrue(floorMaxCorner, "Floor should extend to exactly width-1/depth-1 from the corner");
                helper.assertTrue(ceilingMinCorner, "Ceiling should sit exactly height-1 above the floor");
                helper.assertTrue(ceilingMaxCorner, "Ceiling should extend to exactly width-1/depth-1 from the corner");
                helper.assertFalse(beyondWidth, "The wand should not build one block past the 8-wide footprint");
                helper.assertFalse(beyondDepth, "The wand should not build one block past the 10-deep footprint");
                helper.assertFalse(beyondHeight, "The wand should not build one block past the 6-high footprint");
                helper.assertTrue(structure != null, "The wand should register a DominionStructure for the built room");
            });
        } finally {
            com.sheepfromheaven.minenow.Config.defaultDominionRadius = originalRadius;
        }
    }
}
