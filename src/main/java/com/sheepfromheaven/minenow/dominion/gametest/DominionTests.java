package com.sheepfromheaven.minenow.dominion.gametest;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;

import com.sheepfromheaven.minenow.dominion.townbanner.TownBannerBlocks;
import com.sheepfromheaven.minenow.structure.StructureBlocks;
import com.sheepfromheaven.minenow.structure.block.StructureMarkerBlock;
import com.sheepfromheaven.minenow.structure.block.StructureMarkerBlockEntity;
import com.sheepfromheaven.minenow.dominion.townbanner.TownBannerBlock;
import com.sheepfromheaven.minenow.dominion.DominionInhabitant;
import com.sheepfromheaven.minenow.dominion.DominionSavedData;
import com.sheepfromheaven.minenow.structure.DominionStructure;
import com.sheepfromheaven.minenow.structure.StructureType;
import com.sheepfromheaven.minenow.inhabitant.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;

import java.util.Set;
import java.util.UUID;

// Registered into the vanilla test_function registry by MineNow's RegisterEvent listener;
// the matching data/minenow/test_instance/*.json files supply the structure and tick budget.
public class DominionTests {

    public static void disbandingDominionConvertsInhabitantsAndClearsData(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);

        var level = helper.getLevel();
        var bannerRelPos = new BlockPos(1, 1, 1);
        var bannerPos = helper.absolutePos(bannerRelPos);
        helper.setBlock(bannerRelPos, TownBannerBlocks.TOWN_BANNER.get());

        var structureRelPos = new BlockPos(3, 1, 3);
        var structurePos = helper.absolutePos(structureRelPos);
        helper.setBlock(structureRelPos, Blocks.STONE);

        var data = DominionSavedData.get(level);
        var dominion = data.createDominion(UUID.randomUUID(), "Test Dominion", bannerPos);
        var dominionId = dominion.getId();
        var structure = new DominionStructure("Home", StructureType.HOUSE, structurePos, Set.of(structurePos));
        dominion.addStructure(structure);

        var inhabitantPos = new BlockPos(2, 1, 2);
        var inhabitant = helper.spawn(ModEntities.INHABITANT.get(), inhabitantPos);
        inhabitant.setDominionId(dominionId);
        dominion.addInhabitant(new DominionInhabitant(inhabitant.getUUID(), "Test Inhabitant"));

        var player = helper.makeMockPlayer(GameType.CREATIVE);
        var bannerBlock = (TownBannerBlock) level.getBlockState(bannerPos).getBlock();
        bannerBlock.playerWillDestroy(level, bannerPos, level.getBlockState(bannerPos), player);

