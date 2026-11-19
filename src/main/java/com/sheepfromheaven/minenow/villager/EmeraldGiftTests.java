package com.sheepfromheaven.minenow.villager;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** Emeralds thrown to a villager are money for it, up to what it needs ({@link PickUpGifts#emeraldCap}). */
public final class EmeraldGiftTests {
    private EmeraldGiftTests() {}

    public static void aVillagerPicksUpEmeraldsThrownToIt(GameTestHelper helper) {
        Villager farmer = farmer(helper);
        helper.runAfterDelay(40, () -> throwEmeralds(helper, 3));

        helper.succeedWhen(() -> helper.assertTrue(farmer.getInventory().countItem(Items.EMERALD) == 3,
                "The farmer should pick up the emeralds thrown to it, has " + farmer.getInventory().countItem(Items.EMERALD)));
    }

    public static void aVillagerTakesOnlyAsManyEmeraldsAsItNeeds(GameTestHelper helper) {
        Villager farmer = farmer(helper);
        int cap = PickUpGifts.emeraldCap(helper.getLevel(), farmer);
        farmer.getInventory().addItem(new ItemStack(Items.EMERALD, cap - 2));
        helper.runAfterDelay(40, () -> throwEmeralds(helper, 10));

        helper.runAfterDelay(400, () -> {
            helper.assertTrue(farmer.getInventory().countItem(Items.EMERALD) == cap,
                    "The farmer takes emeralds up to " + cap + ", has " + farmer.getInventory().countItem(Items.EMERALD));
            helper.succeed();
        });
    }

    private static Villager farmer(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        BlockPos composter = new BlockPos(12, 1, 12);
        helper.setBlock(composter, Blocks.COMPOSTER);
        Villager farmer = GameTestHelpers.spawnEmployed(helper, composter.west(), composter, VillagerProfession.FARMER, PoiTypes.FARMER);
        farmer.getInventory().addItem(new ItemStack(Items.IRON_HOE));
        farmer.getInventory().addItem(new ItemStack(Items.WHEAT_SEEDS, 16));
        return farmer;
    }

    private static void throwEmeralds(GameTestHelper helper, int count) {
        BlockPos drop = helper.absolutePos(new BlockPos(14, 1, 12));
        helper.getLevel().addFreshEntity(new ItemEntity(helper.getLevel(), drop.getX() + 0.5, drop.getY(), drop.getZ() + 0.5, new ItemStack(Items.EMERALD, count)));
    }
}
