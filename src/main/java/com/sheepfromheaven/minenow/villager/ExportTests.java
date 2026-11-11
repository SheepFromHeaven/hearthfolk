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
    private static final BlockPos ANVIL = new BlockPos(11, 1, 12);

    private ExportTests() {}

    public static void aWanderingTraderBuysAVillagesSurplusGoods(GameTestHelper helper) {
        Villager armorer = spawnArmorer(helper);
        armorer.getInventory().addItem(new ItemStack(Items.IRON_CHESTPLATE));
        armorer.getInventory().addItem(new ItemStack(Items.IRON_HELMET));
        helper.assertTrue(Exports.sendTrader(helper.getLevel(), armorer) != null, "A travelling merchant comes");

        helper.succeedWhen(() -> {
            helper.assertTrue(armorer.getInventory().countItem(Items.IRON_CHESTPLATE) + armorer.getInventory().countItem(Items.IRON_HELMET) < 2,
                    "The travelling merchant buys the armorer's armor to take away, it still has " + armorer.getInventory().countItem(Items.IRON_CHESTPLATE) + " chestplates, " + armorer.getInventory().countItem(Items.IRON_HELMET) + " helmets");
            helper.assertTrue(Economy.emeralds(helper.getLevel(), armorer) >= 2, "...paying new emeralds into the village, it has " + Economy.emeralds(helper.getLevel(), armorer));
        });
    }

    public static void aTravellingMerchantBuysBulkSurplusAtHalfPrice(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        BlockPos cart = new BlockPos(11, 1, 12);
        helper.setBlock(cart, com.sheepfromheaven.minenow.miner.Miner.FURNACE_CART.get());
        Villager miner = GameTestHelpers.spawnEmployed(helper, cart.west(), cart, com.sheepfromheaven.minenow.miner.Miner.PROFESSION, com.sheepfromheaven.minenow.miner.Miner.POI);
        miner.getInventory().addItem(new ItemStack(Items.WOODEN_PICKAXE));
        for (int i = 0; i < 3; i++) miner.getInventory().addItem(new ItemStack(Items.COBBLESTONE, 64));
        helper.assertTrue(Exports.surplus(helper.getLevel(), miner) > 0, "A pile of cobblestone nobody in the village needs is worth a visit, surplus " + Exports.surplus(helper.getLevel(), miner));
        helper.assertTrue(Exports.sendTrader(helper.getLevel(), miner) != null, "A travelling merchant comes");

        helper.succeedWhen(() -> {
            int cobble = miner.getInventory().countItem(Items.COBBLESTONE);
            helper.assertTrue(cobble < 192, "The merchant buys the miner's surplus cobblestone, it still has " + cobble);
            helper.assertTrue(cobble >= Exports.BULK_KEEP, "...but never what it keeps or what it sells at home, it has " + cobble);
            helper.assertTrue(miner.getInventory().countItem(Items.EMERALD) * 64 <= 192 - cobble, "...at half its price (32 for an emerald), paid "
                    + miner.getInventory().countItem(Items.EMERALD) + " for " + (192 - cobble));
        });
    }

    public static void aTravellingMerchantBuysAClericsSurplusPotions(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        BlockPos stand = new BlockPos(11, 1, 12);
        helper.setBlock(stand, Blocks.BREWING_STAND);
        Villager cleric = GameTestHelpers.spawnEmployed(helper, stand.west(), stand, VillagerProfession.CLERIC, net.minecraft.world.entity.ai.village.poi.PoiTypes.CLERIC);
        cleric.setVillagerData(cleric.getVillagerData().withLevel(2));
        var swiftness = net.minecraft.world.item.alchemy.Potions.SWIFTNESS;
        var awkward = net.minecraft.world.item.alchemy.Potions.AWKWARD;
        for (int i = 0; i < 3; i++) cleric.getInventory().addItem(com.sheepfromheaven.minenow.cleric.PotionGoods.stack(Items.POTION, swiftness));
        for (int i = 0; i < 3; i++) cleric.getInventory().addItem(com.sheepfromheaven.minenow.cleric.PotionGoods.stack(Items.POTION, awkward));
        helper.assertTrue(Exports.surplus(helper.getLevel(), cleric) >= 4, "Two spare swiftness potions are worth a visit, surplus " + Exports.surplus(helper.getLevel(), cleric));
        helper.assertTrue(Exports.sendTrader(helper.getLevel(), cleric) != null, "A travelling merchant comes");

        helper.succeedWhen(() -> {
            int swift = com.sheepfromheaven.minenow.cleric.PotionGoods.count(helper.getLevel(), cleric, Items.POTION, swiftness);
            int awk = com.sheepfromheaven.minenow.cleric.PotionGoods.count(helper.getLevel(), cleric, Items.POTION, awkward);
            helper.assertTrue(swift == 1, "The merchant buys all but one swiftness potion, it has " + swift);
            helper.assertTrue(awk == 3, "...but never the awkward potions it brews with, it has " + awk);
            helper.assertTrue(Economy.emeralds(helper.getLevel(), cleric) >= 4, "...paying the cleric's price, it has " + Economy.emeralds(helper.getLevel(), cleric));
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

    public static void aTravellingMerchantLooksAndTradesUnlikeAWanderingTrader(GameTestHelper helper) {
        Villager armorer = spawnArmorer(helper);
        for (int i = 0; i < 4; i++) armorer.getInventory().addItem(new ItemStack(Items.IRON_CHESTPLATE));

        WanderingTrader merchant = Exports.sendTrader(helper.getLevel(), armorer);

        helper.assertTrue(merchant != null && Exports.isMerchant(merchant), "A travelling merchant comes");
        helper.assertTrue(merchant.hasCustomName() && merchant.getCustomName().getString().equals("Travelling Merchant"), "...named so, " + merchant.getCustomName());
        var mules = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.animal.equine.Mule.class, merchant.getBoundingBox().inflate(8), mule -> mule.getLeashHolder() == merchant);
        helper.assertTrue(mules.size() == 2 && mules.stream().allMatch(net.minecraft.world.entity.animal.equine.AbstractChestedHorse::hasChest), "...with two pack mules on leads, has " + mules.size());
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.animal.equine.TraderLlama.class, merchant.getBoundingBox().inflate(16)).isEmpty(), "...and no trader llamas");
        var sold = merchant.getOffers().stream().map(offer -> offer.getResult().getItem()).toList();
        helper.assertTrue(!sold.isEmpty() && sold.contains(Items.STRING) && sold.stream().allMatch(item -> java.util.List.of(Items.STRING, Items.SUGAR_CANE, Items.COCOA_BEANS, Items.SWEET_BERRIES, Items.HONEYCOMB, Items.NETHER_WART, Items.BLAZE_POWDER, Items.GUNPOWDER, Items.SPIDER_EYE, Items.BROWN_MUSHROOM, Items.GLASS_BOTTLE).contains(item)),
                "...selling goods from afar the village can't make, " + sold);
        for (var brewing : java.util.List.of(Items.NETHER_WART, Items.BLAZE_POWDER, Items.GUNPOWDER, Items.SPIDER_EYE, Items.BROWN_MUSHROOM)) {
            helper.assertTrue(sold.contains(brewing), "...and what a cleric brews with, " + brewing + " among " + sold);
        }
        var string = merchant.getOffers().stream().filter(offer -> offer.getResult().is(Items.STRING)).findFirst().orElseThrow();
        helper.assertTrue(string.getResult().getCount() == 8 && string.getCostA().getCount() == 1 && string.getCostA().is(Items.EMERALD),
                "...at the data file's price, 8 string for an emerald, not " + string.getResult().getCount() + " for " + string.getCostA().getCount());
        merchant.discard();
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(mules.stream().noneMatch(net.minecraft.world.entity.Entity::isAlive), "Its mules leave with it");
            helper.succeed();
        });
    }

    public static void aPlainWanderingTraderDoesNotBuyExports(GameTestHelper helper) {
        Villager armorer = spawnArmorer(helper);
        armorer.getInventory().addItem(new ItemStack(Items.IRON_CHESTPLATE));
        helper.spawn(EntityTypes.WANDERING_TRADER, new BlockPos(7, 1, 12));

        helper.runAfterDelay(400, () -> {
            helper.assertTrue(armorer.getInventory().countItem(Items.IRON_CHESTPLATE) == 1, "Only the travelling merchant buys exports; vanilla's wandering trader stays vanilla");
            helper.succeed();
        });
    }

    public static void travellingMerchantsCanBeSwitchedOff(GameTestHelper helper) {
        Villager armorer = spawnArmorer(helper);
        for (int i = 0; i < 4; i++) armorer.getInventory().addItem(new ItemStack(Items.IRON_CHESTPLATE));
        @SuppressWarnings("removal")
        net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setPos(armorer.position());
        boolean before = com.sheepfromheaven.minenow.MineNowConfig.TRAVELLING_MERCHANTS.get();
        try {
            com.sheepfromheaven.minenow.MineNowConfig.TRAVELLING_MERCHANTS.set(false);
            Exports.visitNear(helper.getLevel(), player, () -> 0.0);
            helper.assertTrue(merchants(helper) == 0, "Switched off, no merchant comes however many exports, got " + merchants(helper));
            com.sheepfromheaven.minenow.MineNowConfig.TRAVELLING_MERCHANTS.set(true);
            Exports.visitNear(helper.getLevel(), player, () -> 0.0);
            helper.assertTrue(merchants(helper) == 1, "Switched on, the same village draws one, got " + merchants(helper));
        } finally {
            com.sheepfromheaven.minenow.MineNowConfig.TRAVELLING_MERCHANTS.set(before);
        }
        helper.succeed();
    }

    private static int merchants(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(WanderingTrader.class, helper.getBounds().inflate(16)).size();
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
