package com.sheepfromheaven.minenow.structure;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureLevelingTest {

    private static final BlockPos MARKER_POS = new BlockPos(1, 1, 1);

    @Test
    void freshStructureStartsAtLevelOne() {
        var structure = new DominionStructure("Lumberyard", StructureType.LUMBERYARD, MARKER_POS, Set.of(MARKER_POS));

        assertEquals(1, structure.getLevel(), "A freshly created structure should start at level 1");
        assertFalse(structure.isMaxLevel(), "A level-1 structure should not already be at its max level");
    }

    @Test
    void levelEffectsGrantWorkSlotsAndProductivityStacking() {
        var structure = new DominionStructure("Lumberyard", StructureType.LUMBERYARD, MARKER_POS, Set.of(MARKER_POS));

        var level1Stats = structure.getStats();
        structure.setLevel(3);
        var level3Stats = structure.getStats();

        assertEquals(1, level1Stats.getInhabitantSlots(), "A level-1 lumberyard should grant exactly its base work slot");
        assertEquals(1.0, level1Stats.getProductivityMultiplier(), "A level-1 structure should have no productivity bonus");
        assertEquals(3, level3Stats.getInhabitantSlots(),
                "A level-3 structure should have gained +1 slot at level 2 and another at level 3, on top of its base slot");
        assertTrue(Math.abs(level3Stats.getProductivityMultiplier() - 1.25 * 1.25) < 0.0001,
                "Productivity bonuses from level 2 and level 3 should stack multiplicatively");
        assertEquals(level3Stats.getInhabitantSlots(), structure.getInhabitantSlots(),
                "DominionStructure.getInhabitantSlots() should match the folded stats");
        assertEquals(level3Stats.getProductivityMultiplier(), structure.getProductivityMultiplier(),
                "DominionStructure.getProductivityMultiplier() should match the folded stats");
    }
}
