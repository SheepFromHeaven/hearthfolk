package com.sheepfromheaven.minenow.gametest;

import com.sheepfromheaven.minenow.dominion.DominionSavedData;
import com.sheepfromheaven.minenow.dominion.DominionStructure;
import com.sheepfromheaven.minenow.dominion.StructureType;
import com.sheepfromheaven.minenow.entity.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.Set;
import java.util.UUID;

// Registered into the vanilla test_function registry by MineNow's RegisterEvent listener;
// the matching data/minenow/test_instance/*.json files supply the structure and tick budget.
public class GoHomeGoalTests {

    public static void inhabitantSleepsInAssignedBedAtNight(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);

        var bedRelPos = new BlockPos(3, 1, 3);
        helper.setBlock(bedRelPos, Blocks.RED_BED.defaultBlockState()
                .setValue(BedBlock.FACING, Direction.NORTH)
                .setValue(BedBlock.PART, BedPart.HEAD));

        var level = helper.getLevel();
        var bedPos = helper.absolutePos(bedRelPos);
        var bannerPos = helper.absolutePos(new BlockPos(1, 1, 1));

        var dominion = DominionSavedData.get(level).createDominion(UUID.randomUUID(), "Test Dominion", bannerPos);
        var structure = new DominionStructure("Home", StructureType.HOUSE, bedPos, Set.of(bedPos));
        dominion.addStructure(structure);

        var inhabitant = helper.spawn(ModEntities.INHABITANT.get(), new BlockPos(1, 1, 1));
        inhabitant.setDominionId(dominion.getId());
        inhabitant.setHomeStructureId(structure.getId());

        // Night window from GoHomeGoal: dayTime in [12542, 23460).
        level.setDayTime(13000L);

