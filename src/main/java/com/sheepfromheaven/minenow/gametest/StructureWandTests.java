package com.sheepfromheaven.minenow.gametest;

import com.sheepfromheaven.minenow.block.StructureMarkerBlock;
import com.sheepfromheaven.minenow.dominion.DominionSavedData;
import com.sheepfromheaven.minenow.dominion.StructureType;
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
    private static final int WIDTH = 8;
    private static final int DEPTH = 10;
    private static final int HEIGHT = 6;

    /**
     * Mirrors {@code StructureWandItem}'s local coordinate frame: {@code dx} runs sideways
     * (centered on the door, which sits at {@code doorPos}) and {@code dz} runs forward, away from
     * the player, from the door (dz = 0) to the back wall (dz = DEPTH - 1).
     */
    private static BlockPos local(BlockPos doorPos, Direction right, Direction forward, int dx, int dz, int y) {
        var pos = doorPos.relative(right, dx - WIDTH / 2).relative(forward, dz);
        return new BlockPos(pos.getX(), y, pos.getZ());
    }

    /**
     * Reproduces the "structure wand ignores the player's facing direction" bug: the door should be
     * placed at the clicked position and rotated to match the player's facing, instead of always
     * ending up rotated the same fixed way no matter which direction they were looking.
     */
    public static void structureWandOrientsDoorToPlayerFacing(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);

        int originalRadius = com.sheepfromheaven.minenow.Config.defaultDominionRadius;
        com.sheepfromheaven.minenow.Config.defaultDominionRadius = 6;
        try {
            var level = helper.getLevel();
            var clickedRelPos = new BlockPos(4, 1, 5);
            var clickedPos = helper.absolutePos(clickedRelPos);
            var bannerPos = clickedPos;

            var player = helper.makeMockServerPlayerInLevel();
            DominionSavedData.get(level).createDominion(player.getUUID(), "Test Dominion", bannerPos);

            // Face west instead of the wand's default (south).
            player.setYRot(90.0f);
            var facing = player.getDirection();
            helper.assertTrue(facing == Direction.WEST, "Test setup sanity check: yaw 90 should resolve to west");

            var wand = ModItems.STRUCTURE_WAND.get().getDefaultInstance();
            var hit = new BlockHitResult(Vec3.atCenterOf(clickedPos), Direction.UP, clickedPos, false);
            wand.useOn(new UseOnContext(level, player, InteractionHand.MAIN_HAND, wand, hit));

            // The door's threshold is always the clicked block, regardless of facing.
            var doorState = level.getBlockState(clickedPos.above());

            helper.succeedWhen(() -> {
                helper.assertTrue(doorState.is(Blocks.OAK_DOOR),
                        "Door should be placed at the clicked position");
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
        com.sheepfromheaven.minenow.Config.defaultDominionRadius = 6;
        try {
            var level = helper.getLevel();
            // Leave room on every side for the building to recede away from the door in the (default,
            // south-facing) player's forward direction, and sideways for its width.
            var clickedPos = helper.absolutePos(new BlockPos(4, 1, 1));
            var player = helper.makeMockServerPlayerInLevel();
            var forward = player.getDirection();
            var right = forward.getClockWise();
            int floorY = clickedPos.getY();
            // Banner at the footprint's center - the farthest corner is only a Chebyshev distance of
            // 5 away, comfortably inside the (default) 64-block claim radius, shrunk to 6 below to
            // avoid bleeding into a neighboring test's dominion.
            var centerPos = local(clickedPos, right, forward, WIDTH / 2, DEPTH / 2, floorY);
            var dominion = DominionSavedData.get(level).createDominion(UUID.randomUUID(), "Test Dominion", centerPos);
            player.setUUID(dominion.getOwnerUuid());

            var wand = ModItems.STRUCTURE_WAND.get().getDefaultInstance();
            var hit = new BlockHitResult(Vec3.atCenterOf(clickedPos), Direction.UP, clickedPos, false);
            wand.useOn(new UseOnContext(level, player, InteractionHand.MAIN_HAND, wand, hit));

            int ceilingY = floorY + HEIGHT - 1;

            boolean floorMinCorner = level.getBlockState(local(clickedPos, right, forward, 0, 0, floorY)).is(Blocks.STONE_BRICKS);
            boolean floorMaxCorner = level.getBlockState(local(clickedPos, right, forward, WIDTH - 1, DEPTH - 1, floorY)).is(Blocks.STONE_BRICKS);
            boolean ceilingMinCorner = level.getBlockState(local(clickedPos, right, forward, 0, 0, ceilingY)).is(Blocks.STONE_BRICKS);
            boolean ceilingMaxCorner = level.getBlockState(local(clickedPos, right, forward, WIDTH - 1, DEPTH - 1, ceilingY)).is(Blocks.STONE_BRICKS);

            boolean beyondWidth = level.getBlockState(local(clickedPos, right, forward, WIDTH, 0, floorY)).is(Blocks.STONE_BRICKS);
            boolean beyondDepth = level.getBlockState(local(clickedPos, right, forward, 0, DEPTH, floorY)).is(Blocks.STONE_BRICKS);
            boolean beyondHeight = level.getBlockState(local(clickedPos, right, forward, 0, 0, floorY + HEIGHT)).is(Blocks.STONE_BRICKS);

            var markerPos = local(clickedPos, right, forward, WIDTH / 2, DEPTH / 2, floorY + 1);
            var structure = dominion.getStructureContaining(markerPos);

            helper.succeedWhen(() -> {
                helper.assertTrue(floorMinCorner, "Floor should be built at the near corner of the footprint");
                helper.assertTrue(floorMaxCorner, "Floor should extend to exactly width-1/depth-1 from the near corner");
                helper.assertTrue(ceilingMinCorner, "Ceiling should sit exactly height-1 above the floor");
                helper.assertTrue(ceilingMaxCorner, "Ceiling should extend to exactly width-1/depth-1 from the near corner");
                helper.assertFalse(beyondWidth, "The wand should not build one block past the 8-wide footprint");
                helper.assertFalse(beyondDepth, "The wand should not build one block past the 10-deep footprint");
                helper.assertFalse(beyondHeight, "The wand should not build one block past the 6-high footprint");
                helper.assertTrue(structure != null, "The wand should register a DominionStructure for the built room");
            });
        } finally {
            com.sheepfromheaven.minenow.Config.defaultDominionRadius = originalRadius;
        }
    }

    /**
     * Shift-right-click cycles the wand's mode (HOUSE -> FISHERY); a subsequent (non-shift)
     * right-click should then build a fishery marker instead of a plain house one, and the room
     * should already contain the fishery's required Barrel and Cauldron so the structure isn't
     * missing requirements the moment it's placed.
     */
    public static void shiftRightClickCyclesModeAndBuildsMatchingFurnishedStructure(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);

        int originalRadius = com.sheepfromheaven.minenow.Config.defaultDominionRadius;
        com.sheepfromheaven.minenow.Config.defaultDominionRadius = 6;
        try {
            var level = helper.getLevel();
            var clickedPos = helper.absolutePos(new BlockPos(4, 1, 1));
            var player = helper.makeMockServerPlayerInLevel();
            var forward = player.getDirection();
            var right = forward.getClockWise();
            int floorY = clickedPos.getY();
            var centerPos = local(clickedPos, right, forward, WIDTH / 2, DEPTH / 2, floorY);
            var dominion = DominionSavedData.get(level).createDominion(UUID.randomUUID(), "Test Dominion", centerPos);
            player.setUUID(dominion.getOwnerUuid());

            var wand = ModItems.STRUCTURE_WAND.get().getDefaultInstance();
            var hit = new BlockHitResult(Vec3.atCenterOf(clickedPos), Direction.UP, clickedPos, false);

            // HOUSE -> FISHERY is one shift-click away (StructureType enum order).
            player.setShiftKeyDown(true);
            wand.useOn(new UseOnContext(level, player, InteractionHand.MAIN_HAND, wand, hit));
            player.setShiftKeyDown(false);

            wand.useOn(new UseOnContext(level, player, InteractionHand.MAIN_HAND, wand, hit));

            var markerPos = local(clickedPos, right, forward, WIDTH / 2, DEPTH / 2, floorY + 1);
            var markerState = level.getBlockState(markerPos);
            var structure = dominion.getStructureContaining(markerPos);

            var hasBarrel = new boolean[]{false};
            var hasCauldron = new boolean[]{false};
            for (int dx = 1; dx < WIDTH - 1; dx++) {
                for (int dz = 1; dz < DEPTH - 1; dz++) {
                    var state = level.getBlockState(local(clickedPos, right, forward, dx, dz, floorY + 1));
                    if (state.is(Blocks.BARREL)) hasBarrel[0] = true;
                    if (state.is(Blocks.CAULDRON)) hasCauldron[0] = true;
                }
            }

            helper.succeedWhen(() -> {
                helper.assertTrue(markerState.getBlock() instanceof StructureMarkerBlock marker && marker.getType() == StructureType.FISHERY,
                        "Marker block should be the fishery variant after cycling the wand's mode");
                helper.assertTrue(structure != null && structure.getType() == StructureType.FISHERY,
                        "Registered structure should be a FISHERY, not the default HOUSE");
                helper.assertTrue(hasBarrel[0], "Built room should already contain a Barrel to satisfy the fishery's requirements");
                helper.assertTrue(hasCauldron[0], "Built room should already contain a Cauldron to satisfy the fishery's requirements");
            });
        } finally {
            com.sheepfromheaven.minenow.Config.defaultDominionRadius = originalRadius;
        }
    }
}
