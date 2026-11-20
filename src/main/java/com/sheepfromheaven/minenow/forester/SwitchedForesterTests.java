package com.sheepfromheaven.minenow.forester;

import com.sheepfromheaven.minenow.MineNowConfig;
import com.sheepfromheaven.minenow.MineNowConfig.Profession;
import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

public final class SwitchedForesterTests {
    private static final BlockPos LOG = new BlockPos(10, 1, 12);
    private static final BlockPos POT = LOG.above();

    private SwitchedForesterTests() {}

    private static Villager forester(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(LOG, Blocks.OAK_LOG);
        helper.setBlock(POT, Forester.nursery(NurseryTree.OAK));
        Villager forester = GameTestHelpers.spawnEmployed(helper, new BlockPos(13, 1, 12), POT, Forester.PROFESSION, Forester.POI);
        forester.setVillagerData(forester.getVillagerData().withLevel(3));
        forester.setVillagerXp(100);
        return forester;
    }

    /** A level 3 forester switched off becomes unemployed and keeps its pockets, experience and stumps to replant. */
    public static void aSwitchedOffForesterLosesItsJobAndKeepsItsItems(GameTestHelper helper) {
        helper.assertTrue(!MineNowConfig.professionEnabled(Profession.FORESTER), "The environment switches foresters off");
        Villager forester = forester(helper);
        forester.getInventory().addItem(new ItemStack(Items.STONE_AXE));
        forester.getInventory().addItem(new ItemStack(Items.EMERALD, 5));
        BlockPos stump = helper.absolutePos(new BlockPos(15, 1, 15));
        forester.setData(Forester.REPLANT, List.of(stump));

        helper.succeedWhen(() -> {
            helper.assertTrue(forester.getVillagerData().profession().is(VillagerProfession.NONE), "Expected the forester to be unemployed");
            helper.assertTrue(forester.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty(), "Expected its job site to be forgotten");
            helper.assertTrue(forester.getVillagerData().level() == 3 && forester.getVillagerXp() == 100, "Expected its experience to stay");
            helper.assertTrue(forester.getInventory().countItem(Items.EMERALD) == 5 && forester.getInventory().countItem(Items.STONE_AXE) == 1, "Expected its items to stay");
            helper.assertTrue(forester.getData(Forester.REPLANT).equals(List.of(stump)), "Expected its stumps to replant to stay");
        });
    }

    /** Beside a nursery, off, nobody takes a job. */
    public static void aSwitchedOffForesterTakesNoNursery(GameTestHelper helper) {
        helper.assertTrue(!MineNowConfig.professionEnabled(Profession.FORESTER), "The environment switches foresters off");
        Villager forester = forester(helper);
        Villager other = helper.spawn(EntityTypes.VILLAGER, new BlockPos(11, 1, 14));

        helper.runAfterDelay(500, () -> {
            helper.assertTrue(forester.getVillagerData().profession().is(VillagerProfession.NONE), "Expected the forester to stay unemployed while off");
            helper.assertTrue(other.getVillagerData().profession().is(VillagerProfession.NONE), "Expected no villager to take the nursery while off");
            helper.assertTrue(forester.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty() && other.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty(),
                    "Expected nobody to hold the nursery");
            helper.succeed();
        });
    }

    /** Switched on, an unemployed villager with experience takes the nursery and comes back at its old level. */
    public static void anUnemployedVillagerWithExperienceTakesTheNurseryAtItsOldLevel(GameTestHelper helper) {
        helper.assertTrue(MineNowConfig.professionEnabled(Profession.FORESTER), "Foresters are on in a plain test");
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(LOG, Blocks.OAK_LOG);
        helper.setBlock(POT, Forester.nursery(NurseryTree.OAK));
        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(13, 1, 12));
        villager.setVillagerData(villager.getVillagerData().withLevel(3));
        villager.setVillagerXp(100);

        helper.succeedWhen(() -> {
            helper.assertTrue(villager.getVillagerData().profession().is(Forester.PROFESSION), "Expected it to take the nursery");
            helper.assertTrue(villager.getVillagerData().level() == 3, "Expected its old level back");
        });
    }
}
