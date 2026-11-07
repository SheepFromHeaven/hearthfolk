package com.sheepfromheaven.minenow.villager;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;

public final class RoadTests {
    private RoadTests() {}

    public static void villagerTakesTheRoadInsteadOfCuttingAcrossGrass(GameTestHelper helper) {
        Villager villager = setUpUShapedRoad(helper, 6);
        helper.runAfterDelay(5, () -> checkRoadTaken(helper, pathToGoal(helper, villager)));
    }

    private static void checkRoadTaken(GameTestHelper helper, Path path) {
        boolean cutAcross = false;
        boolean usedRoad = false;
        for (int i = 0; i < path.getNodeCount(); i++) {
            BlockPos node = helper.relativePos(path.getNode(i).asBlockPos());
            if (node.getZ() == 4 && node.getX() >= 6 && node.getX() <= 16) cutAcross = true;
            if (node.getZ() == 6) usedRoad = true;
        }
        helper.assertTrue(path.canReach(), "The villager should find a way to its goal");
        helper.assertTrue(usedRoad && !cutAcross, "A road two blocks off the straight line should be taken: " + describe(helper, path));
        helper.succeed();
    }

    public static void villagerStillCutsAcrossWhenTheRoadIsALongDetour(GameTestHelper helper) {
        Villager villager = setUpUShapedRoad(helper, 14);
        helper.runAfterDelay(5, () -> checkCutAcross(helper, pathToGoal(helper, villager)));
    }

    private static void checkCutAcross(GameTestHelper helper, Path path) {
        boolean cutAcross = false;
        for (int i = 0; i < path.getNodeCount(); i++) {
            BlockPos node = helper.relativePos(path.getNode(i).asBlockPos());
            if (node.getZ() <= 6 && node.getX() >= 6 && node.getX() <= 16) cutAcross = true;
        }
        helper.assertTrue(path.canReach() && cutAcross, "A road ten blocks out is not worth the detour: " + describe(helper, path));
        helper.succeed();
    }

    public static void aPavedStreetBeatsAStraightDirtPath(GameTestHelper helper) {
        Villager villager = setUpUShapedRoad(helper, 6);
        for (int x = 4; x <= 18; x++) helper.setBlock(x, 0, 4, Blocks.DIRT_PATH);
        helper.runAfterDelay(5, () -> checkRoadTaken(helper, pathToGoal(helper, villager)));
    }

    public static void idleVillagersStrollAlongRoads(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        for (int x = 1; x <= 22; x++) {
            for (int z = 1; z <= 22; z++) helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
        }
        for (int i = 5; i <= 18; i++) {
            helper.setBlock(i, 0, 5, Blocks.STONE_BRICKS);
            helper.setBlock(i, 0, 18, Blocks.STONE_BRICKS);
            helper.setBlock(5, 0, i, Blocks.STONE_BRICKS);
            helper.setBlock(18, 0, i, Blocks.STONE_BRICKS);
        }
        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(11, 1, 11));
        int[] ticks = {0, 0};
        helper.onEachTick(() -> {
            if (++ticks[0] < 100) return;
            if (Roads.isRoad(helper.getLevel(), villager.blockPosition())) ticks[1]++;
        });

        helper.runAfterDelay(2300, () -> {
            float share = ticks[1] / (float) (ticks[0] - 99);
            helper.assertTrue(share > 0.3F, "An idle villager should spend much of its time on roads (the ring is ~12% of the area), was " + Math.round(share * 100) + "%");
            helper.succeed();
        });
    }

    private static Villager setUpUShapedRoad(GameTestHelper helper, int roadZ) {
        GameTestHelpers.fence(helper);
        for (int x = 2; x <= 21; x++) {
            for (int z = 2; z <= 21; z++) helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
        }
        for (int z = 4; z <= roadZ; z++) {
            helper.setBlock(3, 0, z, Blocks.STONE_BRICKS);
            helper.setBlock(19, 0, z, Blocks.STONE_BRICKS);
        }
        for (int x = 3; x <= 19; x++) helper.setBlock(x, 0, roadZ, Blocks.STONE_BRICKS);
        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(3, 1, 4));
        return villager;
    }

    private static Path pathToGoal(GameTestHelper helper, Villager villager) {
        Path path = villager.getNavigation().createPath(helper.absolutePos(new BlockPos(19, 1, 4)), 0);
        if (path == null) throw helper.assertionException("No path at all");
        return path;
    }

    private static String describe(GameTestHelper helper, Path path) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < path.getNodeCount(); i++) {
            Node node = path.getNode(i);
            BlockPos rel = helper.relativePos(node.asBlockPos());
            out.append('(').append(rel.getX()).append(',').append(rel.getZ()).append(") ");
        }
        return out.toString();
    }
}
