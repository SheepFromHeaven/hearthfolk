package com.sheepfromheaven.minenow.villager;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.level.block.Blocks;

import java.util.Optional;

/** Vanilla only claims a job block it can plan a path to, which is never one beyond a villager's 16-block follow range. */
public final class FarJobSiteTests {
    private FarJobSiteTests() {}

    public static void aVillagerWithoutAJobBlockClaimsAFreeOneAcrossTheVillage(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        BlockPos oldLoom = new BlockPos(4, 1, 12);
        helper.setBlock(oldLoom, Blocks.LOOM);
        Villager shepherd = GameTestHelpers.spawnEmployed(helper, oldLoom.east(), oldLoom, VillagerProfession.SHEPHERD, PoiTypes.SHEPHERD);
        GameTestHelpers.giveHome(helper, shepherd, new BlockPos(3, 1, 3));
        BlockPos smoker = new BlockPos(38, 1, 8);
        helper.setBlock(smoker, Blocks.SMOKER);
        Villager neighbour = GameTestHelpers.spawnEmployed(helper, smoker.north(), smoker, VillagerProfession.BUTCHER, PoiTypes.BUTCHER);
        GameTestHelpers.giveHome(helper, neighbour, new BlockPos(40, 1, 3));
        BlockPos bell = new BlockPos(24, 1, 12);
        VillageTests.claimBell(helper, shepherd, bell);
        VillageTests.claimBell(helper, neighbour, bell);
        shepherd.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);
        helper.setBlock(oldLoom, Blocks.AIR);
        BlockPos far = new BlockPos(44, 1, 12);
        helper.setBlock(far, Blocks.LOOM);

        helper.succeedWhen(() -> {
            Optional<BlockPos> site = shepherd.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos);
            helper.assertTrue(site.equals(Optional.of(helper.absolutePos(far))), "The shepherd claims the free loom on the other side of its village, its job site is " + site
                    + ", potential " + shepherd.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE).map(GlobalPos::pos).map(pos -> helper.relativePos(pos).toShortString())
                    + ", at " + helper.relativePos(shepherd.blockPosition()).toShortString() + ", village " + Villages.of(helper.getLevel(), shepherd).isPresent()
                    + ", walk " + shepherd.getBrain().getMemory(MemoryModuleType.WALK_TARGET).map(w -> helper.relativePos(w.getTarget().currentBlockPosition()).toShortString())
                    + ", activity " + shepherd.getBrain().getActiveNonCoreActivity() + ", profession " + shepherd.getVillagerData().profession().getRegisteredName());
        });
    }

    public static void aVillagerClaimsAFreeJobBlockUpTo48BlocksOutsideItsVillageBorder(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        BlockPos oldLoom = new BlockPos(4, 1, 12);
        helper.setBlock(oldLoom, Blocks.LOOM);
        Villager shepherd = GameTestHelpers.spawnEmployed(helper, oldLoom.east(), oldLoom, VillagerProfession.SHEPHERD, PoiTypes.SHEPHERD);
        GameTestHelpers.giveHome(helper, shepherd, new BlockPos(3, 1, 3));
        VillageTests.claimBell(helper, shepherd, new BlockPos(2, 1, 12));
        shepherd.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);
        helper.setBlock(oldLoom, Blocks.AIR);
        BlockPos outside = new BlockPos(56, 1, 12);
        helper.setBlock(outside, Blocks.LOOM);

        helper.succeedWhen(() -> {
            Optional<BlockPos> site = shepherd.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos);
            helper.assertTrue(site.equals(Optional.of(helper.absolutePos(outside))), "The shepherd claims a free loom outside its village border, its job site is " + site);
        });
    }

    public static void aVillagerDoesNotClaimAJobBlockFurtherThan48BlocksFromItsVillageBorder(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        BlockPos oldLoom = new BlockPos(4, 1, 12);
        helper.setBlock(oldLoom, Blocks.LOOM);
        Villager shepherd = GameTestHelpers.spawnEmployed(helper, oldLoom.east(), oldLoom, VillagerProfession.SHEPHERD, PoiTypes.SHEPHERD);
        GameTestHelpers.giveHome(helper, shepherd, new BlockPos(3, 1, 3));
        VillageTests.claimBell(helper, shepherd, new BlockPos(2, 1, 12));
        shepherd.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);
        helper.setBlock(oldLoom, Blocks.AIR);
        helper.setBlock(new BlockPos(70, 1, 12), Blocks.LOOM);

        helper.runAfterDelay(500, () -> {
            helper.assertFalse(shepherd.getBrain().hasMemoryValue(MemoryModuleType.JOB_SITE), "A loom more than 48 blocks past the border is not its business");
            helper.succeed();
        });
    }

    public static void aVillagerWithoutABedClaimsAFreeOneUpTo48BlocksOutsideItsVillageBorder(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        Villager villager = helper.spawn(net.minecraft.world.entity.EntityTypes.VILLAGER, new BlockPos(4, 1, 12));
        VillageTests.claimBell(helper, villager, new BlockPos(2, 1, 12));
        BlockPos bed = new BlockPos(56, 1, 12);
        GameTestHelpers.placeBed(helper, bed);

        helper.succeedWhen(() -> {
            Optional<BlockPos> home = villager.getBrain().getMemory(MemoryModuleType.HOME).map(GlobalPos::pos);
            helper.assertTrue(home.equals(Optional.of(helper.absolutePos(bed))), "The villager claims the free bed outside its village border, its home is " + home);
        });
    }
}
