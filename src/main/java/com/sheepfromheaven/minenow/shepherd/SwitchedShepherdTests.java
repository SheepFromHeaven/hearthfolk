package com.sheepfromheaven.minenow.shepherd;

import com.sheepfromheaven.minenow.MineNowConfig;
import com.sheepfromheaven.minenow.MineNowConfig.Profession;
import com.sheepfromheaven.minenow.villager.OpenFenceGates;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.FenceGateBlock;

/** A shepherd switched off shears no sheep and opens no gate; run in the {@code profession_shepherd} environment. */
public final class SwitchedShepherdTests {
    private SwitchedShepherdTests() {}

    public static void aSwitchedOffShepherdShearsNoSheepByHerLoomAndOpensNoGate(GameTestHelper helper) {
        helper.assertTrue(!MineNowConfig.professionEnabled(Profession.SHEPHERD), "The shepherd's environment switches the shepherd off");
        Villager shepherd = ShepherdTests.spawnShepherd(helper);
        shepherd.setVillagerData(shepherd.getVillagerData().withLevel(5));
        shepherd.getInventory().addItem(new ItemStack(Items.SHEARS));
        ShepherdTests.buildPen(helper, 9);
        BlockPos gate = new BlockPos(9, 1, 12);
        Sheep sheep = ShepherdTests.sheep(helper, 12, 11, DyeColor.WHITE);
        helper.assertTrue(!OpenFenceGates.canOpen(shepherd), "A switched-off shepherd only opens doors, like any vanilla villager");
        boolean[] opened = {false};
        helper.onEachTick(() -> opened[0] |= helper.getBlockState(gate).getValue(FenceGateBlock.OPEN));
        helper.runAfterDelay(900, () -> {
            helper.assertTrue(!sheep.isSheared(), "A switched-off shepherd shears no sheep");
            helper.assertTrue(!opened[0], "...and opens no fence gate");
            helper.succeed();
        });
    }

    public static void aSwitchedOffShepherdShowsVanillaTradesThatCostEmeralds(GameTestHelper helper) {
        helper.assertTrue(!MineNowConfig.professionEnabled(Profession.SHEPHERD), "The shepherd's environment switches the shepherd off");
        Villager shepherd = ShepherdTests.spawnShepherd(helper);
        var offers = com.sheepfromheaven.minenow.villager.ProfessionTrades.currentOffers(helper.getLevel(), shepherd);
        helper.assertTrue(offers.stream().anyMatch(offer -> offer.getBaseCostA().is(Items.EMERALD) || offer.getResult().is(Items.EMERALD)), "Expected a vanilla trade in emeralds, got " + offers.size() + " offers");
        helper.assertTrue(offers.stream().noneMatch(offer -> offer.getBaseCostA().is(Items.SHEARS)), "Hearthfolk's buying of shears is gone");
        helper.succeed();
    }
}
