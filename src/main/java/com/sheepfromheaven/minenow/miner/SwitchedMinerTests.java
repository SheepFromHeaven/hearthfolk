package com.sheepfromheaven.minenow.miner;

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

public final class SwitchedMinerTests {
    private SwitchedMinerTests() {}

    /** A level 3 miner switched off becomes unemployed at once and keeps its pockets and its experience. */
    public static void aSwitchedOffMinerLosesItsJobAndKeepsItsItems(GameTestHelper helper) {
        helper.assertTrue(!MineNowConfig.professionEnabled(Profession.MINER), "The environment switches miners off");
        Villager miner = MinerTests.spawnMiner(helper);
        miner.setVillagerData(miner.getVillagerData().withLevel(3));
        miner.setVillagerXp(100);
        miner.getInventory().addItem(new ItemStack(Items.STONE_PICKAXE));
        miner.getInventory().addItem(new ItemStack(Items.EMERALD, 5));

        helper.succeedWhen(() -> {
            helper.assertTrue(miner.getVillagerData().profession().is(VillagerProfession.NONE), "Expected the miner to be unemployed");
            helper.assertTrue(miner.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty(), "Expected its job site to be forgotten");
            helper.assertTrue(miner.getVillagerData().level() == 3 && miner.getVillagerXp() == 100, "Expected its experience to stay");
            helper.assertTrue(miner.getInventory().countItem(Items.EMERALD) == 5 && miner.getInventory().countItem(Items.STONE_PICKAXE) == 1, "Expected its items to stay");
        });
    }

    /** Next to its own furnace cart, off it takes no job; switched back on it takes it again, at its old level. */
    public static void aSwitchedOffMinerTakesNoCartUntilSwitchedBackOn(GameTestHelper helper) {
        helper.assertTrue(!MineNowConfig.professionEnabled(Profession.MINER), "The environment switches miners off");
        Villager miner = MinerTests.spawnMiner(helper);
        miner.setVillagerData(miner.getVillagerData().withLevel(3));
        miner.setVillagerXp(100);
        Villager other = helper.spawn(EntityTypes.VILLAGER, MinerTests.FURNACE.east());

        helper.runAfterDelay(500, () -> {
            helper.assertTrue(miner.getVillagerData().profession().is(VillagerProfession.NONE), "Expected the miner to stay unemployed while off");
            helper.assertTrue(other.getVillagerData().profession().is(VillagerProfession.NONE), "Expected no villager to take the cart while off");
            helper.assertTrue(miner.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty() && other.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty(),
                    "Expected nobody to hold the cart");
            MineNowConfig.set(Profession.MINER, true);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(MineNowConfig.professionEnabled(Profession.MINER), "Waiting for the switch");
            boolean taken = miner.getVillagerData().profession().is(Miner.PROFESSION) || other.getVillagerData().profession().is(Miner.PROFESSION);
            helper.assertTrue(taken, "Expected a villager to take the cart once switched back on");
            helper.assertTrue(!miner.getVillagerData().profession().is(Miner.PROFESSION) || miner.getVillagerData().level() == 3, "Expected the old level back");
        });
    }

    /** A miner down its mine when it is switched off ends up on the surface, and the shaft is no longer closed to others. */
    public static void aMinerSwitchedOffDownItsMineEndsUpOnTheSurface(GameTestHelper helper) {
        helper.assertTrue(!MineNowConfig.professionEnabled(Profession.MINER), "The environment switches miners off");
        Villager miner = MinerTests.spawnMiner(helper);
        MinerTests.quarry(helper);
        for (int y = 1; y <= 3; y++) {
            for (int x = 7; x <= 9; x++) {
                for (int z = 11; z <= 13; z++) helper.setBlock(x, y, z, Blocks.AIR);
            }
            helper.setBlock(new BlockPos(7, y, 11), Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING, net.minecraft.core.Direction.EAST));
        }
        miner.getInventory().addItem(new ItemStack(Items.STONE_PICKAXE));
        miner.snapTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(9.5, 1, 13.5)));
        int surface = helper.absolutePos(new BlockPos(0, 4, 0)).getY();
        Villager visitor = helper.spawn(EntityTypes.VILLAGER, new BlockPos(20, 4, 20));

        helper.succeedWhen(() -> {
            helper.assertTrue(miner.getVillagerData().profession().is(VillagerProfession.NONE), "Expected the miner to be unemployed");
            helper.assertTrue(miner.getY() >= surface && miner.onGround(), "Expected it on the surface, at " + helper.relativePos(miner.blockPosition()).toShortString());
            BlockPos inShaft = helper.absolutePos(new BlockPos(8, 2, 12));
            helper.assertTrue(!Miner.shaftClosedTo(visitor, inShaft.getX(), inShaft.getY(), inShaft.getZ()), "Expected the shaft to be open again");
        });
    }
}
