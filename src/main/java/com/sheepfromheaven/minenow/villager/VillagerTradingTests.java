package com.sheepfromheaven.minenow.villager;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

public final class VillagerTradingTests {
    private VillagerTradingTests() {}

    public static void butcherBuysWheatFromTheFarmer(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        BlockPos smoker = new BlockPos(4, 1, 12);
        helper.setBlock(smoker, Blocks.SMOKER);
        Villager butcher = GameTestHelpers.spawnEmployed(helper, smoker.east(), smoker, VillagerProfession.BUTCHER, PoiTypes.BUTCHER);
        butcher.getInventory().addItem(new ItemStack(Items.STONE_AXE));
        butcher.getInventory().addItem(new ItemStack(Items.EMERALD, 4));
        BlockPos composter = new BlockPos(19, 1, 12);
        helper.setBlock(composter, Blocks.COMPOSTER);
        Villager farmer = GameTestHelpers.spawnEmployed(helper, composter.west(), composter, VillagerProfession.FARMER, PoiTypes.FARMER);
        farmer.getInventory().addItem(new ItemStack(Items.WHEAT, 40));
        double[] closest = {Double.MAX_VALUE};
        helper.onEachTick(() -> closest[0] = Math.min(closest[0], butcher.distanceTo(farmer)));

        helper.succeedWhen(() -> {
            helper.assertTrue(count(butcher, Items.WHEAT) >= 20, "The butcher should buy wheat from the farmer, has " + count(butcher, Items.WHEAT));
            helper.assertTrue(count(butcher, Items.EMERALD) == 3, "It pays the farmer's price of one emerald for 20, has " + count(butcher, Items.EMERALD) + " emeralds left");
            helper.assertTrue(count(farmer, Items.EMERALD) == 1, "The farmer gets the emerald");
            helper.assertTrue(count(farmer, Items.WHEAT) == 20, "The farmer hands over 20 wheat, has " + count(farmer, Items.WHEAT));
            helper.assertTrue(closest[0] <= 3.0, "It walks over to the farmer to trade, came within " + closest[0]);
        });
    }

    public static void aVillagerKeepsItsReserveWhenBuyingFromVillagers(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        BlockPos smoker = new BlockPos(4, 1, 12);
        helper.setBlock(smoker, Blocks.SMOKER);
        Villager butcher = GameTestHelpers.spawnEmployed(helper, smoker.east(), smoker, VillagerProfession.BUTCHER, PoiTypes.BUTCHER);
        butcher.getInventory().addItem(new ItemStack(Items.STONE_AXE));
        butcher.getInventory().addItem(new ItemStack(Items.EMERALD, Economy.FOOD_MONEY + 1));
        BlockPos composter = new BlockPos(19, 1, 12);
        helper.setBlock(composter, Blocks.COMPOSTER);
        Villager farmer = GameTestHelpers.spawnEmployed(helper, composter.west(), composter, VillagerProfession.FARMER, PoiTypes.FARMER);
        farmer.getInventory().addItem(new ItemStack(Items.WHEAT, 40));

        helper.runAfterDelay(600, () -> {
            helper.assertTrue(count(butcher, Items.EMERALD) == Economy.FOOD_MONEY + 1, "With only its food money and a new axe's price it buys no supplies, has " + count(butcher, Items.EMERALD));
            helper.assertTrue(count(butcher, Items.WHEAT) == 0 || BuyFromVillagers.debt(butcher) > 0, "...so any wheat it takes is on credit");
            helper.succeed();
        });
    }

    private static final BlockPos TABLE = new BlockPos(11, 1, 12);

