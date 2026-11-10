package com.sheepfromheaven.minenow.villager;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.level.block.Blocks;

public final class JobSiteTests {
    private static final BlockPos GRINDSTONE = new BlockPos(4, 1, 4);

    private JobSiteTests() {}

    public static void aVillagerKeepsItsJobSiteAfterAWalkItCouldNotFinish(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(GRINDSTONE, Blocks.GRINDSTONE);
        Villager smith = GameTestHelpers.spawnEmployed(helper, new BlockPos(18, 1, 18), GRINDSTONE, VillagerProfession.WEAPONSMITH, PoiTypes.WEAPONSMITH);
        long[] lost = {-1};
        double[] closest = {Double.MAX_VALUE};
        helper.onEachTick(() -> {
            if (lost[0] < 0 && !smith.getBrain().hasMemoryValue(MemoryModuleType.JOB_SITE)) lost[0] = helper.getTick();
            if (helper.getTick() > 5) closest[0] = Math.min(closest[0], smith.position().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(GRINDSTONE))));
            if (helper.getTick() == 5) {
                smith.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                smith.getBrain().setMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, helper.getLevel().getGameTime() - 3000);
            }
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(lost[0] < 0, "A villager that failed to reach a seller far away should keep its job site, lost it at tick " + lost[0]);
            helper.assertTrue(closest[0] < 12.0, "...and walk back to it, came within " + closest[0]);
        });
    }

    public static void aVillagerKeepsAJobSiteItCannotReach(GameTestHelper helper) {
        Villager smith = walledOff(helper);

        helper.runAfterDelay(2000, () -> {
            helper.assertTrue(smith.getBrain().hasMemoryValue(MemoryModuleType.JOB_SITE), "A villager walled off from its job site keeps it, however long it can't get there");
            helper.succeed();
        });
    }

    public static void aVillagerLosesAJobSiteThatWasBroken(GameTestHelper helper) {
        Villager smith = walledOff(helper);

        helper.runAfterDelay(40, () -> helper.setBlock(GRINDSTONE, Blocks.AIR));
        helper.succeedWhen(() -> helper.assertFalse(smith.getBrain().hasMemoryValue(MemoryModuleType.JOB_SITE),
                "A villager gives up a job site that's gone, even from too far off for vanilla to notice"));
    }

    /** A weaponsmith 22 blocks from its grindstone, more than 20, walled off from it. */
    private static Villager walledOff(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        for (int i = 1; i <= 18; i++) {
            for (int y = 1; y <= 5; y++) {
                helper.setBlock(new BlockPos(i, y, 18), Blocks.BARRIER);
                helper.setBlock(new BlockPos(18, y, i), Blocks.BARRIER);
            }
        }
        helper.setBlock(GRINDSTONE, Blocks.GRINDSTONE);
        return GameTestHelpers.spawnEmployed(helper, new BlockPos(20, 1, 20), GRINDSTONE, VillagerProfession.WEAPONSMITH, PoiTypes.WEAPONSMITH);
    }
}
