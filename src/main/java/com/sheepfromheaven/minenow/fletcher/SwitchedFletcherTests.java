package com.sheepfromheaven.minenow.fletcher;

import com.sheepfromheaven.minenow.MineNowConfig.Profession;
import com.sheepfromheaven.minenow.gametest.SwitchedTrades;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** A fletcher switched off crafts nothing, shoots no rounds and trades the vanilla way. */
public final class SwitchedFletcherTests {
    private SwitchedFletcherTests() {}

    public static void aSwitchedOffFletcherCraftsNothing(GameTestHelper helper) {
        Villager fletcher = FletcherTests.spawnFletcher(helper);
        fletcher.getInventory().addItem(new ItemStack(Items.OAK_PLANKS, 8));
        fletcher.getInventory().addItem(new ItemStack(Items.FLINT, 4));
        fletcher.getInventory().addItem(new ItemStack(Items.FEATHER, 4));
        helper.runAfterDelay(400, () -> {
            helper.assertTrue(fletcher.getInventory().countItem(Items.STICK) == 0 && fletcher.getInventory().countItem(Items.ARROW) == 0,
                    "Expected no sticks or arrows crafted");
            helper.assertTrue(fletcher.getInventory().countItem(Items.OAK_PLANKS) == 8 && fletcher.getInventory().countItem(Items.FLINT) == 4
                    && fletcher.getInventory().countItem(Items.FEATHER) == 4, "Expected the materials untouched");
            helper.succeed();
        });
    }

    public static void aSwitchedOffFletcherShootsNoRounds(GameTestHelper helper) {
        Villager fletcher = FletcherTests.spawnFletcher(helper);
        fletcher.setVillagerData(fletcher.getVillagerData().withLevel(5));
        fletcher.getInventory().addItem(new ItemStack(Items.BOW));
        fletcher.getInventory().addItem(new ItemStack(Items.ARROW, 8));
        BlockPos target = new BlockPos(17, 2, 12);
        helper.setBlock(target.below(), Blocks.STONE);
        helper.setBlock(target, Blocks.TARGET);
        boolean[] practised = {false};
        helper.onEachTick(() -> practised[0] |= fletcher.getBrain().getRunningBehaviors().stream().anyMatch(PracticeArchery.class::isInstance));
        helper.runAfterDelay(600, () -> {
            helper.assertTrue(!practised[0], "Expected PracticeArchery to stay out");
            helper.assertTrue(helper.getBlockState(target).getValue(BlockStateProperties.POWER) == 0, "Expected the target unhit");
            helper.assertTrue(fletcher.getInventory().countItem(Items.ARROW) == 8, "Expected all its arrows kept");
            helper.succeed();
        });
    }

    public static void aSwitchedOffFletcherShowsVanillaTrades(GameTestHelper helper) {
        SwitchedTrades.assertVanillaTrades(helper, Profession.FLETCHER, FletcherTests.spawnFletcher(helper));
        helper.succeed();
    }
}
