package com.sheepfromheaven.minenow.smelter;

import com.sheepfromheaven.minenow.MineNowConfig;
import com.sheepfromheaven.minenow.MineNowConfig.Profession;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

public final class SwitchedSmelterTests {
    private SwitchedSmelterTests() {}

    /** Off, a villager at a blast furnace is an armorer and smelts nothing; on again, the block makes it a smelter. */
    public static void aSwitchedOffSmelterIsAnArmorerAndSmeltsNothing(GameTestHelper helper) {
        helper.assertTrue(!MineNowConfig.professionEnabled(Profession.SMELTER), "The environment switches smelters off");
        Villager smelter = SmelterTests.spawnSmelter(helper);
        smelter.getInventory().addItem(new ItemStack(Items.RAW_IRON, 3));
        smelter.getInventory().addItem(new ItemStack(Items.COAL, 2));
        boolean[] switchedBack = {false};

        helper.succeedWhen(() -> {
            if (!switchedBack[0]) {
                helper.assertTrue(smelter.getVillagerData().profession().is(VillagerProfession.ARMORER), "Expected the smelter to become an armorer");
                helper.assertTrue(helper.getBlockEntity(SmelterTests.BLAST_FURNACE, AbstractFurnaceBlockEntity.class).getItem(0).isEmpty(), "Expected nothing in its blast furnace");
                helper.assertTrue(smelter.getInventory().countItem(Items.RAW_IRON) == 3 && smelter.getInventory().countItem(Items.COAL) == 2, "Expected its items to stay");
                if (helper.getTick() < 300) throw helper.assertionException("Waiting to see it smelt nothing");
                switchedBack[0] = true;
                MineNowConfig.set(Profession.SMELTER, true);
            }
            helper.assertTrue(smelter.getVillagerData().profession().is(Smelter.PROFESSION), "Expected a smelter again once switched back on");
        });
    }
}
