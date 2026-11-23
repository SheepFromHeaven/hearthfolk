package com.sheepfromheaven.minenow.villager;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillageBorderTest {
    private static int length(List<VillageBorder.Edge> edges) {
        return edges.stream().mapToInt(e -> Math.abs(e.x2() - e.x1()) + Math.abs(e.z2() - e.z1())).sum();
    }

    /** The perimeter of the included columns, one unit edge at a time: what the merged edges must add up to. */
    private static int perimeter(List<BlockPos> anchors, int reach) {
        int edges = 0;
        for (int x = -50; x <= 50; x++) {
            for (int z = -50; z <= 50; z++) {
                if (!VillageBorder.includes(anchors, reach, x, z)) continue;
                if (!VillageBorder.includes(anchors, reach, x - 1, z)) edges++;
                if (!VillageBorder.includes(anchors, reach, x + 1, z)) edges++;
                if (!VillageBorder.includes(anchors, reach, x, z - 1)) edges++;
                if (!VillageBorder.includes(anchors, reach, x, z + 1)) edges++;
            }
        }
        return edges;
    }

    @Test
    void aColumnIsIncludedWithinTheReachOfAnAnchorWhateverTheHeight() {
        List<BlockPos> anchors = List.of(new BlockPos(0, 64, 0));
        assertTrue(VillageBorder.includes(anchors, 16, 16, 0));
        assertTrue(VillageBorder.includes(anchors, 16, 11, 11));
        assertTrue(!VillageBorder.includes(anchors, 16, 12, 12));
        assertTrue(!VillageBorder.includes(anchors, 16, 17, 0));
    }

    @Test
    void oneAnchorOfReachOneIsAPlusSignWithATwelveBlockBorder() {
        assertEquals(12, length(VillageBorder.edges(List.of(new BlockPos(0, 0, 0)), 1)));
    }

    @Test
    void theBorderIsTheOutlineOfWhatIsIncludedAndNothingInside() {
        Random random = new Random(7);
        for (int round = 0; round < 20; round++) {
            List<BlockPos> anchors = new java.util.ArrayList<>();
            for (int i = 0; i < 1 + random.nextInt(6); i++) anchors.add(new BlockPos(random.nextInt(21) - 10, random.nextInt(5), random.nextInt(21) - 10));
            assertEquals(perimeter(anchors, 4), length(VillageBorder.edges(anchors, 4)), "anchors " + anchors);
        }
    }

    @Test
    void straightStretchesAreOneEdgeNotOnePerBlock() {
        List<VillageBorder.Edge> edges = VillageBorder.edges(List.of(new BlockPos(0, 0, 0)), 16);
        assertTrue(edges.stream().anyMatch(e -> Math.abs(e.x2() - e.x1()) + Math.abs(e.z2() - e.z1()) > 1), "the flat sides are merged");
        assertTrue(edges.size() < length(edges), "so there are fewer edges than blocks of border, " + edges.size() + " of " + length(edges));
    }

    @Test
    void twoAnchorsOnTheSameSpotAreOneBorder() {
        assertEquals(length(VillageBorder.edges(List.of(new BlockPos(3, 0, 3)), 8)),
                length(VillageBorder.edges(List.of(new BlockPos(3, 0, 3), new BlockPos(3, 9, 3)), 8)));
    }

    @Test
    void noAnchorsNoBorder() {
        assertTrue(VillageBorder.edges(List.of(), 16).isEmpty());
    }

    @Test
    void growthIsOnlyTheBorderOutsideTheVillage() {
        List<BlockPos> village = List.of(new BlockPos(0, 64, 0));
        List<BlockPos> extra = List.of(new BlockPos(40, 64, 0));
        assertEquals(length(VillageBorder.edges(extra, 16)), length(VillageBorder.growth(extra, village, 16)), "far from the village the whole outline counts");
        List<BlockPos> near = List.of(new BlockPos(20, 64, 0));
        assertTrue(length(VillageBorder.growth(near, village, 16)) < length(VillageBorder.edges(near, 16)), "where the circles overlap the part inside the village is left out");
        assertTrue(VillageBorder.growth(List.of(new BlockPos(0, 64, 0)), village, 16).isEmpty(), "a spot already inside adds nothing");
    }
}
