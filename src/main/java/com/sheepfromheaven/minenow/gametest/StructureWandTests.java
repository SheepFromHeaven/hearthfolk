package com.sheepfromheaven.minenow.gametest;

import com.sheepfromheaven.minenow.dominion.DominionSavedData;
import com.sheepfromheaven.minenow.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

// Registered into the vanilla test_function registry by MineNow's RegisterEvent listener;
// the matching data/minenow/test_instance/*.json files supply the structure and tick budget.
public class StructureWandTests {

    /**
     * Reproduces the "structure wand ignores the player's facing direction" bug: the door should be
     * placed on the wall the player is facing (and rotated to match), instead of always ending up on
     * the same fixed south wall no matter which way the player was looking when they used the wand.
     */
    public static void structureWandOrientsDoorToPlayerFacing(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);

        var level = helper.getLevel();
        var floorCenterRelPos = new BlockPos(5, 1, 5);
        var floorCenter = helper.absolutePos(floorCenterRelPos);
        var bannerPos = helper.absolutePos(new BlockPos(1, 1, 1));

        var player = helper.makeMockServerPlayerInLevel();
        DominionSavedData.get(level).createDominion(player.getUUID(), "Test Dominion", bannerPos);

        // Face west instead of the wand's old fixed default (south).
        player.setYRot(90.0f);
        var facing = player.getDirection();
        helper.assertTrue(facing == Direction.WEST, "Test setup sanity check: yaw 90 should resolve to west");

        var wand = ModItems.STRUCTURE_WAND.get().getDefaultInstance();
        var hit = new BlockHitResult(Vec3.atCenterOf(floorCenter), Direction.UP, floorCenter, false);
        wand.useOn(new UseOnContext(level, player, InteractionHand.MAIN_HAND, wand, hit));

        var expectedDoorPos = floorCenter.relative(facing, 2).above();
        var doorState = level.getBlockState(expectedDoorPos);

        helper.succeedWhen(() -> {
            helper.assertTrue(doorState.is(net.minecraft.world.level.block.Blocks.OAK_DOOR),
                    "Door should be on the wall the player was facing (west), not the fixed south wall");
            helper.assertTrue(doorState.getValue(DoorBlock.FACING) == facing.getOpposite(),
                    "Door should be rotated to match the player's facing direction");
        });
    }
}
