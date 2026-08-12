package com.sheepfromheaven.minenow.structure.gametest;

import com.sheepfromheaven.minenow.effect.InhabitantSlotsEffect;
import com.sheepfromheaven.minenow.structure.StructureLevelJsonConfig;
import com.sheepfromheaven.minenow.structure.StructureType;
import net.minecraft.gametest.framework.GameTestHelper;

import java.io.IOException;
import java.nio.file.Files;

// Registered into the vanilla test_function registry by MineNow's RegisterEvent listener;
// the matching data/minenow/test_instance/*.json files supply the structure and tick budget.
public class StructureLevelJsonConfigTests {

    /**
     * {@link StructureLevelJsonConfig} keeps its data in a static map shared by the whole game, so this
     * test always finishes by reloading from a *fresh, empty* temp directory - the missing file there
     * makes {@code load} copy the bundled resource in, restoring every type (including LUMBERYARD) to its
     * normal behavior for whichever test runs next.
     */
    public static void jsonOverrideReplacesCompiledInLevelData(GameTestHelper helper) {
        try {
            var overrideDir = Files.createTempDirectory("minenow-test-override");
            Files.writeString(overrideDir.resolve("minenow-structure-levels.json"), """
                    {
                      "_version": 999,
                      "LUMBERYARD": {
                        "1": {
                          "requirements": [ { "block": "minecraft:barrel", "name": "Barrel", "count": 5 } ],
                          "effects": [ { "type": "inhabitantSlots", "amount": 2 } ]
                        }
                      }
                    }
                    """);
            StructureLevelJsonConfig.load(overrideDir);

            var overriddenEffects = StructureType.LUMBERYARD.getLevelEffects(1);
            var overriddenRequirements = StructureType.LUMBERYARD.getRequirements(1);

            boolean hasTwoSlotEffect = overriddenEffects.stream()
                    .anyMatch(e -> e instanceof InhabitantSlotsEffect ise && ise.getAmount() == 2);
            boolean requiresFiveBarrels = overriddenRequirements.stream()
                    .anyMatch(r -> "Barrel".equals(r.getDisplayName()));

            // Reset: reload from a brand-new empty directory so the bundled resource gets copied in fresh.
            var resetDir = Files.createTempDirectory("minenow-test-reset");
            StructureLevelJsonConfig.load(resetDir);
            var restoredEffects = StructureType.LUMBERYARD.getLevelEffects(1);
            boolean restoredToOneSlot = restoredEffects.stream()
                    .anyMatch(e -> e instanceof InhabitantSlotsEffect ise && ise.getAmount() == 1);

            helper.succeedWhen(() -> {
                helper.assertTrue(hasTwoSlotEffect, "The JSON override should replace level 1's work slot effect with 2");
                helper.assertTrue(requiresFiveBarrels, "The JSON override should replace level 1's barrel requirement");
                helper.assertTrue(restoredToOneSlot, "Reloading from a fresh directory should restore the compiled-in default (1 slot)");
            });
        } catch (IOException e) {
            helper.fail("Failed to set up temp config directories: " + e.getMessage());
        }
    }
}
