package com.sheepfromheaven.minenow.farmer;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import com.sheepfromheaven.minenow.villager.Economy;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.util.Optional;

public final class FarmerTradeTests {
    private static final BlockPos COMPOSTER = new BlockPos(4, 1, 4);
    private static final BlockPos BED = new BlockPos(8, 1, 4);
    private static final BlockPos HOME_CHEST = new BlockPos(10, 1, 4);

    private FarmerTradeTests() {}

    public static void hoelessFarmerOffersToBuyAnUndamagedHoe(GameTestHelper helper) {
        Villager farmer = spawnFarmer(helper);
        ServerPlayer player = player(helper);

        openTrades(player, farmer);

        Optional<MerchantOffer> buyHoe = offerFor(farmer, Items.WOODEN_HOE);
        helper.assertTrue(buyHoe.isPresent(), "A farmer without a hoe should offer to buy one, offers: " + describe(farmer));
        helper.assertTrue(buyHoe.get().getResult().is(Items.EMERALD), "The farmer should pay in emeralds");
        ItemStack damaged = new ItemStack(Items.WOODEN_HOE);
        damaged.setDamageValue(10);
        helper.assertTrue(!buyHoe.get().satisfiedBy(damaged, ItemStack.EMPTY), "The farmer should only buy undamaged hoes");
        helper.assertTrue(buyHoe.get().satisfiedBy(new ItemStack(Items.WOODEN_HOE), ItemStack.EMPTY), "An undamaged hoe should be accepted");
        helper.succeed();
    }

    public static void sellingAHoeToAFarmerMovesRealItems(GameTestHelper helper) {
        Villager farmer = spawnFarmer(helper);
        ServerPlayer player = player(helper);
        openTrades(player, farmer);
        int price = offerFor(farmer, Items.WOODEN_HOE).orElseThrow().getResult().getCount();

        ItemStack paid = trade(player, new ItemStack(Items.WOODEN_HOE));

        helper.assertTrue(paid.is(Items.EMERALD) && paid.getCount() == price, "Player should receive " + price + " emeralds, got " + paid);
        helper.assertTrue(Hoes.has(farmer), "Farmer should now own the hoe");
        helper.assertTrue(Economy.emeralds(helper.getLevel(), farmer) == Economy.STARTING_PURSE - price, "Farmer should have paid from its purse");
        farmer.getOffers().stream().filter(o -> Hoes.isHoe(o.getCostA().getItem().getDefaultInstance())).forEach(o ->
                helper.assertTrue(o.isOutOfStock(), "Once it owns a hoe the farmer should stop buying hoes in the same session"));
        helper.succeed();
    }

    public static void farmerSellsCropsFromItsInventoryAndChest(GameTestHelper helper) {
        Villager farmer = spawnFarmer(helper);
        farmer.getInventory().addItem(new ItemStack(Items.IRON_HOE));
        farmer.getInventory().addItem(new ItemStack(Items.WHEAT, 10));
        helper.setBlock(HOME_CHEST, Blocks.CHEST);
        GameTestHelpers.giveHome(helper, farmer, BED);
        GameTestHelpers.fillOnceClaimed(helper, () -> com.sheepfromheaven.minenow.villager.HomeStorage.storageOf(farmer).isPresent(), HOME_CHEST, new ItemStack(Items.WHEAT, 30));
        ServerPlayer player = player(helper);

        helper.succeedWhen(() -> {
            helper.assertTrue(com.sheepfromheaven.minenow.villager.HomeStorage.storageOf(farmer).isPresent(), "Farmer has not claimed its chest yet");
            openTrades(player, farmer);
            MerchantOffer sellWheat = offerSelling(farmer, Items.WHEAT).orElseThrow(() ->
                    helper.assertionException("Farmer with 40 wheat should sell it, offers: " + describe(farmer)));
            int batch = sellWheat.getResult().getCount();
            helper.assertTrue(sellWheat.getMaxUses() == 40 / batch, "Stock of 40 should allow " + (40 / batch) + " trades, maxUses=" + sellWheat.getMaxUses());

            ItemStack bought = trade(player, new ItemStack(Items.EMERALD, sellWheat.getCostA().getCount()));

            helper.assertTrue(bought.is(Items.WHEAT) && bought.getCount() == batch, "Player should receive " + batch + " wheat, got " + bought);
            helper.assertTrue(Economy.count(helper.getLevel(), farmer, Items.WHEAT) == 40 - batch, "Wheat should leave the farmer's stock");
            helper.assertTrue(Economy.emeralds(helper.getLevel(), farmer) == Economy.STARTING_PURSE + sellWheat.getCostA().getCount(),
                    "Farmer should keep the emerald it was paid");
        });
    }

