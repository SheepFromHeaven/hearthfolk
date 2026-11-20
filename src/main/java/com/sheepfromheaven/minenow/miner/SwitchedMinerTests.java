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

    /** Next to a furnace cart, off, nobody takes a job. */
    public static void aSwitchedOffMinerTakesNoCart(GameTestHelper helper) {
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
            helper.succeed();
        });
    }

    /** Switched on, an unemployed villager with experience takes the cart and comes back at its old level. */
    public static void anUnemployedVillagerWithExperienceTakesTheCartAtItsOldLevel(GameTestHelper helper) {
        helper.assertTrue(MineNowConfig.professionEnabled(Profession.MINER), "Miners are on in a plain test");
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(new BlockPos(10, 1, 12), Miner.FURNACE_CART.get());
        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(13, 1, 12));
        villager.setVillagerData(villager.getVillagerData().withLevel(3));
        villager.setVillagerXp(100);

        helper.succeedWhen(() -> {
            helper.assertTrue(villager.getVillagerData().profession().is(Miner.PROFESSION), "Expected it to take the cart");
            helper.assertTrue(villager.getVillagerData().level() == 3, "Expected its old level back");
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
