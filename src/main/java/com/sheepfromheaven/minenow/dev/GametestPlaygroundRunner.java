package com.sheepfromheaven.minenow.dev;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.TestInstanceBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.Optional;

/**
 * Lays out every {@code minenow:*} test instance in a grid the moment the local player logs into the
 * {@link com.sheepfromheaven.minenow.dev.client.GametestPlaygroundBootstrap} world - every login, not
 * just on first creation, since the point of this world is to always show every registered gametest
 * laid out. Unlike {@code /test runmultiple minenow:*}, each test instance block is placed and left in
 * {@link TestInstanceBlockEntity.Status#CLEARED} - the structure is there to walk up to, but nothing
 * ticks until a player right-clicks its test instance block and presses "Run Test" in the vanilla GUI.
 */
public final class GametestPlaygroundRunner {
    /** Generous fixed spacing between grid cells - MineNow's test structures top out around 23 blocks
     * on a side (e.g. room_5x17x23), so this comfortably avoids any overlap without needing to size
     * each cell to its own test's actual structure dimensions. */
    private static final int GRID_CELL_SIZE = 32;

    private GametestPlaygroundRunner() {}

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!Boolean.getBoolean("minenow.gametestPlayground")) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) {
            return;
        }
        var level = (net.minecraft.server.level.ServerLevel) serverPlayer.level();
        var tests = level.registryAccess().lookupOrThrow(Registries.TEST_INSTANCE).listElements()
                .filter(ref -> ref.key().identifier().getNamespace().equals("minenow"))
                .toList();
        if (tests.isEmpty()) {
            return;
        }

        var playerPos = serverPlayer.blockPosition();
        int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, playerPos.getX(), playerPos.getZ());
        var origin = new BlockPos(playerPos.getX(), surfaceY, playerPos.getZ() + 3);

        int testsPerRow = (int) Math.ceil(Math.sqrt(tests.size()));
        for (int i = 0; i < tests.size(); i++) {
            var testKey = tests.get(i).key();
            int col = i % testsPerRow;
            int row = i / testsPerRow;
            var pos = origin.offset(col * GRID_CELL_SIZE, 0, row * GRID_CELL_SIZE);

            level.setBlockAndUpdate(pos, Blocks.TEST_INSTANCE_BLOCK.defaultBlockState());
            if (!(level.getBlockEntity(pos) instanceof TestInstanceBlockEntity blockEntity)) {
                continue;
            }
            Vec3i size = TestInstanceBlockEntity.getStructureSize(level, testKey).orElse(new Vec3i(1, 1, 1));
            blockEntity.set(new TestInstanceBlockEntity.Data(Optional.of(testKey), size, Rotation.NONE, false,
                    TestInstanceBlockEntity.Status.CLEARED, Optional.empty()));
            blockEntity.placeStructure();
        }
    }
}
