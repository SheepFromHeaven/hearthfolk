package com.sheepfromheaven.minenow.villager;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.npc.villager.Villager;

/** A birth takes a free bed anywhere in the village, not only within 48 blocks of the parent. */
public final class VillageBedsTests {
    private VillageBedsTests() {}

    public static void aBabyTakesAFreeBedOfTheVillage(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        BlockPos bell = new BlockPos(36, 1, 12);
        Villager a = GameTestHelpers.spawnWithHome(helper, new BlockPos(6, 1, 8), new BlockPos(4, 1, 4));
        Villager b = GameTestHelpers.spawnWithHome(helper, new BlockPos(58, 1, 8), new BlockPos(60, 1, 4));
        GameTestHelpers.placeBed(helper, new BlockPos(52, 1, 12));
        GameTestHelpers.placeBed(helper, new BlockPos(67, 1, 12));
        VillageTests.claimBell(helper, a, bell);
        VillageTests.claimBell(helper, b, bell);
        BlockPos far = helper.absolutePos(new BlockPos(67, 1, 12));

        helper.runAfterDelay(110, () -> {
            java.util.Set<BlockPos> taken = new java.util.HashSet<>();
            for (int i = 0; i < 40; i++) {
                var bed = VillageBeds.take(helper.getLevel(), a);
                if (bed.isEmpty()) break;
                helper.assertTrue(taken.add(bed.get()), "A taken bed is not given twice");
            }
            helper.assertTrue(taken.contains(far), "The free bed past 48 blocks, in the village, is taken too, got " + taken + " without " + far);
            helper.succeed();
        });
    }
}
