package com.sheepfromheaven.minenow.structure.gametest;

import com.sheepfromheaven.minenow.dominion.DominionSavedData;
import com.sheepfromheaven.minenow.structure.DominionStructure;
import com.sheepfromheaven.minenow.structure.StructureLevelJsonConfig;
import com.sheepfromheaven.minenow.structure.StructureType;
import com.sheepfromheaven.minenow.structure.mine.MineClaim;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Set;
import java.util.UUID;

// Registered into the vanilla test_function registry by MineNow's RegisterEvent listener;
// the matching data/minenow/test_instance/*.json files supply the structure and tick budget.
public class WorkAreaMarkerRequirementTests {

    /**
     * A Mine gains a placement {@link com.sheepfromheaven.minenow.structure.Territory} from a
     * {@code territory} effect and requires a {@code mineClaim} marker within it as a level-1 work
     * condition. Verifies the odd-sized box geometry, that no claim leaves the work slot non-functioning,
     * that a claim inside the territory makes it functioning, and that a claim outside the territory does
     * not count. Uses a JSON override (like {@link StructureLevelJsonConfigTests}) and resets afterwards.
     */
    public static void mineClaimMarkerIsALevelOneWorkCondition(GameTestHelper helper) {
        try {
            var overrideDir = Files.createTempDirectory("minenow-marker-req-override");
            Files.writeString(overrideDir.resolve("minenow-structure-levels.json"), """
                    {
                      "MINE": {
                        "1": {
                          "requirements": [ { "marker": "mineClaim", "name": "Mine Claim", "count": 1 } ],
                          "effects": [
                            { "type": "inhabitantSlots", "amount": 1 },
                            { "type": "territory", "x": 21, "y": 21, "z": 21 }
                          ]
                        }
                      }
                    }
                    """);
            StructureLevelJsonConfig.load(overrideDir);

            var level = helper.getLevel();
            var markerPos = helper.absolutePos(new BlockPos(2, 2, 2));

            var dominion = DominionSavedData.get(level).createDominion(UUID.randomUUID(), "Test Dominion", markerPos);
            var structure = new DominionStructure("Mine", StructureType.MINE, markerPos, Set.of(markerPos));
            dominion.addStructure(structure);

            // 21 wide on each axis -> 10 blocks reachable to each side of the centre marker.
            var territory = structure.getTerritory();
            boolean territoryIsOddCentredBox = territory != null
                    && territory.halfX() == 10 && territory.halfY() == 10 && territory.halfZ() == 10;

            int slotsWithNoClaim = structure.getFunctioningInhabitantSlots(level, dominion);

            var inTerritory = new MineClaim(markerPos.offset(6, 0, 6));
            inTerritory.setAssignedStructureId(structure.getId());
            dominion.addMineClaim(inTerritory);
            int slotsWithClaimInTerritory = structure.getFunctioningInhabitantSlots(level, dominion);

            // A claim assigned to the structure but sitting outside its territory must not satisfy the
            // condition - use a second structure so it's the only claim in play.
            var farMarkerPos = helper.absolutePos(new BlockPos(2, 2, 40));
            var farStructure = new DominionStructure("Far Mine", StructureType.MINE, farMarkerPos, Set.of(farMarkerPos));
            dominion.addStructure(farStructure);
            var outsideTerritory = new MineClaim(farMarkerPos.offset(50, 0, 0));
            outsideTerritory.setAssignedStructureId(farStructure.getId());
            dominion.addMineClaim(outsideTerritory);
            int slotsWithClaimOutsideTerritory = farStructure.getFunctioningInhabitantSlots(level, dominion);

            // Reset: reload from a brand-new empty directory so the bundled resource gets copied in fresh.
            StructureLevelJsonConfig.load(Files.createTempDirectory("minenow-marker-req-reset"));

            helper.succeedWhen(() -> {
                helper.assertTrue(territoryIsOddCentredBox, "A 21-wide territory effect should give a box reaching 10 blocks each way");
                helper.assertTrue(slotsWithNoClaim == 0, "With no claim placed, the Mine's work slot should be non-functioning");
                helper.assertTrue(slotsWithClaimInTerritory == 1, "A claim within the territory should make the work slot functioning");
                helper.assertTrue(slotsWithClaimOutsideTerritory == 0, "A claim outside the territory should not satisfy the work condition");
            });
        } catch (IOException e) {
            helper.fail("Failed to set up temp config directories: " + e.getMessage());
        }
    }

