package com.sheepfromheaven.minenow.dev;

import com.sheepfromheaven.minenow.MineNow;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.TestInstanceBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.Optional;

/**
 * Dev tooling for the {@code gametestPlayground} run ({@code -Dminenow.gametestPlayground=true}): on every login, lays
 * out every {@code minenow:*} test instance in a grid near the player, idle, so each can be started from its test
 * instance block and watched.
 */
public final class GametestPlayground {
    public static final String PROPERTY = "minenow.gametestPlayground";
    private static final int GRID_CELL_SIZE = 32;

    private GametestPlayground() {}

    public static boolean enabled() {
        return Boolean.getBoolean(PROPERTY);
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!enabled() || !(event.getEntity() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return;
        var tests = level.registryAccess().lookupOrThrow(Registries.TEST_INSTANCE).listElements()
                .filter(test -> test.key().identifier().getNamespace().equals(MineNow.MOD_ID))
                .toList();
        if (tests.isEmpty()) return;

        BlockPos at = player.blockPosition();
        BlockPos origin = new BlockPos(at.getX(), level.getHeight(Heightmap.Types.WORLD_SURFACE, at.getX(), at.getZ()), at.getZ() + 3);
        int perRow = (int) Math.ceil(Math.sqrt(tests.size()));
        for (int i = 0; i < tests.size(); i++) {
            var key = tests.get(i).key();
            BlockPos pos = origin.offset((i % perRow) * GRID_CELL_SIZE, 0, (i / perRow) * GRID_CELL_SIZE);
            level.setBlockAndUpdate(pos, Blocks.TEST_INSTANCE_BLOCK.defaultBlockState());
            if (!(level.getBlockEntity(pos) instanceof TestInstanceBlockEntity block)) continue;
            Vec3i size = TestInstanceBlockEntity.getStructureSize(level, key).orElse(new Vec3i(1, 1, 1));
            block.set(new TestInstanceBlockEntity.Data(Optional.of(key), size, Rotation.NONE, false,
                    TestInstanceBlockEntity.Status.CLEARED, Optional.empty()));
            block.placeStructure();
        }
    }
}
