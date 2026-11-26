package com.sheepfromheaven.minenow.villager;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.level.block.Blocks;

/** A novice that has been without a job site for a day gives up its trade even with work experience, which vanilla's reset would keep. */
public final class JobLossTests {
    private static final BlockPos SMOKER = new BlockPos(6, 1, 6);

    private JobLossTests() {}

    private static Villager workerWithoutAJobSite(GameTestHelper helper, long lostAt) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(SMOKER, Blocks.SMOKER);
        Villager villager = GameTestHelpers.spawnEmployed(helper, SMOKER.east(), SMOKER, VillagerProfession.BUTCHER, PoiTypes.BUTCHER);
        villager.setVillagerXp(10);
        villager.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);
        helper.setBlock(SMOKER, Blocks.AIR);
        villager.setData(ModVillagers.NO_JOB_SINCE, helper.getLevel().getGameTime() - lostAt);
        return villager;
    }

    public static void aNoviceWithoutAJobSiteForADayLosesItsTrade(GameTestHelper helper) {
        Villager villager = workerWithoutAJobSite(helper, JobLoss.PATIENCE + 100);
        helper.succeedWhen(() -> helper.assertTrue(villager.getVillagerData().profession().is(VillagerProfession.NONE),
                "It has had no job site for over a day, so it is unemployed again, is " + villager.getVillagerData().profession()));
    }

    public static void aNoviceThatLostItsJobSiteJustNowKeepsItsTrade(GameTestHelper helper) {
        Villager villager = workerWithoutAJobSite(helper, 100);
        helper.runAfterDelay(100, () -> {
            helper.assertTrue(villager.getVillagerData().profession().is(VillagerProfession.BUTCHER), "A short absence keeps the trade");
            helper.succeed();
        });
    }

    public static void aJourneymanKeepsItsTradeWithoutAJobSite(GameTestHelper helper) {
        Villager villager = workerWithoutAJobSite(helper, JobLoss.PATIENCE + 100);
        villager.setVillagerData(villager.getVillagerData().withLevel(3));
        helper.runAfterDelay(100, () -> {
            helper.assertTrue(villager.getVillagerData().profession().is(VillagerProfession.BUTCHER), "A villager above novice keeps its trade");
            helper.succeed();
        });
    }

    public static void aJobSiteInAnUnloadedChunkWhoseBlockIsGoneIsGivenUp(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(SMOKER, Blocks.SMOKER);
        Villager villager = GameTestHelpers.spawnEmployed(helper, SMOKER.east(), SMOKER, VillagerProfession.BUTCHER, PoiTypes.BUTCHER);
        BlockPos far = helper.absolutePos(SMOKER).offset(20000, 0, 0);
        villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, net.minecraft.core.GlobalPos.of(helper.getLevel().dimension(), far));
        helper.setBlock(SMOKER, Blocks.AIR);

        helper.succeedWhen(() -> helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty(),
                "A job site whose block is gone is dropped even when its chunk isn't loaded, so the trade can lapse"));
    }

    public static void theReportSaysWhenAJoblessNoviceLosesItsTrade(GameTestHelper helper) {
        Villager villager = workerWithoutAJobSite(helper, JobLoss.PATIENCE / 2);
        helper.runAfterDelay(5, () -> {
            String report = String.join("\n", IdleReport.describe(helper.getLevel(), villager).stream().map(line -> line.getString()).toList());
            helper.assertTrue(report.contains("Job site: none") && report.contains("trade lapses in"), "The report says there is no job site and when the trade lapses, got " + report);
            helper.succeed();
        });
    }

    public static void aBrokenJobBlockFarAwayIsForgottenEvenAtNight(GameTestHelper helper) {
        helper.setTime(NightDoorTests.NIGHT);
        GameTestHelpers.fence(helper);
        helper.setBlock(SMOKER, Blocks.SMOKER);
        Villager villager = GameTestHelpers.spawnEmployed(helper, SMOKER.east(), SMOKER, VillagerProfession.BUTCHER, PoiTypes.BUTCHER);
        BlockPos far = helper.absolutePos(SMOKER).offset(20000, 0, 0);
        villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, net.minecraft.core.GlobalPos.of(helper.getLevel().dimension(), far));
        helper.setBlock(SMOKER, Blocks.AIR);

        helper.succeedWhen(() -> helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty(),
                "The job site's block is gone, so the villager forgets it whether or not it is working or the chunk is loaded"));
    }

    public static void theReportNamesTheBlockAtTheJobSite(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(SMOKER, Blocks.SMOKER);
        Villager villager = GameTestHelpers.spawnEmployed(helper, SMOKER.east(), SMOKER, VillagerProfession.BUTCHER, PoiTypes.BUTCHER);

        helper.runAfterDelay(5, () -> {
            String report = JobLoss.describe(helper.getLevel(), villager);
            helper.assertTrue(report.contains("Smoker") && report.contains("butcher"), "The job line names the block and the point of interest there, got " + report);
            helper.succeed();
        });
    }

    public static void aJobSiteNowHoldingAnotherKindOfBlockIsForgotten(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        BlockPos far = new BlockPos(64, 1, 12);
        helper.setBlock(far, Blocks.CHEST);
        Villager villager = helper.spawn(net.minecraft.world.entity.EntityTypes.VILLAGER, new BlockPos(6, 1, 6));
        villager.setVillagerData(villager.getVillagerData().withProfession(helper.getLevel().registryAccess(), VillagerProfession.BUTCHER));
        villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, net.minecraft.core.GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(far)));
        villager.setNoAi(true);

        helper.succeedWhen(() -> helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty(),
                "A chest is a point of interest but no butcher's job site, and vanilla only checks within 16 blocks"));
    }

    private static Villager workerWhoseJobBlockWasBroken(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(SMOKER, Blocks.SMOKER);
        Villager villager = GameTestHelpers.spawnEmployed(helper, SMOKER.east(), SMOKER, VillagerProfession.BUTCHER, PoiTypes.BUTCHER);
        helper.runAfterDelay(45, () -> helper.setBlock(SMOKER, Blocks.AIR));
        return villager;
    }

    public static void aJobBlockPlacedNearTheOldOneGoesToTheSameVillager(GameTestHelper helper) {
        Villager villager = workerWhoseJobBlockWasBroken(helper);
        BlockPos moved = SMOKER.offset(0, 0, 4);
        helper.runAfterDelay(200, () -> helper.setBlock(moved, Blocks.SMOKER));
        helper.runAfterDelay(400, () -> {
            helper.assertTrue(villager.getVillagerData().profession().is(VillagerProfession.BUTCHER), "A moved workstation keeps the trade, is " + villager.getVillagerData().profession());
            helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(site -> site.pos().equals(helper.absolutePos(moved))).orElse(false),
                    "The villager takes the moved block as its job site, has " + villager.getBrain().getMemory(MemoryModuleType.JOB_SITE));
            helper.succeed();
        });
    }
}
