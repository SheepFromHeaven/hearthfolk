package com.sheepfromheaven.minenow.merchant;

import com.sheepfromheaven.minenow.MineNowConfig;
import com.sheepfromheaven.minenow.MineNowConfig.Profession;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.equine.Mule;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Optional;

public final class SwitchedMerchantTests {
    private SwitchedMerchantTests() {}

    /** Switched off mid-tour a merchant is unemployed, holds no tour ticket, its mules are nobody's and their cargo is in its goods. */
    public static void aSwitchedOffMerchantEndsItsTourAndKeepsItsCargo(GameTestHelper helper) {
        helper.assertTrue(!MineNowConfig.professionEnabled(Profession.MERCHANT), "The environment switches merchants off");
        Villager merchant = MerchantTests.spawnMerchant(helper, 2);
        Mule mule = helper.spawn(EntityTypes.MULE, new BlockPos(13, 1, 12));
        mule.getSlot(499).set(new ItemStack(Items.CHEST));
        mule.setData(Merchant.MULE_OWNER, Optional.of(merchant.getUUID()));
        mule.getInventory().setItem(0, new ItemStack(Items.WHEAT, 20));
        long now = helper.getLevel().getGameTime();
        merchant.setData(Merchant.TOUR, Optional.of(new Tour(List.of(helper.absolutePos(new BlockPos(22, 1, 22))), 0, Tour.Phase.TRAVEL, now, now, merchant.blockPosition(), List.of())));
        Tours.resume(merchant);

        helper.succeedWhen(() -> {
            helper.assertTrue(merchant.getVillagerData().profession().is(VillagerProfession.NONE), "Expected the merchant to be unemployed");
            helper.assertTrue(merchant.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty(), "Expected its job site to be forgotten");
            helper.assertTrue(merchant.getData(Merchant.TOUR).isEmpty() && !Tours.active(merchant), "Expected its tour to end and its ticket to go");
            helper.assertTrue(mule.getData(Merchant.MULE_OWNER).isEmpty(), "Expected its mule to be nobody's");
            helper.assertTrue(mule.hasChest() && mule.getInventory().countItem(Items.WHEAT) == 0, "Expected the chest to stay on the mule, emptied");
            helper.assertTrue(merchant.getInventory().countItem(Items.WHEAT) == 20, "Expected its cargo in its goods");
        });
    }
}
