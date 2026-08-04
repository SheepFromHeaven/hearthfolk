package com.sheepfromheaven.minenow.gametest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

class GameTestHelpers {
    private GameTestHelpers() {}

    static void makeFloor(GameTestHelper helper) {
        AABB bounds = helper.getBounds();
        int width = (int) (bounds.maxX - bounds.minX);
        int length = (int) (bounds.maxZ - bounds.minZ);
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < length; z++) {
                helper.setBlock(x, -1, z, Blocks.STONE);
            }
        }
    }
}
