package com.sheepfromheaven.minenow.villager;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.level.block.Blocks;

/** Tests that need rain run in the {@code rain} environment and the long arena (the short one has a barrier roof), after the rain has started. */
public final class RainShelterTests {
    private static final BlockPos ROOF_CENTRE = new BlockPos(11, 4, 11);
    private static final BlockPos SMOKER = new BlockPos(5, 1, 12);

    private RainShelterTests() {}

    /**
     * The arena as the open air: the barrier roof the test framework puts one block above it is cleared and the biome (desert
     * in the test world, which never rains) becomes plains, so rain falls where nothing covers it. {@link #restoreSky} puts both back.
     */
    public static void openSky(GameTestHelper helper) {
        var bounds = helper.getBounds();
        for (int x = 0; x < (int) (bounds.maxX - bounds.minX); x++) {
            for (int z = 0; z < (int) (bounds.maxZ - bounds.minZ); z++) helper.setBlock(x, (int) (bounds.maxY - bounds.minY), z, Blocks.AIR);
        }
        fillBiome(helper, "minecraft:plains");
    }

    public static void restoreSky(GameTestHelper helper) {
        var bounds = helper.getBounds();
        for (int x = 0; x < (int) (bounds.maxX - bounds.minX); x++) {
            for (int z = 0; z < (int) (bounds.maxZ - bounds.minZ); z++) helper.setBlock(x, (int) (bounds.maxY - bounds.minY), z, Blocks.BARRIER);
        }
        fillBiome(helper, "minecraft:desert");
    }

    private static void fillBiome(GameTestHelper helper, String biome) {
        var bounds = helper.getBounds();
        var level = helper.getLevel();
        var source = level.getServer().createCommandSourceStack().withLevel(level);
        level.getServer().getCommands().performPrefixedCommand(source, "fillbiome " + (int) bounds.minX + " " + (int) bounds.minY + " " + (int) bounds.minZ + " "
                + ((int) bounds.maxX - 1) + " " + ((int) bounds.maxY - 1) + " " + ((int) bounds.maxZ - 1) + " " + biome);
    }

    /** A 3x3 slab roof four blocks up, centred on {@code centre}'s column. */
    public static void roof(GameTestHelper helper, BlockPos centre) {
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) helper.setBlock(centre.offset(x, 0, z), Blocks.STONE_SLAB);
        }
    }

    /** Runs {@code then} once the rain is falling: it fades in over about twenty ticks, and behaviour that starts before it would not feel it. */
    public static void whenItRains(GameTestHelper helper, Runnable then) {
        boolean[] started = {false};
        helper.onEachTick(() -> {
            if (started[0] || !helper.getLevel().isRaining()) return;
            started[0] = true;
            then.run();
        });
    }

    public static void rainWetsOpenGroundButNotGroundUnderARoof(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        openSky(helper);
        roof(helper, ROOF_CENTRE);
        whenItRains(helper, () -> {
            var level = helper.getLevel();
            helper.assertTrue(RainShelter.wet(level, helper.absolutePos(new BlockPos(40, 1, 10))), "Open ground is wet");
            helper.assertTrue(!RainShelter.wet(level, helper.absolutePos(new BlockPos(11, 1, 11))), "Ground under a roof is dry");
            helper.assertTrue(RainShelter.nearest(level, helper.absolutePos(new BlockPos(16, 1, 11)), RainShelter.QUICK_TRIP).isPresent(), "A roof four blocks away is a shelter");
            helper.assertTrue(RainShelter.nearest(level, helper.absolutePos(new BlockPos(40, 1, 10)), RainShelter.QUICK_TRIP).isEmpty(), "Open ground far from any roof has none");
            helper.assertTrue(RainShelter.allows(level, helper.absolutePos(new BlockPos(16, 1, 11))), "Work a short trip from a roof is allowed in the rain");
            helper.assertTrue(RainShelter.allows(level, helper.absolutePos(new BlockPos(11, 1, 11))), "...and under it");
            helper.assertTrue(!RainShelter.allows(level, helper.absolutePos(new BlockPos(40, 1, 10))), "...but not far from any roof");
            restoreSky(helper);
            helper.succeed();
        });
    }

    public static void aWorkerStandingInTheRainWalksUnderANearbyRoof(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        openSky(helper);
        helper.setBlock(SMOKER, Blocks.SMOKER);
        roof(helper, SMOKER.offset(4, 3, 1));
        whenItRains(helper, () -> {
            Villager butcher = GameTestHelpers.spawnEmployed(helper, SMOKER.west(), SMOKER, VillagerProfession.BUTCHER, PoiTypes.BUTCHER);
            helper.succeedWhen(() -> {
                helper.assertTrue(!RainShelter.wet(helper.getLevel(), butcher.blockPosition()),
                        "A villager at work in the rain with a roof nearby should stand under it, at " + helper.relativePos(butcher.blockPosition()).toShortString());
                restoreSky(helper);
            });
        });
    }
}
