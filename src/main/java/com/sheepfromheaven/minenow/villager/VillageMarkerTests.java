package com.sheepfromheaven.minenow.villager;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** A banner on a hay bale links the places within 32 blocks of it, so a bed too far from the bell still joins the village. */
public final class VillageMarkerTests {
    private static final BlockPos BELL = new BlockPos(14, 1, 12);
    private static final BlockPos MARKER = new BlockPos(38, 1, 12);

    private VillageMarkerTests() {}

    private static Villager[] farBed(GameTestHelper helper, Block base, Block top) {
        GameTestHelpers.fence(helper);
        Villager near = GameTestHelpers.spawnWithHome(helper, new BlockPos(8, 1, 8), new BlockPos(6, 1, 4));
        Villager far = GameTestHelpers.spawnWithHome(helper, new BlockPos(66, 1, 8), new BlockPos(68, 1, 4));
        far.setNoAi(true);
        VillageTests.claimBell(helper, near, BELL);
        helper.setBlock(MARKER, base);
        helper.setBlock(MARKER.above(), top);
        return new Villager[]{near, far};
    }

    public static void aBannerOnAHayBaleLinksABedTooFarFromTheBell(GameTestHelper helper) {
        Villager[] pair = farBed(helper, Blocks.HAY_BLOCK, Blocks.BANNER.white());
        helper.succeedWhen(() -> {
            var home = Villages.of(helper.getLevel(), pair[0]);
            helper.assertTrue(home.isPresent(), "The bell's villager has a village");
            helper.assertTrue(Villages.of(helper.getLevel(), pair[1]).equals(home), "The far bed joins it through the banner");
        });
    }

    public static void aHayBaleWithoutABannerLinksNothing(GameTestHelper helper) {
        Villager[] pair = farBed(helper, Blocks.HAY_BLOCK, Blocks.AIR);
        helper.runAfterDelay(150, () -> {
            helper.assertTrue(Villages.of(helper.getLevel(), pair[0]).isPresent(), "The bell's villager has a village");
            helper.assertTrue(Villages.of(helper.getLevel(), pair[1]).isEmpty(), "A bare hay bale links nothing");
            helper.succeed();
        });
    }

    public static void aBannerOnAnotherBlockLinksNothing(GameTestHelper helper) {
        Villager[] pair = farBed(helper, Blocks.STONE, Blocks.BANNER.white());
        helper.runAfterDelay(150, () -> {
            helper.assertTrue(Villages.of(helper.getLevel(), pair[0]).isPresent(), "The bell's villager has a village");
            helper.assertTrue(Villages.of(helper.getLevel(), pair[1]).isEmpty(), "A banner on stone is just decoration");
            helper.succeed();
        });
    }
}