        helper.succeedWhen(() -> helper.assertTrue(inhabitant.isSleeping(), "Inhabitant should be asleep in its assigned bed at night"));
    }

    public static void inhabitantWalksHomeWhenRequested(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);

        var level = helper.getLevel();
        var bannerPos = helper.absolutePos(new BlockPos(1, 1, 1));

        var dominion = DominionSavedData.get(level).createDominion(UUID.randomUUID(), "Test Dominion", bannerPos);
        // No structure assigned: GoHomeGoal falls back to the dominion's banner position.
        var inhabitant = helper.spawn(ModEntities.INHABITANT.get(), new BlockPos(3, 1, 1));
        inhabitant.setDominionId(dominion.getId());
        inhabitant.goHome();

        helper.succeedWhen(() -> helper.assertTrue(
                inhabitant.distanceToSqr(bannerPos.getX() + 0.5, bannerPos.getY(), bannerPos.getZ() + 0.5) <= 4.0,
                "Inhabitant should have walked to the dominion banner"));
    }

    public static void inhabitantWalksAroundHouseToBed(GameTestHelper helper) {
        buildHouseObstacle(helper, HouseOpening.AIR_GAP);
        assertInhabitantSpawnedBehindHouseReachesBed(helper,
                "Inhabitant spawned behind the house should still path around to its bed and sleep");
    }

    public static void inhabitantOpensDoorToReachBed(GameTestHelper helper) {
        buildHouseObstacle(helper, HouseOpening.DOOR);
        // Spawn directly in front of the door rather than behind the house.
        // The walk-around-house path is tested by inhabitantWalksAroundHouseToBed;
        // this test only needs to verify that the door is opened to pass through.
        var bedRelPos = new BlockPos(4, 0, 4);
        var level = helper.getLevel();
        var bedPos = helper.absolutePos(bedRelPos);
        var bannerPos = helper.absolutePos(bedRelPos);

        var dominion = DominionSavedData.get(level).createDominion(UUID.randomUUID(), "Test Dominion", bannerPos);
        var structure = new DominionStructure("Home", StructureType.HOUSE, bedPos, Set.of(bedPos));
        dominion.addStructure(structure);

        var inhabitant = helper.spawn(ModEntities.INHABITANT.get(), new BlockPos(4, 1, 1));
        inhabitant.setDominionId(dominion.getId());
        inhabitant.setHomeStructureId(structure.getId());

        level.setDayTime(13000L);

        helper.succeedWhen(() -> helper.assertTrue(inhabitant.isSleeping(),
                "Inhabitant spawned in front of a closed door should open it and reach its bed"));
    }

    public static void inhabitantOpensGateToReachBed(GameTestHelper helper) {
        buildFencedYardObstacle(helper);
        assertInhabitantSpawnedBehindHouseReachesBed(helper,
                "Inhabitant spawned behind a closed fence gate should open it and reach its bed");
    }

    private static void assertInhabitantSpawnedBehindHouseReachesBed(GameTestHelper helper, String failureMessage) {
        var bedRelPos = new BlockPos(4, 1, 4);
        var level = helper.getLevel();
        var bedPos = helper.absolutePos(bedRelPos);
        var bannerPos = helper.absolutePos(bedRelPos);

        var dominion = DominionSavedData.get(level).createDominion(UUID.randomUUID(), "Test Dominion", bannerPos);
        var structure = new DominionStructure("Home", StructureType.HOUSE, bedPos, Set.of(bedPos));
        dominion.addStructure(structure);

        // Spawned behind the house (north side, opposite the opening on the south wall),
        // so it must path around one side of the house and back in through the opening.
        var inhabitant = helper.spawn(ModEntities.INHABITANT.get(), new BlockPos(4, 1, 7));
        inhabitant.setDominionId(dominion.getId());
        inhabitant.setHomeStructureId(structure.getId());

        level.setDayTime(13000L);

        helper.succeedWhen(() -> helper.assertTrue(inhabitant.isSleeping(), failureMessage));
    }

    private enum HouseOpening {
        /** A plain 3-high hole in the wall, no block to interact with. */
        AIR_GAP,
        /** A real two-block-tall door that must be opened to pass through. */
        DOOR
    }

    /**
     * Clears the whole test volume to air first (the test structure has no block data of its own,
     * so without this the surrounding overworld terrain leaks into the test area and makes
     * navigation non-deterministic), then builds a hollow 3x3 house (interior x/z 3..5) with
     * stone walls at x=2, x=6, z=2, z=6, y=1..3, and either an open gap or a closed door in the
     * south wall (z=2) at x=4, and a floor under the whole footprint.
     */
    private static void buildHouseObstacle(GameTestHelper helper, HouseOpening opening) {
        clearVolumeAndFloor(helper);

        // Walls at y=0..2 so the entity (walking at y=0) is fully blocked. y=3 is open sky.
        for (int x = 2; x <= 6; x++) {
            for (int y = 0; y <= 2; y++) {
                helper.setBlock(new BlockPos(x, y, 2), x == 4 ? Blocks.AIR : Blocks.STONE);
                helper.setBlock(new BlockPos(x, y, 6), Blocks.STONE);
            }
        }
        for (int z = 2; z <= 6; z++) {
            for (int y = 0; y <= 2; y++) {
                helper.setBlock(new BlockPos(2, y, z), Blocks.STONE);
                helper.setBlock(new BlockPos(6, y, z), Blocks.STONE);
            }
        }

        if (opening == HouseOpening.DOOR) {
            // Lintel above the door at y=2 (stone); door occupies y=0 (lower) and y=1 (upper).
            helper.setBlock(new BlockPos(4, 2, 2), Blocks.STONE);
            helper.setBlock(new BlockPos(4, 0, 2), Blocks.OAK_DOOR.defaultBlockState()
                    .setValue(DoorBlock.FACING, Direction.NORTH)
                    .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER)
                    .setValue(DoorBlock.HINGE, DoorHingeSide.LEFT));
            helper.setBlock(new BlockPos(4, 1, 2), Blocks.OAK_DOOR.defaultBlockState()
                    .setValue(DoorBlock.FACING, Direction.NORTH)
                    .setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER)
                    .setValue(DoorBlock.HINGE, DoorHingeSide.LEFT));
        }

        // Bed at y=0 (entity walking level) so the distance check's Y component is zero.
        helper.setBlock(new BlockPos(4, 0, 4), Blocks.RED_BED.defaultBlockState()
                .setValue(BedBlock.FACING, Direction.NORTH)
                .setValue(BedBlock.PART, BedPart.HEAD));
    }

    /**
     * Same footprint as {@link #buildHouseObstacle}, but the perimeter is a single-high fence
     * (not stone walls) with a closed fence gate as the only opening in the south side at x=4.
     */
    private static void buildFencedYardObstacle(GameTestHelper helper) {
        clearVolumeAndFloor(helper);

        for (int x = 2; x <= 6; x++) {
            helper.setBlock(new BlockPos(x, 1, 2), x == 4
                    ? Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, Direction.NORTH)
                    : Blocks.OAK_FENCE.defaultBlockState());
            helper.setBlock(new BlockPos(x, 1, 6), Blocks.OAK_FENCE.defaultBlockState());
        }
        for (int z = 2; z <= 6; z++) {
            helper.setBlock(new BlockPos(2, 1, z), Blocks.OAK_FENCE.defaultBlockState());
            helper.setBlock(new BlockPos(6, 1, z), Blocks.OAK_FENCE.defaultBlockState());
        }

        helper.setBlock(new BlockPos(4, 1, 4), Blocks.RED_BED.defaultBlockState()
                .setValue(BedBlock.FACING, Direction.NORTH)
                .setValue(BedBlock.PART, BedPart.HEAD));
    }

    private static void clearVolumeAndFloor(GameTestHelper helper) {
        for (int x = 0; x <= 8; x++) {
            for (int z = 0; z <= 8; z++) {
                for (int y = 0; y <= 4; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
        GameTestHelpers.makeFloor(helper);
    }
}
