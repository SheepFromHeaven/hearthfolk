package com.sheepfromheaven.minenow.toolsmith;

import com.sheepfromheaven.minenow.MineNowConfig.Profession;
import com.sheepfromheaven.minenow.gametest.SwitchedTrades;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** A toolsmith switched off crafts nothing and trades the vanilla way. */
public final class SwitchedToolsmithTests {
    private SwitchedToolsmithTests() {}

    public static void aSwitchedOffToolsmithCraftsNothing(GameTestHelper helper) {
        Villager toolsmith = ToolsmithTests.spawnToolsmith(helper);
        toolsmith.getInventory().addItem(new ItemStack(Items.COBBLESTONE, 4));
        toolsmith.getInventory().addItem(new ItemStack(Items.STICK, 4));
        helper.runAfterDelay(400, () -> {
            helper.assertTrue(toolsmith.getInventory().countItem(Items.STONE_HOE) == 0 && toolsmith.getInventory().countItem(Items.STONE_AXE) == 0
                    && toolsmith.getInventory().countItem(Items.STONE_PICKAXE) == 0, "Expected no tool crafted");
            helper.assertTrue(toolsmith.getInventory().countItem(Items.COBBLESTONE) == 4 && toolsmith.getInventory().countItem(Items.STICK) == 4,
                    "Expected the materials untouched");
            helper.succeed();
        });
    }

    public static void aSwitchedOffToolsmithShowsVanillaTrades(GameTestHelper helper) {
        SwitchedTrades.assertVanillaTrades(helper, Profession.TOOLSMITH, ToolsmithTests.spawnToolsmith(helper));
        helper.succeed();
    }
}
