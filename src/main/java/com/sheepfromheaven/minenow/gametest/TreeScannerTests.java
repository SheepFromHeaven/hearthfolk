package com.sheepfromheaven.minenow.gametest;

import com.sheepfromheaven.minenow.entity.job.TreeScanner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

// Registered into the vanilla test_function registry by MineNow's RegisterEvent listener;
// the matching data/minenow/test_instance/*.json files supply the structure and tick budget.
public class TreeScannerTests {

    public static void logOnSoilWithNaturalLeafIsRecognizedAsTree(GameTestHelper helper) {
        helper.makeFloor();

        var soilPos = new BlockPos(2, 0, 2);
        var logPos = new BlockPos(2, 1, 2);
        var leafPos = new BlockPos(3, 1, 2);

        helper.setBlock(soilPos, Blocks.GRASS_BLOCK);
        helper.setBlock(logPos, Blocks.OAK_LOG);
        helper.setBlock(leafPos, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false));

        var level = helper.getLevel();
        var tree = TreeScanner.findTree(level, helper.absolutePos(logPos));

        helper.succeedWhen(() -> {
            helper.assertTrue(tree.isPresent(), "A log on soil with a natural leaf sibling should be recognized as a tree");
            helper.assertTrue(tree.get().contains(helper.absolutePos(logPos)), "The trunk log should be part of the returned tree");
        });
    }

    public static void logPillarWithoutLeavesIsNotATree(GameTestHelper helper) {
        helper.makeFloor();

        var soilPos = new BlockPos(2, 0, 2);
        var logPos = new BlockPos(2, 1, 2);

        helper.setBlock(soilPos, Blocks.GRASS_BLOCK);
        helper.setBlock(logPos, Blocks.OAK_LOG);

        var level = helper.getLevel();
        var tree = TreeScanner.findTree(level, helper.absolutePos(logPos));

        helper.succeedWhen(() ->
                helper.assertTrue(tree.isEmpty(), "A bare log pillar with no leaves should not be recognized as a tree"));
    }

    public static void logWithOnlyPersistentLeafIsNotATree(GameTestHelper helper) {
        helper.makeFloor();

        var soilPos = new BlockPos(2, 0, 2);
        var logPos = new BlockPos(2, 1, 2);
        var leafPos = new BlockPos(3, 1, 2);

        helper.setBlock(soilPos, Blocks.GRASS_BLOCK);
        helper.setBlock(logPos, Blocks.OAK_LOG);
        helper.setBlock(leafPos, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));

        var level = helper.getLevel();
        var tree = TreeScanner.findTree(level, helper.absolutePos(logPos));

        helper.succeedWhen(() ->
                helper.assertTrue(tree.isEmpty(), "A player-placed persistent leaf should not confirm a tree, since decorative bushes use persistent leaves"));
    }

    public static void logNotOnSoilIsNotATree(GameTestHelper helper) {
        helper.makeFloor();

        var stonePos = new BlockPos(2, 0, 2);
        var logPos = new BlockPos(2, 1, 2);
        var leafPos = new BlockPos(3, 1, 2);

        helper.setBlock(stonePos, Blocks.STONE);
        helper.setBlock(logPos, Blocks.OAK_LOG);
        helper.setBlock(leafPos, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false));

        var level = helper.getLevel();
        var tree = TreeScanner.findTree(level, helper.absolutePos(logPos));

        helper.succeedWhen(() ->
                helper.assertTrue(tree.isEmpty(), "A log not resting on soil should never be scanned as a tree"));
    }

    public static void logAdjacentToPlanksAbortsEvenWithLeavesHigherUp(GameTestHelper helper) {
        helper.makeFloor();

        var soilPos = new BlockPos(2, 0, 2);
        var lowerLogPos = new BlockPos(2, 1, 2);
        var plankPos = new BlockPos(3, 1, 2);
        var upperLogPos = new BlockPos(2, 2, 2);
        var leafPos = new BlockPos(3, 2, 2);

        helper.setBlock(soilPos, Blocks.GRASS_BLOCK);
        helper.setBlock(lowerLogPos, Blocks.OAK_LOG);
        helper.setBlock(plankPos, Blocks.OAK_PLANKS);
        helper.setBlock(upperLogPos, Blocks.OAK_LOG);
        helper.setBlock(leafPos, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false));

        var level = helper.getLevel();
        var tree = TreeScanner.findTree(level, helper.absolutePos(lowerLogPos));

        helper.succeedWhen(() ->
                helper.assertTrue(tree.isEmpty(),
                        "A trunk touching a plank block should be rejected even though leaves exist further up, to protect log structures built next to trees"));
    }

    public static void beehiveNeighborAllowsClimbingToLeavesAbove(GameTestHelper helper) {
        helper.makeFloor();

        var soilPos = new BlockPos(2, 0, 2);
        var lowerLogPos = new BlockPos(2, 1, 2);
        var beehivePos = new BlockPos(3, 1, 2);
        var upperLogPos = new BlockPos(2, 2, 2);
        var leafPos = new BlockPos(3, 2, 2);

        helper.setBlock(soilPos, Blocks.GRASS_BLOCK);
        helper.setBlock(lowerLogPos, Blocks.OAK_LOG);
        helper.setBlock(beehivePos, Blocks.BEEHIVE);
        helper.setBlock(upperLogPos, Blocks.OAK_LOG);
        helper.setBlock(leafPos, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false));

        var level = helper.getLevel();
        var tree = TreeScanner.findTree(level, helper.absolutePos(lowerLogPos));

        helper.succeedWhen(() ->
                helper.assertTrue(tree.isPresent(), "A beehive next to the trunk should not block climbing to the leaves above"));
    }

    public static void sameAxisLogNeighborAllowsClimbingToLeavesAbove(GameTestHelper helper) {
        helper.makeFloor();

        var soilPos = new BlockPos(2, 0, 2);
        var lowerLogPos = new BlockPos(2, 1, 2);
        var neighborTrunkLogPos = new BlockPos(3, 1, 2);
        var upperLogPos = new BlockPos(2, 2, 2);
        var leafPos = new BlockPos(3, 2, 2);

        helper.setBlock(soilPos, Blocks.GRASS_BLOCK);
        helper.setBlock(lowerLogPos, Blocks.OAK_LOG);
        helper.setBlock(neighborTrunkLogPos, Blocks.OAK_LOG);
        helper.setBlock(upperLogPos, Blocks.OAK_LOG);
        helper.setBlock(leafPos, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false));

        var level = helper.getLevel();
        var tree = TreeScanner.findTree(level, helper.absolutePos(lowerLogPos));

        helper.succeedWhen(() ->
                helper.assertTrue(tree.isPresent(),
                        "A second vertical log of the same axis (multi-trunk tree) should not block climbing to the leaves above"));
    }

    public static void differentAxisLogNeighborAbortsTree(GameTestHelper helper) {
        helper.makeFloor();

        var soilPos = new BlockPos(2, 0, 2);
        var lowerLogPos = new BlockPos(2, 1, 2);
        var branchLogPos = new BlockPos(3, 1, 2);
        var upperLogPos = new BlockPos(2, 2, 2);
        var leafPos = new BlockPos(3, 2, 2);

        helper.setBlock(soilPos, Blocks.GRASS_BLOCK);
        helper.setBlock(lowerLogPos, Blocks.OAK_LOG);
        helper.setBlock(branchLogPos, Blocks.OAK_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
        helper.setBlock(upperLogPos, Blocks.OAK_LOG);
        helper.setBlock(leafPos, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false));

        var level = helper.getLevel();
        var tree = TreeScanner.findTree(level, helper.absolutePos(lowerLogPos));

        helper.succeedWhen(() ->
                helper.assertTrue(tree.isEmpty(),
                        "A horizontally-oriented log of a different axis is treated as a foreign structure block, not a natural branch"));
    }

    public static void grassAndFlowersAdjacentToTrunkDoNotAbortTree(GameTestHelper helper) {
        helper.makeFloor();

        var soilPos = new BlockPos(2, 0, 2);
        var lowerLogPos = new BlockPos(2, 1, 2);
        var grassSoilPos = new BlockPos(3, 0, 2);
        var grassPos = new BlockPos(3, 1, 2);
        var upperLogPos = new BlockPos(2, 2, 2);
        var flowerSoilPos = new BlockPos(1, 0, 2);
        var flowerPos = new BlockPos(1, 1, 2);
        var leafPos = new BlockPos(3, 2, 2);

        helper.setBlock(soilPos, Blocks.GRASS_BLOCK);
        helper.setBlock(lowerLogPos, Blocks.OAK_LOG);
        helper.setBlock(grassSoilPos, Blocks.GRASS_BLOCK);
        helper.setBlock(grassPos, Blocks.SHORT_GRASS);
        helper.setBlock(flowerSoilPos, Blocks.GRASS_BLOCK);
        helper.setBlock(flowerPos, Blocks.DANDELION);
        helper.setBlock(upperLogPos, Blocks.OAK_LOG);
        helper.setBlock(leafPos, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false));

        var level = helper.getLevel();
        var tree = TreeScanner.findTree(level, helper.absolutePos(lowerLogPos));

        helper.succeedWhen(() ->
                helper.assertTrue(tree.isPresent(), "Grass and flowers touching the trunk should not abort tree recognition"));
    }

    public static void nonLogPillarTwoBlocksAwayDoesNotAbortTree(GameTestHelper helper) {
        helper.makeFloor();

        var soilPos = new BlockPos(2, 0, 2);
        var logPos = new BlockPos(2, 1, 2);
        var leafPos = new BlockPos(1, 1, 2);
        var cornerPillarPos = new BlockPos(4, 1, 2);

        helper.setBlock(soilPos, Blocks.GRASS_BLOCK);
        helper.setBlock(logPos, Blocks.OAK_LOG);
        helper.setBlock(leafPos, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false));
        helper.setBlock(cornerPillarPos, Blocks.OAK_LOG);

        var level = helper.getLevel();
        var tree = TreeScanner.findTree(level, helper.absolutePos(logPos));

        helper.succeedWhen(() ->
                helper.assertTrue(tree.isPresent(), "A log pillar two blocks away from the trunk (not adjacent) should not affect tree recognition"));
    }
}
