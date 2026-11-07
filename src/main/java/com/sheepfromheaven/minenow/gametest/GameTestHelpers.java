package com.sheepfromheaven.minenow.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class GameTestHelpers {
    private GameTestHelpers() {}

    // TEMPORARY debug aid: vanilla removes every barrier wall the instant a test passes
    // (TestInstanceBlockEntity#removeBarriers, called from GameTestRunner's testPassed listener),
    // which made it hard to tell "our own code tore the room down" apart from "vanilla just opened
    // it up on success". Every position barrierBounds() seals is registered here and re-asserted
    // as BARRIER on every server tick, for every test, so sealed rooms stay sealed after they pass.
    // Remove this registry + its call site in barrierBounds() once that's no longer needed.
    private static final Map<ServerLevel, Set<BlockPos>> enforcedBarrierPositions = new ConcurrentHashMap<>();
    private static boolean enforcementListenerRegistered = false;

    private static void ensureEnforcementListenerRegistered() {
        if (enforcementListenerRegistered) return;
        enforcementListenerRegistered = true;
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, event -> {
            enforcedBarrierPositions.forEach((level, positions) -> positions.forEach(pos -> {
                if (!level.getBlockState(pos).is(Blocks.BARRIER) && !level.getBlockState(pos).is(Blocks.TEST_INSTANCE_BLOCK)) {
                    level.setBlockAndUpdate(pos, Blocks.BARRIER.defaultBlockState());
                }
            }));
        });
    }

    public static void makeFloor(GameTestHelper helper) {
        AABB bounds = helper.getBounds();
        int width = (int) (bounds.maxX - bounds.minX);
        int length = (int) (bounds.maxZ - bounds.minZ);
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < length; z++) {
                helper.setBlock(x, -1, z, Blocks.STONE);
            }
        }
        barrierBounds(helper);
    }

    /**
     * Seals the test space with barrier blocks on all four walls and the ceiling, so that
     * wandering mobs (e.g. an idle inhabitant's WaterAvoidingRandomStrollGoal) can't path out
     * of the structure and into a neighboring test when many tests run side by side in a grid.
     * Called automatically by {@link #makeFloor}; only call directly if a test skips makeFloor.
     */
    public static void barrierBounds(GameTestHelper helper) {
        ensureEnforcementListenerRegistered();
        var enforced = enforcedBarrierPositions.computeIfAbsent(helper.getLevel(), l -> ConcurrentHashMap.newKeySet());

        AABB bounds = helper.getBounds();
        int width = (int) (bounds.maxX - bounds.minX);
        int height = (int) (bounds.maxY - bounds.minY);
        int length = (int) (bounds.maxZ - bounds.minZ);

        for (int x = -1; x <= width; x++) {
            for (int y = -1; y <= height; y++) {
                setBarrierUnlessTestInstanceBlock(helper, enforced, x, y, -1);
                setBarrierUnlessTestInstanceBlock(helper, enforced, x, y, length);
            }
        }
        for (int z = -1; z <= length; z++) {
            for (int y = -1; y <= height; y++) {
                setBarrierUnlessTestInstanceBlock(helper, enforced, -1, y, z);
                setBarrierUnlessTestInstanceBlock(helper, enforced, width, y, z);
            }
        }
        for (int x = -1; x <= width; x++) {
            for (int z = -1; z <= length; z++) {
                setBarrierUnlessTestInstanceBlock(helper, enforced, x, height, z);
            }
        }
    }

    // The test's own anchor (the in-world Test Instance Block used to run/reset/inspect it) can sit
    // right on this perimeter; overwriting it with a barrier destroys the only way to re-run or
    // inspect the test. Vanilla's own TestInstanceBlockEntity#encaseStructure guards against this the
    // same way - mirror that here.
    private static void setBarrierUnlessTestInstanceBlock(GameTestHelper helper, Set<BlockPos> enforced, int x, int y, int z) {
        var pos = new BlockPos(x, y, z);
        if (!helper.getBlockState(pos).is(Blocks.TEST_INSTANCE_BLOCK)) {
            helper.setBlock(pos, Blocks.BARRIER);
            enforced.add(helper.absolutePos(pos));
        }
    }
}
