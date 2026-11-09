package com.sheepfromheaven.minenow.villager;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

public final class ExportTests {
    private static final BlockPos ANVIL = new BlockPos(5, 1, 12);

    private ExportTests() {}

    public static void aWanderingTraderBuysAVillagesSurplusGoods(GameTestHelper helper) {
        Villager armorer = spawnArmorer(helper);
        armorer.getInventory().addItem(new ItemStack(Items.IRON_CHESTPLATE));
        armorer.getInventory().addItem(new ItemStack(Items.IRON_HELMET));
        helper.spawn(EntityTypes.WANDERING_TRADER, new BlockPos(14, 1, 12));

        helper.succeedWhen(() -> {
            helper.assertTrue(armorer.getInventory().countItem(Items.IRON_CHESTPLATE) + armorer.getInventory().countItem(Items.IRON_HELMET) < 2,
                    "The wandering trader buys the armorer's armor to take away, it still has " + armorer.getInventory().countItem(Items.IRON_CHESTPLATE) + " chestplates, " + armorer.getInventory().countItem(Items.IRON_HELMET) + " helmets");
            helper.assertTrue(Economy.emeralds(helper.getLevel(), armorer) >= 2, "...paying new emeralds into the village, it has " + Economy.emeralds(helper.getLevel(), armorer));
        });
    }

    public static void surplusGoodsDrawAWanderingTraderToTheVillage(GameTestHelper helper) {
        Villager armorer = spawnArmorer(helper);
        for (int i = 0; i < 4; i++) armorer.getInventory().addItem(new ItemStack(Items.IRON_CHESTPLATE));
        helper.assertTrue(Exports.surplus(helper.getLevel(), armorer) >= 8, "Four chestplates are worth a visit, surplus " + Exports.surplus(helper.getLevel(), armorer));

        WanderingTrader trader = Exports.sendTrader(helper.getLevel(), armorer);

        helper.assertTrue(trader != null && trader.distanceTo(armorer) <= 16, "A wandering trader comes to the village with goods to export, " + trader);
        helper.succeed();
    }

    private static Villager spawnArmorer(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(ANVIL, Blocks.ANVIL);
        Villager armorer = GameTestHelpers.spawnEmployed(helper, ANVIL.east(), ANVIL, VillagerProfession.ARMORER, PoiTypes.ARMORER);
        armorer.setVillagerData(armorer.getVillagerData().withLevel(3));
        return armorer;
    }
}