        helper.succeedWhen(() -> {
            helper.assertTrue(inhabitant.isRemoved(), "Inhabitant entity should be discarded after disband");

            var villagerAbsPos = helper.absolutePos(inhabitantPos);
            boolean villagerSpawned = !level.getEntitiesOfClass(Villager.class, new AABB(villagerAbsPos).inflate(2.0))
                    .isEmpty();
            helper.assertTrue(villagerSpawned, "A vanilla villager should replace the inhabitant after disband");

            helper.assertTrue(level.getBlockState(structurePos).isAir(),
                    "Structure blocks should be destroyed after disband");

            helper.assertTrue(DominionSavedData.get(level).getDominionById(dominionId) == null,
                    "Dominion data should be deleted after disband");
        });
    }

    public static void destroyingStructureRemovesInhabitantHomeAndJob(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);

        var level = helper.getLevel();
        var bannerPos = helper.absolutePos(new BlockPos(1, 1, 1));
        var data = DominionSavedData.get(level);
        var dominion = data.createDominion(UUID.randomUUID(), "Test Dominion", bannerPos);

        var structureRelPos = new BlockPos(3, 1, 3);
        var structurePos = helper.absolutePos(structureRelPos);
        helper.setBlock(structureRelPos, StructureBlocks.STRUCTURE_MARKER.get());

        var structure = new DominionStructure("Home", StructureType.HOUSE, structurePos, Set.of(structurePos));
        dominion.addStructure(structure);
        var structureId = structure.getId();

        if (level.getBlockEntity(structurePos) instanceof StructureMarkerBlockEntity be) {
            be.setStructureData(structure.getName(), structure.getType(), dominion.getId(), structureId);
        }

        var inhabitant = helper.spawn(ModEntities.INHABITANT.get(), new BlockPos(2, 1, 2));
        inhabitant.setDominionId(dominion.getId());
        inhabitant.setHomeStructureId(structureId);
        inhabitant.setWorkStructureId(structureId);

        var dominionInhabitant = new DominionInhabitant(inhabitant.getUUID(), "Test Inhabitant");
        dominionInhabitant.setHomeStructureId(structureId);
        dominionInhabitant.setWorkStructureId(structureId);
        dominion.addInhabitant(dominionInhabitant);
        structure.addResident(dominionInhabitant.getId());
        structure.addWorker(dominionInhabitant.getId());

        var player = helper.makeMockPlayer(GameType.CREATIVE);
        var structureMarkerBlock = (StructureMarkerBlock) level.getBlockState(structurePos).getBlock();
        structureMarkerBlock.playerWillDestroy(level, structurePos, level.getBlockState(structurePos), player);

        helper.succeedWhen(() -> {
            helper.assertTrue(dominion.getStructureById(structureId) == null,
                    "Structure should be removed from the dominion after being destroyed");
            helper.assertTrue(inhabitant.getHomeStructureId() == null,
                    "Inhabitant's home structure should be cleared after the structure is destroyed");
            helper.assertTrue(inhabitant.getWorkStructureId() == null,
                    "Inhabitant's work structure should be cleared after the structure is destroyed");
            helper.assertTrue(!inhabitant.isRemoved(),
                    "Inhabitant should remain in the dominion, only unassigned from its home/job");
        });
    }

    public static void assigningNewHomeUnassignsInhabitantFromPreviousHome(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);

        var level = helper.getLevel();
        var bannerPos = helper.absolutePos(new BlockPos(1, 1, 1));
        var dominion = DominionSavedData.get(level).createDominion(UUID.randomUUID(), "Test Dominion", bannerPos);

        var houseARelPos = new BlockPos(3, 1, 3);
        helper.setBlock(houseARelPos, Blocks.RED_BED.defaultBlockState()
                .setValue(BedBlock.FACING, Direction.NORTH)
                .setValue(BedBlock.PART, BedPart.HEAD));
        var houseAPos = helper.absolutePos(houseARelPos);
        var houseA = new DominionStructure("House A", StructureType.HOUSE, houseAPos, Set.of(houseAPos));
        dominion.addStructure(houseA);

        var houseBRelPos = new BlockPos(5, 1, 3);
        helper.setBlock(houseBRelPos, Blocks.RED_BED.defaultBlockState()
                .setValue(BedBlock.FACING, Direction.NORTH)
                .setValue(BedBlock.PART, BedPart.HEAD));
        var houseBPos = helper.absolutePos(houseBRelPos);
        var houseB = new DominionStructure("House B", StructureType.HOUSE, houseBPos, Set.of(houseBPos));
        dominion.addStructure(houseB);

        var inhabitant = helper.spawn(ModEntities.INHABITANT.get(), new BlockPos(2, 1, 2));
        inhabitant.setDominionId(dominion.getId());
        var dominionInhabitant = new DominionInhabitant(inhabitant.getUUID(), "Test Inhabitant");
        dominion.addInhabitant(dominionInhabitant);

        dominion.assignHouse(houseA, dominionInhabitant.getId(), level);
        dominion.assignHouse(houseB, dominionInhabitant.getId(), level);

        helper.succeedWhen(() -> {
            helper.assertTrue(houseA.getAssignedResidentIds().isEmpty(),
                    "Previous house should no longer be assigned to the inhabitant");
            helper.assertTrue(houseB.getAssignedResidentIds().contains(dominionInhabitant.getId()),
                    "New house should be assigned to the inhabitant");
            helper.assertTrue(houseB.getId().equals(dominionInhabitant.getHomeStructureId()),
                    "Inhabitant's home structure should point at the new house only");
            helper.assertTrue(houseB.getId().equals(inhabitant.getHomeStructureId()),
                    "Inhabitant entity's home structure should point at the new house only");
        });
    }

    public static void assigningNewJobUnassignsInhabitantFromPreviousJob(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);

        var level = helper.getLevel();
        var bannerPos = helper.absolutePos(new BlockPos(1, 1, 1));
        var dominion = DominionSavedData.get(level).createDominion(UUID.randomUUID(), "Test Dominion", bannerPos);

        var jobAPos = helper.absolutePos(new BlockPos(3, 1, 3));
        var jobA = new DominionStructure("Fishery A", StructureType.FISHERY, jobAPos, Set.of(jobAPos));
        dominion.addStructure(jobA);

        var jobBPos = helper.absolutePos(new BlockPos(5, 1, 3));
        var jobB = new DominionStructure("Fishery B", StructureType.FISHERY, jobBPos, Set.of(jobBPos));
        dominion.addStructure(jobB);

        var inhabitant = helper.spawn(ModEntities.INHABITANT.get(), new BlockPos(2, 1, 2));
        inhabitant.setDominionId(dominion.getId());
        var dominionInhabitant = new DominionInhabitant(inhabitant.getUUID(), "Test Inhabitant");
        dominion.addInhabitant(dominionInhabitant);

        dominion.assignWorker(jobA, dominionInhabitant.getId(), level);
        dominion.assignWorker(jobB, dominionInhabitant.getId(), level);

        helper.succeedWhen(() -> {
            helper.assertTrue(jobA.getAssignedWorkerIds().isEmpty(),
                    "Previous job should no longer be assigned to the inhabitant");
            helper.assertTrue(jobB.getAssignedWorkerIds().contains(dominionInhabitant.getId()),
                    "New job should be assigned to the inhabitant");
            helper.assertTrue(jobB.getId().equals(dominionInhabitant.getWorkStructureId()),
                    "Inhabitant's work structure should point at the new job only");
            helper.assertTrue(jobB.getId().equals(inhabitant.getWorkStructureId()),
                    "Inhabitant entity's work structure should point at the new job only");
        });
    }

    public static void assigningHouseReservesBedFromVanillaVillagers(GameTestHelper helper) {
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
        var dominionInhabitant = new DominionInhabitant(inhabitant.getUUID(), "Test Inhabitant");
        dominion.addInhabitant(dominionInhabitant);

        var poiManager = level.getPoiManager();
        boolean freeBeforeAssign = poiManager.getCountInRange(type -> type.is(PoiTypes.HOME), bedPos, 1, PoiManager.Occupancy.HAS_SPACE) > 0;

        dominion.assignHouse(structure, dominionInhabitant.getId(), level);
        boolean freeAfterAssign = poiManager.getCountInRange(type -> type.is(PoiTypes.HOME), bedPos, 1, PoiManager.Occupancy.HAS_SPACE) > 0;

        dominion.unassignInhabitant(structure, dominionInhabitant.getId(), level);
        boolean freeAfterUnassign = poiManager.getCountInRange(type -> type.is(PoiTypes.HOME), bedPos, 1, PoiManager.Occupancy.HAS_SPACE) > 0;

        helper.succeedWhen(() -> {
            helper.assertTrue(freeBeforeAssign, "Bed should have a free ticket before it's assigned to an inhabitant");
            helper.assertTrue(!freeAfterAssign, "Bed should have no free tickets left for a vanilla villager once assigned to an inhabitant");
            helper.assertTrue(freeAfterUnassign, "Bed should have a free ticket again once unassigned from the inhabitant");
        });
    }

    public static void assigningInhabitantToHouseWithoutBedIsRefused(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);

        var level = helper.getLevel();
        var housePos = helper.absolutePos(new BlockPos(3, 1, 3));
        var bannerPos = helper.absolutePos(new BlockPos(1, 1, 1));

        var dominion = DominionSavedData.get(level).createDominion(UUID.randomUUID(), "Test Dominion", bannerPos);
        var structure = new DominionStructure("Home", StructureType.HOUSE, housePos, Set.of(housePos));
        dominion.addStructure(structure);

        var inhabitant = helper.spawn(ModEntities.INHABITANT.get(), new BlockPos(1, 1, 1));
        inhabitant.setDominionId(dominion.getId());
        var dominionInhabitant = new DominionInhabitant(inhabitant.getUUID(), "Test Inhabitant");
        dominion.addInhabitant(dominionInhabitant);

        boolean assigned = dominion.assignHouse(structure, dominionInhabitant.getId(), level);

        helper.succeedWhen(() -> {
            helper.assertTrue(!assigned, "Assigning a house with no bed should be refused");
            helper.assertTrue(structure.getAssignedResidentIds().isEmpty(),
                    "House without a bed should have no assigned inhabitant");
            helper.assertTrue(dominionInhabitant.getHomeStructureId() == null,
                    "Inhabitant should not be homed when the assignment was refused");
        });
    }

    public static void inhabitantComplainsWhenAssignedBedIsBroken(GameTestHelper helper) {
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
        var dominionInhabitant = new DominionInhabitant(inhabitant.getUUID(), "Test Inhabitant");
        dominion.addInhabitant(dominionInhabitant);

        helper.assertTrue(dominion.assignHouse(structure, dominionInhabitant.getId(), level),
                "Assigning a house with a bed should succeed");

        helper.setBlock(bedRelPos, Blocks.AIR.defaultBlockState());

        helper.succeedWhen(() -> helper.assertTrue(
                inhabitant.getIssues().contains("There is no Bed in my house"),
                "Inhabitant should complain once its assigned home's bed is broken"));
    }

    public static void findNearestStructureOfTypePicksClosestMatch(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);

        var level = helper.getLevel();
        var bannerPos = helper.absolutePos(new BlockPos(1, 1, 1));
        var dominion = DominionSavedData.get(level).createDominion(UUID.randomUUID(), "Test Dominion", bannerPos);

        var nearPos = helper.absolutePos(new BlockPos(3, 1, 3));
        var nearTavern = new DominionStructure("Near Tavern", StructureType.TAVERN, nearPos, Set.of(nearPos));
        dominion.addStructure(nearTavern);

        var farPos = helper.absolutePos(new BlockPos(8, 1, 8));
        var farTavern = new DominionStructure("Far Tavern", StructureType.TAVERN, farPos, Set.of(farPos));
        dominion.addStructure(farTavern);

        var from = helper.absolutePos(new BlockPos(1, 1, 1));
        var found = dominion.findNearestStructureOfType(StructureType.TAVERN, from, s -> true);

        helper.succeedWhen(() ->
                helper.assertTrue(found.isPresent() && found.get().getId().equals(nearTavern.getId()),
                        "The nearer tavern should be picked when both satisfy the filter"));
    }

    public static void findNearestStructureOfTypeSkipsClosestWhenFilterFails(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);

        var level = helper.getLevel();
        var bannerPos = helper.absolutePos(new BlockPos(1, 1, 1));
        var dominion = DominionSavedData.get(level).createDominion(UUID.randomUUID(), "Test Dominion", bannerPos);

        var nearPos = helper.absolutePos(new BlockPos(3, 1, 3));
        var nearTavern = new DominionStructure("Near Tavern (no food)", StructureType.TAVERN, nearPos, Set.of(nearPos));
        dominion.addStructure(nearTavern);

        var farPos = helper.absolutePos(new BlockPos(8, 1, 8));
        var farTavern = new DominionStructure("Far Tavern (has food)", StructureType.TAVERN, farPos, Set.of(farPos));
        dominion.addStructure(farTavern);

        var from = helper.absolutePos(new BlockPos(1, 1, 1));
        var found = dominion.findNearestStructureOfType(StructureType.TAVERN, from, s -> s.getId().equals(farTavern.getId()));

        helper.succeedWhen(() ->
                helper.assertTrue(found.isPresent() && found.get().getId().equals(farTavern.getId()),
                        "The farther tavern should be picked when the nearer one fails the filter"));
    }

    /**
     * Reproduces the "issues button always shows red !" bug with a real, fully-built house (walls,
     * floor, ceiling, and a bed) instead of a hand-crafted room set - the room's interior is detected
     * the same way {@link StructureMarkerBlock} does it in-game, via a real
     * {@link com.sheepfromheaven.minenow.structure.RoomDetector} flood fill, to check whether that
     * detection correctly recognizes the bed as satisfying the house's requirement.
     */
    public static void bedInRealDetectedRoomSatisfiesHouseRequirement(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);

        // A 5x5 footprint (x:1-5, z:1-5) enclosing a 3x3 interior (x:2-4, z:2-4), two blocks tall,
        // with solid floor/walls/ceiling - built the way a player would build a house.
        for (int x = 1; x <= 5; x++) {
            for (int z = 1; z <= 5; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                helper.setBlock(new BlockPos(x, 3, z), Blocks.STONE);
            }
        }
        for (int x = 1; x <= 5; x++) {
            for (int y = 1; y <= 2; y++) {
                helper.setBlock(new BlockPos(x, y, 1), Blocks.STONE);
                helper.setBlock(new BlockPos(x, y, 5), Blocks.STONE);
            }
        }
        for (int z = 1; z <= 5; z++) {
            for (int y = 1; y <= 2; y++) {
                helper.setBlock(new BlockPos(1, y, z), Blocks.STONE);
                helper.setBlock(new BlockPos(5, y, z), Blocks.STONE);
            }
        }

        var bedHeadRelPos = new BlockPos(3, 1, 2);
        var bedFootRelPos = new BlockPos(3, 1, 3);
        helper.setBlock(bedHeadRelPos, Blocks.RED_BED.defaultBlockState()
                .setValue(BedBlock.FACING, Direction.SOUTH)
                .setValue(BedBlock.PART, BedPart.HEAD));
        helper.setBlock(bedFootRelPos, Blocks.RED_BED.defaultBlockState()
                .setValue(BedBlock.FACING, Direction.SOUTH)
                .setValue(BedBlock.PART, BedPart.FOOT));

        var level = helper.getLevel();
        var markerRelPos = new BlockPos(3, 1, 4);
        var markerPos = helper.absolutePos(markerRelPos);
        var bannerPos = helper.absolutePos(new BlockPos(3, 1, 4));

        var dominion = DominionSavedData.get(level).createDominion(UUID.randomUUID(), "Test Dominion", bannerPos);

        var result = com.sheepfromheaven.minenow.structure.RoomDetector.detect(level, markerPos, 4096);
        helper.assertTrue(result instanceof com.sheepfromheaven.minenow.structure.RoomDetector.Result.Success,
                "The room should be detected as a valid enclosed space");
        var roomBlocks = ((com.sheepfromheaven.minenow.structure.RoomDetector.Result.Success) result).blocks();

        var structure = new DominionStructure("Home", StructureType.HOUSE, markerPos, roomBlocks);
        dominion.addStructure(structure);

        // checkRequirements() also reports the (informational, non-gating) room-size status - this test
        // is specifically about the bed being detected, so only that status needs to be satisfied.
        var bedStatus = structure.checkRequirements(level, dominion).stream()
                .filter(s -> s.name().equals("Bed"))
                .findFirst();
        helper.succeedWhen(() -> {
            helper.assertTrue(bedStatus.isPresent(), "The house should have a bed requirement");
            helper.assertTrue(bedStatus.get().satisfied(),
                    "The bed requirement should be satisfied by the bed actually in the room, but found "
                            + bedStatus.get().found() + "/" + bedStatus.get().required());
        });
    }
}
