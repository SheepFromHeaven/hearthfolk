package com.sheepfromheaven.minenow.butcher;

import com.sheepfromheaven.minenow.MineNowConfig;
import com.sheepfromheaven.minenow.MineNowConfig.Profession;
import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import com.sheepfromheaven.minenow.villager.OpenFenceGates;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.FenceGateBlock;

import java.util.List;

/** A butcher switched off culls, breeds and feeds nothing and opens no gate; run in the {@code profession_butcher} environment. */
public final class SwitchedButcherTests {
    private SwitchedButcherTests() {}

    public static void aSwitchedOffButcherCullsNothingAndOpensNoGate(GameTestHelper helper) {
        helper.assertTrue(!MineNowConfig.professionEnabled(Profession.BUTCHER), "The butcher's environment switches the butcher off");
        Villager butcher = ButcherTests.spawnButcher(helper);
        butcher.getInventory().addItem(new ItemStack(Items.IRON_AXE));
        BlockPos gate = GameTestHelpers.gatedPen(helper, 9);
        List<Animal> pigs = ButcherTests.herd(helper, EntityTypes.PIG, ButcherHerd.BREEDING_STOCK + 3);
        helper.assertTrue(!OpenFenceGates.canOpen(butcher), "A switched-off butcher only opens doors, like any vanilla villager");
        boolean[] opened = {false};
        helper.onEachTick(() -> opened[0] |= helper.getBlockState(gate).getValue(FenceGateBlock.OPEN));
        helper.runAfterDelay(900, () -> {
            helper.assertTrue(ButcherTests.alive(pigs) == pigs.size(), "A switched-off butcher culls nothing");
            helper.assertTrue(!opened[0], "...and opens no fence gate");
            helper.succeed();
        });
    }

    public static void aSwitchedOffButcherBreedsNothing(GameTestHelper helper) {
        helper.assertTrue(!MineNowConfig.professionEnabled(Profession.BUTCHER), "The butcher's environment switches the butcher off");
        Villager butcher = ButcherTests.spawnButcher(helper);
        butcher.getInventory().addItem(new ItemStack(Items.WHEAT, 4));
        GameTestHelpers.gatedPen(helper, 9);
        List<Animal> cows = ButcherTests.herd(helper, EntityTypes.COW, 2);
        helper.runAfterDelay(900, () -> {
            helper.assertTrue(cows.stream().noneMatch(Animal::isInLove) && helper.getLevel().getEntitiesOfClass(Animal.class, helper.getBounds(), Animal::isBaby).isEmpty(),
                    "A switched-off butcher breeds no herd");
            helper.assertTrue(ButcherTests.count(butcher, Items.WHEAT) == 4, "...and keeps its wheat");
            helper.succeed();
        });
    }

    public static void aSwitchedOffButcherFeedsNoBabyAnimal(GameTestHelper helper) {
        helper.assertTrue(!MineNowConfig.professionEnabled(Profession.BUTCHER), "The butcher's environment switches the butcher off");
        Villager butcher = ButcherTests.spawnButcher(helper);
        butcher.getInventory().addItem(new ItemStack(Items.WHEAT, 3));
        ButcherTests.spawnCalf(helper);
        helper.runAfterDelay(600, () -> {
            helper.assertTrue(ButcherTests.count(butcher, Items.WHEAT) == 3, "A switched-off butcher feeds no calf, has " + ButcherTests.count(butcher, Items.WHEAT) + " wheat");
            helper.succeed();
        });
    }

    public static void aSwitchedOffButcherShowsVanillaTradesThatCostEmeralds(GameTestHelper helper) {
        helper.assertTrue(!MineNowConfig.professionEnabled(Profession.BUTCHER), "The butcher's environment switches the butcher off");
        Villager butcher = ButcherTests.spawnButcher(helper);
        var offers = com.sheepfromheaven.minenow.villager.ProfessionTrades.currentOffers(helper.getLevel(), butcher);
        helper.assertTrue(offers.stream().anyMatch(offer -> offer.getBaseCostA().is(Items.EMERALD) || offer.getResult().is(Items.EMERALD)), "Expected a vanilla trade in emeralds, got " + offers.size() + " offers");
        helper.assertTrue(offers.stream().noneMatch(offer -> offer.getBaseCostA().is(Items.IRON_AXE) || offer.getBaseCostA().is(Items.STONE_AXE)), "Hearthfolk's buying of an axe is gone");
        helper.succeed();
    }
}