    public static void noviceFarmerOnlyBuysWoodenHoes(GameTestHelper helper) {
        Villager farmer = spawnFarmer(helper);
        ServerPlayer player = player(helper);

        openTrades(player, farmer);

        helper.assertTrue(offerFor(farmer, Items.WOODEN_HOE).isPresent(), "A novice should buy a wooden hoe, offers: " + describe(farmer));
        helper.assertTrue(offerFor(farmer, Items.STONE_HOE).isEmpty(), "A novice should not buy a stone hoe yet");
        helper.assertTrue(offerFor(farmer, Items.IRON_HOE).isEmpty(), "A novice should not buy an iron hoe yet");
        helper.succeed();
    }

    public static void journeymanFarmerBuysUpToIronHoes(GameTestHelper helper) {
        Villager farmer = spawnFarmer(helper);
        farmer.setVillagerData(farmer.getVillagerData().withLevel(3));
        ServerPlayer player = player(helper);

        openTrades(player, farmer);

        helper.assertTrue(offerFor(farmer, Items.WOODEN_HOE).isPresent(), "Earlier tiers stay available, offers: " + describe(farmer));
        helper.assertTrue(offerFor(farmer, Items.STONE_HOE).isPresent(), "A journeyman should buy a stone hoe");
        helper.assertTrue(offerFor(farmer, Items.IRON_HOE).isPresent(), "A journeyman should buy an iron hoe");
        helper.assertTrue(offerFor(farmer, Items.DIAMOND_HOE).isEmpty(), "A journeyman should not buy a diamond hoe yet");
        helper.succeed();
    }

    public static void farmerKeepsTheEnchantmentsOfAHoeItBuys(GameTestHelper helper) {
        Villager farmer = spawnFarmer(helper);
        farmer.setVillagerData(farmer.getVillagerData().withLevel(3));
        ServerPlayer player = player(helper);
        ItemStack enchanted = new ItemStack(Items.IRON_HOE);
        var unbreaking = helper.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING);
        enchanted.enchant(unbreaking, 3);
        openTrades(player, farmer);

        trade(player, enchanted);

