package com.sheepfromheaven.minenow.miner;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MineLayoutTest {
    private static final int TOP = 0;

    private static MineLayout sevenBySeven() {
        Set<BlockPos> outline = new HashSet<>();
        for (int x = 0; x < 7; x++) {
            for (int z = 0; z < 7; z++) outline.add(new BlockPos(x, TOP, z));
        }
        return new MineLayout(outline, TOP, new BlockPos(0, TOP, 1));
    }

    @Test
    void everyFifthLayerIsAPlatform() {
        MineLayout mine = sevenBySeven();
        assertFalse(mine.isPlatform(-1));
        assertFalse(mine.isPlatform(-4));
        assertTrue(mine.isPlatform(-5));
        assertTrue(mine.isPlatform(-10));
    }

    @Test
    void aPlatformKeepsATwoBlockRingAndOpensTheMiddle() {
        MineLayout mine = sevenBySeven();
        assertFalse(mine.digs(new BlockPos(0, -5, 0)));
        assertFalse(mine.digs(new BlockPos(1, -5, 3)));
        assertTrue(mine.digs(new BlockPos(2, -5, 2)));
        assertTrue(mine.digs(new BlockPos(3, -5, 3)));
        assertTrue(mine.digs(new BlockPos(4, -5, 4)));
        assertTrue(mine.digs(new BlockPos(1, -4, 1)), "the shaft is 7x7 between platforms");
        assertTrue(mine.digs(new BlockPos(1, -6, 1)), "...and below them");
    }

    @Test
    void theLadderGoesThroughEveryPlatform() {
        assertTrue(sevenBySeven().digs(new BlockPos(0, -5, 1)));
    }

    @Test
    void fourThreeByThreeTunnelsRunOutFromTheMiddleOfEachWall() {
        List<BlockPos> tunnels = sevenBySeven().tunnels(-5, 8);
        assertEquals(4 * 8 * 9, tunnels.size());
        assertTrue(tunnels.contains(new BlockPos(7, -4, 2)), "east tunnel starts beside the shaft, one above the platform");
        assertTrue(tunnels.contains(new BlockPos(14, -2, 4)), "...and is 3 high and 3 wide, 8 long");
        assertFalse(tunnels.contains(new BlockPos(15, -4, 3)));
        assertTrue(tunnels.contains(new BlockPos(-1, -3, 3)), "west tunnel");
        assertTrue(tunnels.contains(new BlockPos(3, -3, -8)), "north tunnel");
        assertTrue(tunnels.contains(new BlockPos(3, -3, 14)), "south tunnel");
        assertFalse(tunnels.contains(new BlockPos(7, -4, 1)), "tunnels are only the middle 3 columns of a wall");
        assertFalse(tunnels.contains(new BlockPos(7, -5, 3)), "the tunnel floor is the platform level, left standing");
    }

    @Test
    void theLadderStaysOffTunnelWalls() {
        MineLayout mine = sevenBySeven();
        assertTrue(mine.tunnelWall(new BlockPos(0, 0, 3), net.minecraft.core.Direction.WEST));
        assertFalse(mine.tunnelWall(new BlockPos(0, 0, 1), net.minecraft.core.Direction.WEST));
    }

    @Test
    void aShaftNarrowerThanFiveBranchesNoTunnels() {
        Set<BlockPos> outline = new HashSet<>();
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) outline.add(new BlockPos(x, TOP, z));
        }
        MineLayout mine = new MineLayout(outline, TOP, new BlockPos(0, TOP, 0));
        assertTrue(mine.tunnels(-5, 8).isEmpty());
        assertFalse(mine.tunnelWall(new BlockPos(0, 0, 1), net.minecraft.core.Direction.WEST));
    }
}
