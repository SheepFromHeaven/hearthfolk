package com.sheepfromheaven.minenow.structure.gametest;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;

import com.sheepfromheaven.minenow.dominion.DominionSavedData;
import com.sheepfromheaven.minenow.dominion.DominionTier;
import com.sheepfromheaven.minenow.structure.DominionStructure;
import com.sheepfromheaven.minenow.structure.StructureType;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

// Registered into the vanilla test_function registry by MineNow's RegisterEvent listener;
// the matching data/minenow/test_instance/*.json files supply the structure and tick budget.
public class StructureLevelingTests {

    private static Set<BlockPos> room(GameTestHelper helper, int width, int length) {
        var blocks = new HashSet<BlockPos>();
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < length; z++) {
                blocks.add(helper.absolutePos(new BlockPos(x, 1, z)));
            }
        }
        return blocks;
    }

    public static void levelUpFailsWithoutMeetingNextLevelRequirements(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);
        var level = helper.getLevel();
        var markerPos = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.setBlock(new BlockPos(2, 1, 1), Blocks.BARREL);

        var dominion = DominionSavedData.get(level).createDominion(UUID.randomUUID(), "Test Dominion", markerPos);
        dominion.setTier(DominionTier.CITY); // don't let the tier cap be the reason this fails
        var structure = new DominionStructure("Lumberyard", StructureType.LUMBERYARD, markerPos, room(helper, 2, 1));
        dominion.addStructure(structure);

        boolean upgraded = dominion.tryLevelUpStructure(structure.getId(), (net.minecraft.server.level.ServerLevel) level);

        helper.succeedWhen(() -> {
            helper.assertFalse(upgraded, "Leveling up should fail while level-2 requirements (bigger room, 2 barrels) aren't met");
            helper.assertTrue(structure.getLevel() == 1, "The structure should remain at level 1 after a failed upgrade attempt");
        });
    }

    public static void levelUpSucceedsOnceNextLevelRequirementsAreMet(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);
        var level = helper.getLevel();
        var markerPos = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.setBlock(new BlockPos(2, 1, 1), Blocks.BARREL);
        helper.setBlock(new BlockPos(3, 1, 1), Blocks.BARREL);

        var dominion = DominionSavedData.get(level).createDominion(UUID.randomUUID(), "Test Dominion", markerPos);
        dominion.setTier(DominionTier.CITY);
        // Lumberyard level-2 requirement: minSize 0+8=8, and 2 barrels (base count 1 + 1 extra).
        var structure = new DominionStructure("Lumberyard", StructureType.LUMBERYARD, markerPos, room(helper, 4, 2));
        dominion.addStructure(structure);

        boolean upgraded = dominion.tryLevelUpStructure(structure.getId(), (net.minecraft.server.level.ServerLevel) level);

        helper.succeedWhen(() -> {
            helper.assertTrue(upgraded, "Leveling up should succeed once level-2 requirements are met");
            helper.assertTrue(structure.getLevel() == 2, "The structure should now be at level 2");
            helper.assertTrue(structure.getProductivityMultiplier() > 1.0,
                    "A level-2 structure should have a productivity bonus over the base rate");
        });
    }

    public static void levelUpIsCappedByDominionTier(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);
        var level = helper.getLevel();
        var markerPos = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.setBlock(new BlockPos(2, 1, 1), Blocks.BARREL);
        helper.setBlock(new BlockPos(3, 1, 1), Blocks.BARREL);

        // SETTLEMENT (the starting tier) caps structures at level 1, even if level-2 requirements are met.
        var dominion = DominionSavedData.get(level).createDominion(UUID.randomUUID(), "Test Dominion", markerPos);
        var structure = new DominionStructure("Lumberyard", StructureType.LUMBERYARD, markerPos, room(helper, 4, 2));
        dominion.addStructure(structure);

        boolean upgraded = dominion.tryLevelUpStructure(structure.getId(), (net.minecraft.server.level.ServerLevel) level);

        helper.succeedWhen(() -> {
            helper.assertTrue(dominion.getTier() == DominionTier.SETTLEMENT, "Sanity: dominion should still be at its starting tier");
            helper.assertFalse(upgraded, "Leveling up should be refused when it would exceed the dominion's tier cap");
            helper.assertTrue(structure.getLevel() == 1, "The structure should remain at level 1 when capped by the dominion's tier");
            helper.assertTrue(dominion.getStructureLevelCap(structure.getId()) == 1,
                    "A SETTLEMENT-tier dominion should cap structure level at 1");
        });
    }

    /**
     * The scenario the level-2 work slot is meant to protect against: a level-2 lumberyard has two
     * workers, one in the base (level-1) slot and one in the slot the level-2 upgrade granted. Losing the
     * second barrel that level 2 requires should knock out only the second slot's worker - the first
     * slot's worker keeps working since the level-1 requirement (just one barrel) still holds.
     */
    public static void losingALevelUpRequirementOnlyDisablesTheSlotItUnlocked(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);
        var level = helper.getLevel();
        var markerPos = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.setBlock(new BlockPos(2, 1, 1), Blocks.BARREL);
        helper.setBlock(new BlockPos(3, 1, 1), Blocks.BARREL);

        var dominion = DominionSavedData.get(level).createDominion(UUID.randomUUID(), "Test Dominion", markerPos);
        dominion.setTier(DominionTier.CITY);
        var structure = new DominionStructure("Lumberyard", StructureType.LUMBERYARD, markerPos, room(helper, 4, 2));
        dominion.addStructure(structure);
        helper.assertTrue(dominion.tryLevelUpStructure(structure.getId(), (net.minecraft.server.level.ServerLevel) level),
                "Sanity: should reach level 2 with both barrels present");

        var firstWorker = UUID.randomUUID();
        var secondWorker = UUID.randomUUID();
        structure.addWorker(firstWorker);
        structure.addWorker(secondWorker);

        boolean bothWorkBeforeBreaking = structure.canWork(firstWorker, level, dominion) && structure.canWork(secondWorker, level, dominion);
        int slotsBeforeBreaking = structure.getFunctioningInhabitantSlots(level, dominion);

        helper.setBlock(new BlockPos(3, 1, 1), Blocks.AIR);
        boolean firstStillWorks = structure.canWork(firstWorker, level, dominion);
        boolean secondStillWorks = structure.canWork(secondWorker, level, dominion);
        int slotsAfterBreaking = structure.getFunctioningInhabitantSlots(level, dominion);

        helper.succeedWhen(() -> {
            helper.assertTrue(bothWorkBeforeBreaking, "Both workers should be able to work while both barrels are present");
            helper.assertTrue(slotsBeforeBreaking == 2, "A level-2 lumberyard with both barrels should have 2 functioning slots");
            helper.assertTrue(structure.getLevel() == 2, "Breaking a barrel should not change the structure's level");
            helper.assertTrue(firstStillWorks, "The first (level-1) worker should keep working - level 1 only needs one barrel");
            helper.assertFalse(secondStillWorks, "The second (level-2) worker should stop working once the second barrel is gone");
            helper.assertTrue(slotsAfterBreaking == 1, "Only the level-1 slot should still be functioning");
        });
    }
}