        ItemStack owned = farmer.getInventory().getItems().stream().filter(Hoes::isHoe).findFirst().orElse(ItemStack.EMPTY);
        helper.assertTrue(owned.is(Items.IRON_HOE), "Farmer should own the iron hoe, has " + owned);
        helper.assertTrue(net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(unbreaking, owned) == 3,
                "Farmer's hoe should keep Unbreaking III, has " + owned.getEnchantments());
        helper.succeed();
    }

    public static void farmerBuysBoneMealInBatchesOfEightUpToThirtyTwo(GameTestHelper helper) {
        Villager farmer = spawnFarmer(helper);
        farmer.getInventory().addItem(new ItemStack(Items.IRON_HOE));
        farmer.getInventory().addItem(new ItemStack(Items.EMERALD, 4));
        ServerPlayer player = player(helper);

        openTrades(player, farmer);
        MerchantOffer boneMeal = offerFor(farmer, Items.BONE_MEAL).orElseThrow(() ->
                helper.assertionException("A farmer should buy bone meal, offers: " + describe(farmer)));
        helper.assertTrue(boneMeal.getCostA().getCount() == 8, "Bone meal is bought in batches of 8, not " + boneMeal.getCostA().getCount());
        helper.assertTrue(boneMeal.getMaxUses() == 4, "With none in stock it can take 4 batches (32), maxUses=" + boneMeal.getMaxUses());

        trade(player, new ItemStack(Items.BONE_MEAL, 8));
        helper.assertTrue(farmer.getInventory().countItem(Items.BONE_MEAL) == 8, "The farmer should now have the 8 bone meal");
        player.closeContainer();

        farmer.getInventory().addItem(new ItemStack(Items.BONE_MEAL, 16));
        openTrades(player, farmer);
        helper.assertTrue(offerFor(farmer, Items.BONE_MEAL).map(MerchantOffer::getMaxUses).orElse(0) == 1, "At 24 there is room for one more batch");
        player.closeContainer();

        farmer.getInventory().addItem(new ItemStack(Items.BONE_MEAL, 6));
        openTrades(player, farmer);
        helper.assertTrue(offerFor(farmer, Items.BONE_MEAL).isEmpty(), "At 30 another batch would pass 32, offers: " + describe(farmer));
        helper.succeed();
    }

    public static void cropsWithoutAPriceSellAtTheDefaultRate(GameTestHelper helper) {
        Villager farmer = spawnFarmer(helper);
        farmer.getInventory().addItem(new ItemStack(Items.IRON_HOE));
        farmer.getInventory().addItem(new ItemStack(Items.BONE_MEAL, 32));
        farmer.getInventory().addItem(new ItemStack(Items.SUGAR_CANE, 40));
        ServerPlayer player = player(helper);

        openTrades(player, farmer);

        MerchantOffer sugarCane = offerSelling(farmer, Items.SUGAR_CANE).orElseThrow(() ->
                helper.assertionException("Sugar cane has no price of its own but is a crop, so it should sell at the default rate: " + describe(farmer)));
        helper.assertTrue(sugarCane.getResult().getCount() == 20 && sugarCane.getMaxUses() == 2,
                "Default rate is 20 per emerald, so 40 sugar cane is 2 trades: " + describe(farmer));
        helper.succeed();
    }

    public static void aFarmerSellsPumpkinsFromJourneymanAndMelonSlicesFromExpert(GameTestHelper helper) {
        Villager farmer = spawnFarmer(helper);
        farmer.getInventory().addItem(new ItemStack(Items.IRON_HOE));
        farmer.getInventory().addItem(new ItemStack(Items.PUMPKIN, 8));
        farmer.getInventory().addItem(new ItemStack(Items.MELON_SLICE, 40));
        ServerPlayer player = player(helper);

        farmer.setVillagerData(farmer.getVillagerData().withLevel(2));
        openTrades(player, farmer);
        helper.assertTrue(offerSelling(farmer, Items.PUMPKIN).isEmpty() && offerSelling(farmer, Items.MELON_SLICE).isEmpty(),
                "An apprentice grows neither, so it sells neither: " + describe(farmer));
        player.closeContainer();

        farmer.setVillagerData(farmer.getVillagerData().withLevel(3));
        openTrades(player, farmer);
        helper.assertTrue(offerSelling(farmer, Items.PUMPKIN).isPresent(), "A journeyman sells pumpkins: " + describe(farmer));
        helper.assertTrue(offerSelling(farmer, Items.MELON_SLICE).isEmpty(), "...but no melons yet: " + describe(farmer));
        player.closeContainer();

        farmer.setVillagerData(farmer.getVillagerData().withLevel(4));
        openTrades(player, farmer);
        helper.assertTrue(offerSelling(farmer, Items.MELON_SLICE).isPresent(), "An expert sells melon slices: " + describe(farmer));
        helper.succeed();
    }

    public static void aFarmerBuysStemSeedsFromThePlayerWhenItGrowsThem(GameTestHelper helper) {
        Villager farmer = spawnFarmer(helper);
        farmer.getInventory().addItem(new ItemStack(Items.IRON_HOE));
        ServerPlayer player = player(helper);

        farmer.setVillagerData(farmer.getVillagerData().withLevel(2));
        openTrades(player, farmer);
        helper.assertTrue(offerFor(farmer, Items.PUMPKIN_SEEDS).isEmpty(), "An apprentice has no use for pumpkin seeds: " + describe(farmer));
        player.closeContainer();

        farmer.setVillagerData(farmer.getVillagerData().withLevel(3));
        openTrades(player, farmer);
        helper.assertTrue(offerFor(farmer, Items.PUMPKIN_SEEDS).isPresent(), "A journeyman buys pumpkin seeds: " + describe(farmer));
        helper.assertTrue(offerFor(farmer, Items.MELON_SEEDS).isEmpty(), "...but melon seeds only from expert: " + describe(farmer));
        player.closeContainer();

        farmer.setVillagerData(farmer.getVillagerData().withLevel(4));
        openTrades(player, farmer);
        helper.assertTrue(offerFor(farmer, Items.MELON_SEEDS).isPresent(), "An expert buys melon seeds: " + describe(farmer));
        helper.succeed();
    }

    public static void aSeedlessFarmerOffersToBuySeeds(GameTestHelper helper) {
        Villager farmer = spawnFarmer(helper);
        for (int x = 3; x <= 7; x++) {
            for (int z = 5; z <= 8; z++) helper.setBlock(new BlockPos(x, 0, z), x == 5 && z == 7 ? Blocks.WATER : Blocks.FARMLAND);
        }
        farmer.getInventory().addItem(new ItemStack(Items.IRON_HOE));
        ServerPlayer player = player(helper);

        openTrades(player, farmer);
        Optional<MerchantOffer> buySeeds = offerFor(farmer, Items.WHEAT_SEEDS);
        helper.assertTrue(buySeeds.isPresent() && buySeeds.get().getResult().is(Items.EMERALD), "A farmer with nothing to plant and farmland waiting offers to buy seeds, offers: " + describe(farmer));
        helper.assertTrue(offerFor(farmer, Items.POTATO).isPresent() && offerFor(farmer, Items.CARROT).isPresent() && offerFor(farmer, Items.BEETROOT_SEEDS).isPresent(),
                "...and potatoes, carrots and beetroot seeds, offers: " + describe(farmer));
        int count = buySeeds.get().getCostA().getCount();

        ItemStack paid = trade(player, new ItemStack(Items.WHEAT_SEEDS, count));

        helper.assertTrue(paid.is(Items.EMERALD), "The player is paid in emeralds, got " + paid);
        helper.assertTrue(farmer.getInventory().countItem(Items.WHEAT_SEEDS) == count, "The farmer now has seeds to plant, " + farmer.getInventory().countItem(Items.WHEAT_SEEDS));
        helper.succeed();
    }

    public static void aFarmerSellsSurplusSeedsThirtyTwoToAnEmerald(GameTestHelper helper) {
        Villager farmer = spawnFarmer(helper);
        farmer.getInventory().addItem(new ItemStack(Items.IRON_HOE));
        farmer.getInventory().addItem(new ItemStack(Items.WHEAT_SEEDS, 64));
        farmer.getInventory().addItem(new ItemStack(Items.WHEAT_SEEDS, 64));
        ServerPlayer player = player(helper);

        openTrades(player, farmer);

        Optional<MerchantOffer> seeds = offerSelling(farmer, Items.WHEAT_SEEDS);
        helper.assertTrue(seeds.isPresent() && seeds.get().getResult().getCount() == 32 && seeds.get().getCostA().getCount() == 1,
                "Surplus seeds sell 32 to an emerald, offers: " + describe(farmer));
        helper.succeed();
    }

    public static void farmerWithNothingToTradeShakesItsHead(GameTestHelper helper) {
        Villager farmer = spawnFarmer(helper);
        farmer.getInventory().addItem(new ItemStack(Items.IRON_HOE));
        farmer.getInventory().addItem(new ItemStack(Items.BONE_MEAL, 32));
        for (var seed : new net.minecraft.world.item.Item[]{Items.WHEAT_SEEDS, Items.BEETROOT_SEEDS, Items.POTATO, Items.CARROT}) farmer.getInventory().addItem(new ItemStack(seed, 16));
        ServerPlayer player = player(helper);

        openTrades(player, farmer);

        helper.assertTrue(farmer.getOffers().isEmpty(), "A farmer with a hoe and seeds to plant but no crops has nothing to trade, offers: " + describe(farmer));
        helper.assertTrue(!(player.containerMenu instanceof MerchantMenu), "No trade screen should open");
        helper.succeed();
    }

    private static Villager spawnFarmer(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(COMPOSTER, Blocks.COMPOSTER);
        Villager farmer = GameTestHelpers.spawnEmployed(helper, COMPOSTER.east(), COMPOSTER, VillagerProfession.FARMER, PoiTypes.FARMER);
        GameTestHelpers.giveStartingPurse(helper, farmer);
        return farmer;
    }

    @SuppressWarnings("removal")
    private static ServerPlayer player(GameTestHelper helper) {
        return helper.makeMockServerPlayerInLevel();
    }

    private static void openTrades(ServerPlayer player, Villager farmer) {
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.interactOn(farmer, InteractionHand.MAIN_HAND, farmer.position());
    }

    private static ItemStack trade(ServerPlayer player, ItemStack payment) {
        MerchantMenu menu = (MerchantMenu) player.containerMenu;
        menu.getSlot(0).set(payment);
        menu.clicked(2, 0, ContainerInput.PICKUP, player);
        return menu.getCarried().copy();
    }

    private static Optional<MerchantOffer> offerFor(Villager farmer, net.minecraft.world.item.Item cost) {
        return farmer.getOffers().stream().filter(offer -> offer.getCostA().is(cost)).findFirst();
    }

    private static Optional<MerchantOffer> offerSelling(Villager farmer, net.minecraft.world.item.Item result) {
        return farmer.getOffers().stream().filter(offer -> offer.getResult().is(result)).findFirst();
    }

    private static String describe(Villager farmer) {
        return farmer.getOffers().stream().map(o -> o.getCostA() + " -> " + o.getResult() + " x" + o.getMaxUses()).toList().toString();
    }
}
