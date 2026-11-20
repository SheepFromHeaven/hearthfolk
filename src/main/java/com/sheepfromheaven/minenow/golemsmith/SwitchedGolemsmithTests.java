package com.sheepfromheaven.minenow.golemsmith;

import com.sheepfromheaven.minenow.MineNowConfig;
import com.sheepfromheaven.minenow.MineNowConfig.Profession;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

public final class SwitchedGolemsmithTests {
    private SwitchedGolemsmithTests() {}

    /** A journeyman golemsmith switched off with a started golem loses its job, forgets the site and adds no block to it. */
    public static void aSwitchedOffGolemsmithLosesItsJobAndBuildsNothing(GameTestHelper helper) {
        helper.assertTrue(!MineNowConfig.professionEnabled(Profession.GOLEMSMITH), "The environment switches golemsmiths off");
        Villager smith = GolemsmithTests.spawnGolemsmith(helper, 3);
        smith.setVillagerXp(100);
        smith.getInventory().addItem(new ItemStack(Items.IRON_BLOCK, 2));
        BlockPos base = new BlockPos(14, 1, 16);
        helper.setBlock(base, Blocks.IRON_BLOCK);
        smith.setData(Golemsmith.SITE, new GolemSite(helper.absolutePos(base), Direction.NORTH, false));

        helper.runAfterDelay(400, () -> {
            helper.assertTrue(smith.getVillagerData().profession().is(VillagerProfession.NONE), "Expected the golemsmith to be unemployed");
            helper.assertTrue(smith.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty(), "Expected its job site to be forgotten");
            helper.assertTrue(!smith.hasData(Golemsmith.SITE), "Expected its golem site to be forgotten");
            helper.assertTrue(smith.getVillagerData().level() == 3 && smith.getVillagerXp() == 100, "Expected its experience to stay");
            helper.assertTrue(smith.getInventory().countItem(Items.IRON_BLOCK) == 2, "Expected its iron to stay in its pockets");
            helper.assertTrue(GolemsmithTests.placed(helper, Blocks.IRON_BLOCK) == 1, "Expected the half-built golem to stay as it was, with no block added");
            helper.succeed();
        });
    }
}
