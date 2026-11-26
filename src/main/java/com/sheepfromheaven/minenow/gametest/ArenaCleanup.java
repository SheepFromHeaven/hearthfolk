package com.sheepfromheaven.minenow.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * Vanilla's game test server never clears a finished test, and each batch lays its structures out afresh, so the beds,
 * looms, chests and villagers of one batch stood in the next batch's surroundings and villagers claimed them (a village
 * reaches 48 blocks past its border). Every test therefore empties its own space when it ends. Only on the game test
 * server: the playground keeps its tests and labels standing.
 */
public final class ArenaCleanup {
    private ArenaCleanup() {}

    public static void install(GameTestHelper helper) {
        if (!(helper.getLevel().getServer() instanceof GameTestServer)) return;
        helper.testInfo.addListener(new GameTestListener() {
            @Override
            public void testStructureLoaded(GameTestInfo info) {}

            @Override
            public void testPassed(GameTestInfo info, GameTestRunner runner) {
                clear(info);
            }

            @Override
            public void testFailed(GameTestInfo info, GameTestRunner runner) {
                clear(info);
            }

            @Override
            public void testAddedForRerun(GameTestInfo original, GameTestInfo copy, GameTestRunner runner) {}
        });
    }

    private static void clear(GameTestInfo info) {
        ServerLevel level = info.getLevel();
        AABB area = info.getStructureBounds().inflate(2, 1, 2);
        for (Entity entity : level.getEntities((Entity) null, area, e -> !(e instanceof Player))) entity.discard();
        BlockPos.betweenClosedStream(area).forEach(pos -> {
            if (!level.getBlockState(pos).isAir()) level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        });
    }
}
