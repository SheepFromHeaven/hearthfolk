package com.sheepfromheaven.minenow.villager;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

public final class NightDoorTests {
    public static final long NIGHT = 14000L;
    private static final BlockPos DOOR = new BlockPos(11, 1, 14);
    private static final BlockPos BED = new BlockPos(11, 1, 9);

    private NightDoorTests() {}

    private static void house(GameTestHelper helper, boolean open) {
        for (int x = 8; x <= 14; x++) {
            for (int z = 8; z <= 14; z++) {
                boolean wall = x == 8 || x == 14 || z == 8 || z == 14;
                for (int y = 1; y <= 3; y++) if (wall) helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                helper.setBlock(new BlockPos(x, 4, z), Blocks.STONE);
            }
        }
        for (int y = 1; y <= 2; y++) helper.setBlock(new BlockPos(11, y, 14), Blocks.AIR);
        BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH).setValue(DoorBlock.OPEN, open);
        helper.setBlock(DOOR, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        helper.setBlock(DOOR.above(), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    public static void aVillagerClosesTheDoorBehindItOnItsWayToBed(GameTestHelper helper) {
        closesDoor(helper, false);
    }

    public static void aVillagerClosesADoorThePlayerLeftOpenBehindIt(GameTestHelper helper) {
        closesDoor(helper, true);
    }

    private static void closesDoor(GameTestHelper helper, boolean openAtStart) {
        helper.setTime(NIGHT);
        GameTestHelpers.fence(helper);
        house(helper, openAtStart);
        Villager villager = GameTestHelpers.spawnWithHome(helper, new BlockPos(11, 1, 19), BED);
        BlockPos inside = helper.absolutePos(new BlockPos(11, 1, 12));
        boolean[] wasInside = {false};
        helper.succeedWhen(() -> {
            if (villager.blockPosition().getZ() <= inside.getZ()) wasInside[0] = true;
            helper.assertTrue(wasInside[0], "The villager should walk into the house, is at " + helper.relativePos(villager.blockPosition()));
            helper.assertFalse(helper.getBlockState(DOOR).getValue(DoorBlock.OPEN), "The door should be closed behind the villager");
        });
    }

    /** A 51-block hall with its only door in the far end wall: the bed is a few blocks from the villager through the wall, and a long walk round. */
    public static void aVillagerWalksRoundALongWallToItsBed(GameTestHelper helper) {
        helper.setTime(NIGHT);
        GameTestHelpers.fence(helper);
        for (int x = 10; x <= 60; x++) {
            for (int z = 8; z <= 16; z++) {
                boolean wall = x == 10 || x == 60 || z == 8 || z == 16;
                for (int y = 1; y <= 3; y++) helper.setBlock(new BlockPos(x, y, z), wall ? Blocks.STONE : Blocks.AIR);
                helper.setBlock(new BlockPos(x, 4, z), Blocks.STONE);
            }
        }
        for (int y = 1; y <= 2; y++) helper.setBlock(new BlockPos(60, y, 12), Blocks.AIR);
        BlockPos bed = new BlockPos(35, 1, 10);
        Villager villager = GameTestHelpers.spawnWithHome(helper, new BlockPos(35, 1, 6), bed);
        BlockPos absBed = helper.absolutePos(bed);
        helper.succeedWhen(() -> helper.assertTrue(villager.blockPosition().distManhattan(absBed) <= 3 && villager.blockPosition().getZ() > helper.absolutePos(new BlockPos(0, 0, 8)).getZ(),
                "The villager should walk round to its bed, is at " + helper.relativePos(villager.blockPosition())));
    }
}