    /**
     * The placement query attaches a marker to a structure by <em>territory</em>, not by which dominion
     * contains the marker: a spot inside a Mine's territory but outside the dominion bounds still matches,
     * while a spot beyond the territory - or a query from another player - does not.
     */
    public static void territoryPlacementQueryRespectsTerritoryOwnerAndBounds(GameTestHelper helper) {
        try {
            var overrideDir = Files.createTempDirectory("minenow-territory-query");
            Files.writeString(overrideDir.resolve("minenow-structure-levels.json"),
                    "{ \"MINE\": { \"1\": { \"effects\": [ { \"type\": \"territory\", \"x\": 21, \"y\": 21, \"z\": 21 } ] } } }");
            StructureLevelJsonConfig.load(overrideDir);

            var level = helper.getLevel();
            var owner = UUID.randomUUID();
            var bannerPos = helper.absolutePos(new BlockPos(2, 2, 2));
            // Mine near the dominion edge so its territory (reach 10) pokes past the 64-block dominion radius.
            var minePos = bannerPos.offset(60, 0, 0);
            var data = DominionSavedData.get(level);
            var dominion = data.createDominion(owner, "Test Dominion", bannerPos);
            var mine = new DominionStructure("Mine", StructureType.MINE, minePos, Set.of(minePos));
            dominion.addStructure(mine);

            var insideTerritoryOutsideDominion = minePos.offset(10, 0, 0); // dx=10 -> in territory; x=72 > 64 -> outside dominion
            var beyondTerritory = minePos.offset(15, 0, 0);

            var match = data.findStructureWithTerritoryContaining(insideTerritoryOutsideDominion, StructureType.MINE, owner);
            var noMatchBeyond = data.findStructureWithTerritoryContaining(beyondTerritory, StructureType.MINE, owner);
            var noMatchWrongOwner = data.findStructureWithTerritoryContaining(insideTerritoryOutsideDominion, StructureType.MINE, UUID.randomUUID());

            StructureLevelJsonConfig.load(Files.createTempDirectory("minenow-territory-query-reset"));

            helper.succeedWhen(() -> {
                helper.assertTrue(match != null && match.structure().getId().equals(mine.getId()),
                        "A spot inside the Mine's territory should match even when it lies outside the dominion bounds");
                helper.assertTrue(noMatchBeyond == null, "A spot beyond the territory reach should not match");
                helper.assertTrue(noMatchWrongOwner == null, "Another player's query should not match this Mine");
            });
        } catch (IOException e) {
            helper.fail("Failed to set up temp config directories: " + e.getMessage());
        }
    }

    /** An even territory size rounds up to the next odd number so a single centre block always exists. */
    public static void evenTerritorySizeRoundsUpToKeepCenter(GameTestHelper helper) {
        try {
            var overrideDir = Files.createTempDirectory("minenow-territory-even");
            Files.writeString(overrideDir.resolve("minenow-structure-levels.json"),
                    "{ \"MINE\": { \"1\": { \"effects\": [ { \"type\": \"territory\", \"x\": 20, \"y\": 20, \"z\": 20 } ] } } }");
            StructureLevelJsonConfig.load(overrideDir);

            var level = helper.getLevel();
            var markerPos = helper.absolutePos(new BlockPos(2, 2, 2));
            var dominion = DominionSavedData.get(level).createDominion(UUID.randomUUID(), "Test Dominion", markerPos);
            var mine = new DominionStructure("Mine", StructureType.MINE, markerPos, Set.of(markerPos));
            dominion.addStructure(mine);
            var territory = mine.getTerritory();

            StructureLevelJsonConfig.load(Files.createTempDirectory("minenow-territory-even-reset"));

            helper.succeedWhen(() -> helper.assertTrue(
                    territory != null && territory.halfX() == 10 && territory.halfY() == 10 && territory.halfZ() == 10,
                    "An even territory size of 20 should round up to 21, giving half-extents of 10 around the centre marker"));
        } catch (IOException e) {
            helper.fail("Failed to set up temp config directories: " + e.getMessage());
        }
    }
}