    private static Villager spawnToolsmith(GameTestHelper helper, int emeralds) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(TABLE, Blocks.SMITHING_TABLE);
        Villager smith = GameTestHelpers.spawnEmployed(helper, TABLE.west(), TABLE, VillagerProfession.TOOLSMITH, PoiTypes.TOOLSMITH);
        smith.getInventory().addItem(new ItemStack(Items.EMERALD, emeralds));
        return smith;
    }

    private static net.minecraft.world.item.trading.MerchantOffer offerOf(net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader merchant, Item item) {
        return merchant.getOffers().stream().filter(offer -> offer.getResult().is(item)).findFirst().orElseThrow();
    }

    public static void aVillagerBuysItsSupplyFromATravellingMerchant(GameTestHelper helper) {
        Villager smith = spawnToolsmith(helper, 20);
        var merchant = Exports.sendTrader(helper.getLevel(), smith);
        helper.assertTrue(merchant != null, "A travelling merchant comes");

        helper.succeedWhen(() -> {
            helper.assertTrue(count(smith, Items.STRING) >= 8, "The toolsmith buys the string it needs from the merchant, has " + count(smith, Items.STRING));
            helper.assertTrue(count(smith, Items.STRING) <= 16, "...up to its max, has " + count(smith, Items.STRING));
            helper.assertTrue(count(smith, Items.EMERALD) == 20 - count(smith, Items.STRING) / 8, "...at the merchant's price, 8 for an emerald, has " + count(smith, Items.EMERALD) + " emeralds");
            helper.assertTrue(offerOf(merchant, Items.STRING).getUses() == count(smith, Items.STRING) / 8, "...and the merchant's stock goes down with it");
        });
    }

    public static void aVillagerNeverBuysFromAMerchantBelowItsReserve(GameTestHelper helper) {
        Villager smith = spawnToolsmith(helper, 0);
        smith.getInventory().addItem(new ItemStack(Items.EMERALD, 2));
        Exports.sendTrader(helper.getLevel(), smith);

        helper.runAfterDelay(600, () -> {
            helper.assertTrue(count(smith, Items.STRING) == 0, "Its food money is not for string, has " + count(smith, Items.STRING));
            helper.succeed();
        });
    }

    public static void aVillagerBuysNothingFromAMerchantOutOfStock(GameTestHelper helper) {
        Villager smith = spawnToolsmith(helper, 20);
        var merchant = Exports.sendTrader(helper.getLevel(), smith);
        var string = offerOf(merchant, Items.STRING);
        while (!string.isOutOfStock()) string.increaseUses();

        helper.runAfterDelay(600, () -> {
            helper.assertTrue(count(smith, Items.STRING) == 0, "A merchant with no string left sells none, it bought " + count(smith, Items.STRING));
            helper.succeed();
        });
    }

    public static void aVillagerBuysNothingFromAPlainWanderingTrader(GameTestHelper helper) {
        Villager smith = spawnToolsmith(helper, 20);
        var trader = helper.spawn(net.minecraft.world.entity.EntityTypes.WANDERING_TRADER, new BlockPos(8, 1, 12));
        trader.getOffers().clear();
        trader.getOffers().add(new net.minecraft.world.item.trading.MerchantOffer(new net.minecraft.world.item.trading.ItemCost(Items.EMERALD, 1), new ItemStack(Items.STRING, 8), 8, 1, 0.05F));

        helper.runAfterDelay(600, () -> {
            helper.assertTrue(count(smith, Items.STRING) == 0, "Only the travelling merchant's wares are for villagers, it bought " + count(smith, Items.STRING));
            helper.succeed();
        });
    }

    public static void demandIsWhatAVillagerCouldBuyFromTheMerchant(GameTestHelper helper) {
        Villager smith = spawnToolsmith(helper, 20);
        var level = helper.getLevel();
        helper.assertTrue(BuyFromVillagers.demand(level, smith) == 2, "A toolsmith with money and no string would spend 2 emeralds on 16 string, demand " + BuyFromVillagers.demand(level, smith));
        smith.getInventory().addItem(new ItemStack(Items.STRING, 8));
        helper.assertTrue(BuyFromVillagers.demand(level, smith) == 0, "With a batch of string it needs none, demand " + BuyFromVillagers.demand(level, smith));
        smith.getInventory().clearContent();
        smith.getInventory().addItem(new ItemStack(Items.EMERALD, 2));
        helper.assertTrue(BuyFromVillagers.demand(level, smith) == 0, "With only its food money it can't buy, demand " + BuyFromVillagers.demand(level, smith));
        helper.succeed();
    }

    public static void unmetNeedsDrawATravellingMerchant(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        java.util.List<Villager> smiths = new java.util.ArrayList<>();
        for (int i = 0; i < 4; i++) {
            BlockPos table = new BlockPos(6 + 4 * i, 1, 12);
            helper.setBlock(table, Blocks.SMITHING_TABLE);
            Villager smith = GameTestHelpers.spawnEmployed(helper, table.south(), table, VillagerProfession.TOOLSMITH, PoiTypes.TOOLSMITH);
            smiths.add(smith);
        }
        @SuppressWarnings("removal")
        net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setPos(smiths.get(0).position());
        Exports.visitNear(helper.getLevel(), player, () -> 0.0);
        helper.assertTrue(merchants(helper) == 0, "Villagers with nothing to spend draw no merchant, got " + merchants(helper));
        smiths.get(0).getInventory().addItem(new ItemStack(Items.EMERALD, 20));
        Exports.visitNear(helper.getLevel(), player, () -> 0.0);
        helper.assertTrue(merchants(helper) == 0, "One toolsmith's 2 emeralds are not worth the trip, got " + merchants(helper));
        smiths.forEach(smith -> smith.getInventory().addItem(new ItemStack(Items.EMERALD, 20)));
        Exports.visitNear(helper.getLevel(), player, () -> 0.0);
        helper.assertTrue(merchants(helper) == 1, "Four toolsmiths out of string and with money draw a merchant, got " + merchants(helper));
        helper.succeed();
    }

    public static void aTravellingMerchantWalksToTheBellAndOpensTheMarket(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        BlockPos bell = new BlockPos(4, 1, 12);
        BlockPos table = new BlockPos(20, 1, 12);
        helper.setBlock(table, Blocks.SMITHING_TABLE);
        Villager smith = GameTestHelpers.spawnEmployed(helper, table.south(), table, VillagerProfession.TOOLSMITH, PoiTypes.TOOLSMITH);
        VillageTests.claimBell(helper, smith, bell);
        var merchant = Exports.sendTrader(helper.getLevel(), smith);
        helper.assertTrue(merchant != null, "A travelling merchant comes");

        helper.succeedWhen(() -> {
            helper.assertTrue(merchant.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(bell))) <= 16, "The merchant walks to the village's bell, is at " + helper.relativePos(merchant.blockPosition()).toShortString());
            helper.assertTrue(Exports.openMarket(merchant, helper.getLevel().getGameTime()).filter(helper.absolutePos(bell)::equals).isPresent(), "...and opens the market there");
            helper.assertTrue(!smith.getBrain().hasMemoryValue(net.minecraft.world.entity.ai.memory.MemoryModuleType.HEARD_BELL_TIME),
                    "...without ringing the bell for real, which would send the villagers into hiding");
        });
    }

    public static void villagersBeyondTheMerchantsReachComeWhenItOpensTheMarket(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        BlockPos bell = new BlockPos(4, 1, 12);
        Villager neighbour = helper.spawn(net.minecraft.world.entity.EntityTypes.VILLAGER, new BlockPos(6, 1, 12));
        VillageTests.claimBell(helper, neighbour, bell);
        BlockPos table = new BlockPos(66, 1, 12);
        helper.setBlock(table, Blocks.SMITHING_TABLE);
        Villager smith = GameTestHelpers.spawnEmployed(helper, table.south(), table, VillagerProfession.TOOLSMITH, PoiTypes.TOOLSMITH);
        smith.getInventory().addItem(new ItemStack(Items.EMERALD, 20));
        VillageTests.claimBell(helper, smith, bell);
        helper.assertTrue(Exports.sendTrader(helper.getLevel(), neighbour) != null, "A travelling merchant comes");

        helper.succeedWhen(() -> helper.assertTrue(count(smith, Items.STRING) >= 8,
                "A toolsmith 60 blocks from the merchant hears the market open and walks over to buy string, has " + count(smith, Items.STRING)));
    }

    private static int merchants(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader.class, helper.getBounds().inflate(16)).size();
    }

    private static int count(Villager villager, Item item) {
        return villager.getInventory().countItem(item);
    }

    public static void aSellerOutOfReachDoesNotCostTheBuyerItsJob(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        BlockPos composter = new BlockPos(5, 1, 12);
        helper.setBlock(composter, Blocks.COMPOSTER);
        Villager buyer = GameTestHelpers.spawnEmployed(helper, composter.east(), composter, VillagerProfession.FARMER, PoiTypes.FARMER);
        buyer.getInventory().addItem(new ItemStack(Items.EMERALD, 5));
        BlockPos sellerSite = new BlockPos(11, 1, 12);
        for (int x = 8; x <= 14; x++) {
            for (int z = 9; z <= 15; z++) {
                for (int y = 1; y <= 3; y++) {
                    if (x == 8 || x == 14 || z == 9 || z == 15 || y == 3) helper.setBlock(x, y, z, Blocks.GLASS);
                }
            }
        }
        helper.setBlock(sellerSite, Blocks.COMPOSTER);
        Villager seller = GameTestHelpers.spawnEmployed(helper, sellerSite.east(), sellerSite, VillagerProfession.FARMER, PoiTypes.FARMER);
        seller.getInventory().addItem(new ItemStack(Items.POTATO, 64));
        seller.setNoAi(true);
        seller.snapTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(9.6, 1, 12.5)));
        helper.onEachTick(() -> com.sheepfromheaven.minenow.villager.Meals.makeHungry(buyer));

        helper.runAfterDelay(2600, () -> {
            helper.assertTrue(buyer.getBrain().hasMemoryValue(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE),
                    "Failing to reach a seller behind glass must not count as failing to reach its own composter, or vanilla takes its job away");
            helper.assertTrue(buyer.getInventory().countItem(Items.EMERALD) == 5, "...and it can't buy through the glass, has " + buyer.getInventory().countItem(Items.EMERALD));
            helper.succeed();
        });
    }
}
