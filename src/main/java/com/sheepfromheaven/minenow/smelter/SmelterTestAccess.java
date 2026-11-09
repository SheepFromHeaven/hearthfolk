package com.sheepfromheaven.minenow.smelter;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.npc.villager.Villager;

/** Lets other features' gametests set up a smelter the way the smelter's own do. */
public final class SmelterTestAccess {
    public static final BlockPos BLAST_FURNACE = SmelterTests.BLAST_FURNACE;

    private SmelterTestAccess() {}

    public static Villager spawnSmelter(GameTestHelper helper) {
        return SmelterTests.spawnSmelter(helper);
    }
}
