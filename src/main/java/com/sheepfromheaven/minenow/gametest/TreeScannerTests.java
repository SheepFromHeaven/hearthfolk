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
        GameTestHelpers.makeFloor(helper);

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
        GameTestHelpers.makeFloor(helper);

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
        GameTestHelpers.makeFloor(helper);

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
        GameTestHelpers.makeFloor(helper);

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
        GameTestHelpers.makeFloor(helper);

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
        GameTestHelpers.makeFloor(helper);

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
        GameTestHelpers.makeFloor(helper);

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

    /**
     * The trunk is now a flood fill of every block of the same log species, regardless of the
     * axis/orientation each one is placed in - a differently-oriented log of the same species
     * (e.g. a branch growing sideways) is part of the trunk, not a foreign structure block.
     */
    public static void differentAxisLogOfSameSpeciesIsIncludedInTrunk(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);

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

        helper.succeedWhen(() -> {
            helper.assertTrue(tree.isPresent(), "A same-species log of a different axis should not abort tree recognition");
            helper.assertTrue(tree.get().contains(helper.absolutePos(branchLogPos)),
                    "The differently-oriented log should be flood-filled into the trunk");
        });
    }

    public static void grassAndFlowersAdjacentToTrunkDoNotAbortTree(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);

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

    /**
     * Reproduces the "acacia tree not recognized" bug: acacia (and other diagonally-branching
     * trees) offset the trunk sideways by one block partway up instead of staying in the same
     * column, so a scanner that only ever checks straight up loses the trunk and never finds the
     * leaves confirming it as a tree.
     */
    public static void diagonallyOffsetTrunkIsStillRecognizedAsTree(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);

        var soilPos = new BlockPos(2, 0, 2);
        var baseLogPos = new BlockPos(2, 1, 2);
        var diagonalLogPos = new BlockPos(3, 2, 2);
        var leafPos = new BlockPos(4, 2, 2);

        helper.setBlock(soilPos, Blocks.GRASS_BLOCK);
        helper.setBlock(baseLogPos, Blocks.ACACIA_LOG);
        helper.setBlock(diagonalLogPos, Blocks.ACACIA_LOG);
        helper.setBlock(leafPos, Blocks.ACACIA_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false));

        var level = helper.getLevel();
        var tree = TreeScanner.findTree(level, helper.absolutePos(baseLogPos));

        helper.succeedWhen(() -> {
            helper.assertTrue(tree.isPresent(), "A trunk that jumps diagonally by one block should still be recognized as a tree");
            helper.assertTrue(tree.get().contains(helper.absolutePos(baseLogPos)), "The base log should be part of the returned trunk");
            helper.assertTrue(tree.get().contains(helper.absolutePos(diagonalLogPos)), "The diagonally-offset log should be part of the returned trunk");
        });
    }

    /**
     * Reproduces the "acacia canopy not fully recognized" bug: a tree's leaves can touch the trunk
     * at more than one point (e.g. a low branch near the base as well as the main canopy up top),
     * and the canopy itself is wider than just the blocks directly touching the trunk. The full
     * recognized tree should flood-fill outward from every touch point, not just record the single
     * touching leaf block per point.
     */
    public static void findTreeShapeFloodFillsTheWholeCanopyFromEveryTrunkTouchPoint(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);

        var soilPos = new BlockPos(2, 0, 2);
        var baseLogPos = new BlockPos(2, 1, 2);
        var diagonalLogPos = new BlockPos(3, 2, 2);

        // Main canopy up top: three connected leaves, only one of which directly touches the trunk.
        var canopyTouchPos = new BlockPos(4, 2, 2);
        var canopyFarPos1 = new BlockPos(5, 2, 2);
        var canopyFarPos2 = new BlockPos(4, 2, 3);

        // A separate low leaf touching the base log directly, simulating a low branch near the roots.
        var lowLeafPos = new BlockPos(1, 1, 2);

        helper.setBlock(soilPos, Blocks.GRASS_BLOCK);
        helper.setBlock(baseLogPos, Blocks.ACACIA_LOG);
        helper.setBlock(diagonalLogPos, Blocks.ACACIA_LOG);
        helper.setBlock(canopyTouchPos, Blocks.ACACIA_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false));
        helper.setBlock(canopyFarPos1, Blocks.ACACIA_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false));
        helper.setBlock(canopyFarPos2, Blocks.ACACIA_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false));
        helper.setBlock(lowLeafPos, Blocks.ACACIA_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false));

        var level = helper.getLevel();
        var shape = TreeScanner.findTreeShape(level, helper.absolutePos(baseLogPos));

        helper.succeedWhen(() -> {
            helper.assertTrue(shape.isPresent(), "A tree with leaves touching the trunk at multiple points should still be recognized");
            var leaves = shape.get().leaves();
            helper.assertTrue(leaves.contains(helper.absolutePos(canopyFarPos1)),
                    "Flood fill should include canopy leaves not directly touching the trunk");
            helper.assertTrue(leaves.contains(helper.absolutePos(canopyFarPos2)),
                    "Flood fill should include canopy leaves not directly touching the trunk");
            helper.assertTrue(leaves.contains(helper.absolutePos(lowLeafPos)),
                    "A separate low leaf touching the base log should also be part of the recognized tree");
        });
    }

    /**
     * Reproduces the "only the small branch gets recognized" bug: acacia trunks can fork into
     * *two* diagonal branches from the same point, not just one. A scanner that only ever follows
     * a single path loses whichever branch it doesn't happen to pick, along with that branch's own
     * logs and canopy.
     */
    public static void trunkForkingIntoTwoDiagonalBranchesRecognizesBothBranches(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);

        var soilPos = new BlockPos(2, 0, 2);
        var baseLogPos = new BlockPos(2, 1, 2);

        // Fork point: two diagonal branches in different directions, each with its own canopy leaf.
        var branchALogPos = new BlockPos(3, 2, 2);
        var branchALeafPos = new BlockPos(4, 2, 2);
        var branchBLogPos = new BlockPos(1, 2, 2);
        var branchBLeafPos = new BlockPos(0, 2, 2);

        helper.setBlock(soilPos, Blocks.GRASS_BLOCK);
        helper.setBlock(baseLogPos, Blocks.ACACIA_LOG);
        helper.setBlock(branchALogPos, Blocks.ACACIA_LOG);
        helper.setBlock(branchALeafPos, Blocks.ACACIA_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false));
        helper.setBlock(branchBLogPos, Blocks.ACACIA_LOG);
        helper.setBlock(branchBLeafPos, Blocks.ACACIA_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false));

        var level = helper.getLevel();
        var shape = TreeScanner.findTreeShape(level, helper.absolutePos(baseLogPos));

        helper.succeedWhen(() -> {
            helper.assertTrue(shape.isPresent(), "A trunk forking into two diagonal branches should still be recognized as a tree");
            var trunk = shape.get().trunk();
            helper.assertTrue(trunk.contains(helper.absolutePos(branchALogPos)), "Branch A's log should be part of the recognized trunk");
            helper.assertTrue(trunk.contains(helper.absolutePos(branchBLogPos)), "Branch B's log should be part of the recognized trunk");
            var leaves = shape.get().leaves();
            helper.assertTrue(leaves.contains(helper.absolutePos(branchALeafPos)), "Branch A's canopy leaf should be part of the recognized tree");
            helper.assertTrue(leaves.contains(helper.absolutePos(branchBLeafPos)), "Branch B's canopy leaf should be part of the recognized tree");
        });
    }

    /**
     * Reproduces the "vines block the search" bug: vines commonly grow on and hang off of natural
     * trunks, and shouldn't be treated as a foreign structure block that aborts recognition.
     */
    public static void vinesAdjacentToTrunkDoNotAbortTree(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);

        var soilPos = new BlockPos(2, 0, 2);
        var lowerLogPos = new BlockPos(2, 1, 2);
        var vinePos = new BlockPos(3, 1, 2);
        var upperLogPos = new BlockPos(2, 2, 2);
        var leafPos = new BlockPos(3, 2, 2);

        helper.setBlock(soilPos, Blocks.GRASS_BLOCK);
        helper.setBlock(lowerLogPos, Blocks.OAK_LOG);
        helper.setBlock(vinePos, Blocks.VINE.defaultBlockState().setValue(net.minecraft.world.level.block.VineBlock.WEST, true));
        helper.setBlock(upperLogPos, Blocks.OAK_LOG);
        helper.setBlock(leafPos, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false));

        var level = helper.getLevel();
        var tree = TreeScanner.findTree(level, helper.absolutePos(lowerLogPos));

        helper.succeedWhen(() ->
                helper.assertTrue(tree.isPresent(), "Vines touching the trunk should not abort tree recognition"));
    }

    public static void nonLogPillarTwoBlocksAwayDoesNotAbortTree(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);

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
