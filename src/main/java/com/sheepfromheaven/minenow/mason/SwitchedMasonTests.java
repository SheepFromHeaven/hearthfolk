package com.sheepfromheaven.minenow.mason;

import com.sheepfromheaven.minenow.MineNowConfig;
import com.sheepfromheaven.minenow.MineNowConfig.Profession;
import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import com.sheepfromheaven.minenow.gametest.VanillaOffers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** A mason switched off paves nothing, powered or paid, and trades as vanilla's. */
public final class SwitchedMasonTests {
    private static final BlockPos STONECUTTER = new BlockPos(5, 1, 12);

    private SwitchedMasonTests() {}

    public static void aSwitchedOffMasonPavesNoPathWithASignal(GameTestHelper helper) {
        helper.assertTrue(!MineNowConfig.professionEnabled(Profession.MASON), "The mason's environment switches the mason off");
        Villager mason = MasonTests.spawnMason(helper, 2);
        mason.getInventory().addItem(new ItemStack(Items.COBBLESTONE, 32));
        mason.getInventory().addItem(new ItemStack(Items.GRAVEL, 16));
        for (int x = 8; x < 16; x++) helper.setBlock(new BlockPos(x, 0, 14), Blocks.DIRT_PATH);
        helper.setBlock(STONECUTTER.north(), Blocks.REDSTONE_BLOCK);

        helper.runAfterDelay(600, () -> {
            for (int x = 8; x < 16; x++) helper.assertTrue(helper.getBlockState(new BlockPos(x, 0, 14)).is(Blocks.DIRT_PATH), "A switched-off mason paves nothing, at x " + x);
            helper.succeed();
        });
    }

    public static void aSwitchedOffMasonRefusesAPavingOrder(GameTestHelper helper) {
        Villager mason = MasonTests.spawnMason(helper, 2);
        mason.getInventory().addItem(new ItemStack(Items.COBBLESTONE, 64));
        for (int x = 6; x <= 21; x++) helper.setBlock(new BlockPos(x, 0, 14), Blocks.DIRT_PATH);
        helper.setBlock(STONECUTTER.north(), Blocks.REDSTONE_BLOCK);
        Villager neighbour = GameTestHelpers.spawnWithHome(helper, new BlockPos(19, 1, 17), new BlockPos(19, 1, 16));
        neighbour.getInventory().addItem(new ItemStack(Items.EMERALD, 20));
        neighbour.getInventory().addItem(new ItemStack(Items.BREAD, 12));

        helper.runAfterDelay(900, () -> {
            helper.assertTrue(!PaveRoads.takesWork(helper.getLevel(), mason), "A switched-off mason takes no paving work");
            helper.assertTrue(PaveRoads.quote(helper.getLevel(), neighbour, mason, 20).isEmpty(), "...and quotes no order");
            helper.assertTrue(mason.getInventory().countItem(Items.EMERALD) == 0, "...so it takes no money for paving");
            helper.assertTrue(helper.getBlockState(new BlockPos(10, 0, 14)).is(Blocks.DIRT_PATH), "...and paves nothing");
            helper.succeed();
        });
    }

    public static void aSwitchedOffMasonOffersOnlyVanillaTrades(GameTestHelper helper) {
        Villager mason = MasonTests.spawnMason(helper, 1);
        VanillaOffers.assertVanilla(helper, mason);
        helper.succeed();
    }
}
