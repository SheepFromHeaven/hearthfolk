package com.sheepfromheaven.minenow.villager;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Blocks;

import java.util.Map;
import java.util.stream.Stream;

/** {@code villagersPayEachOther} off: villagers take what they need from each other for free, players still pay and are paid. */
public final class PayEachOtherTests {
    private PayEachOtherTests() {}

    public static void withoutPaymentAButcherWithNoEmeraldsGetsWheatFromTheFarmer(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        BlockPos smoker = new BlockPos(4, 1, 12);
        helper.setBlock(smoker, Blocks.SMOKER);
        Villager butcher = GameTestHelpers.spawnEmployed(helper, smoker.east(), smoker, VillagerProfession.BUTCHER, PoiTypes.BUTCHER);
        butcher.getInventory().addItem(new ItemStack(Items.STONE_AXE));
        BlockPos composter = new BlockPos(19, 1, 12);
        helper.setBlock(composter, Blocks.COMPOSTER);
        Villager farmer = GameTestHelpers.spawnEmployed(helper, composter.west(), composter, VillagerProfession.FARMER, PoiTypes.FARMER);
        farmer.getInventory().addItem(new ItemStack(Items.WHEAT, 40));

        helper.succeedWhen(() -> {
            helper.assertTrue(butcher.getInventory().countItem(Items.WHEAT) >= 20, "A butcher with no emeralds takes the farmer's wheat, has " + butcher.getInventory().countItem(Items.WHEAT));
            helper.assertTrue(Economy.emeralds(helper.getLevel(), butcher) == 0 && Economy.emeralds(helper.getLevel(), farmer) == 0,
                    "...and no emeralds move, butcher " + Economy.emeralds(helper.getLevel(), butcher) + ", farmer " + Economy.emeralds(helper.getLevel(), farmer));
            helper.assertTrue(BuyFromVillagers.debt(butcher) == 0, "...nor does it owe anything, owes " + BuyFromVillagers.debt(butcher));
        });
    }

    public static void withoutPaymentABrokeVillagerCommissionsMissingArmorAndGetsIt(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        BlockPos anvil = new BlockPos(5, 1, 12);
        helper.setBlock(anvil, Blocks.ANVIL);
        Villager armorer = GameTestHelpers.spawnEmployed(helper, anvil.east(), anvil, VillagerProfession.ARMORER, PoiTypes.ARMORER);
        BlockPos furnace = new BlockPos(5, 1, 18);
        helper.setBlock(furnace, Blocks.BLAST_FURNACE);
        Villager smelter = GameTestHelpers.spawnEmployed(helper, furnace.east(), furnace, com.sheepfromheaven.minenow.smelter.Smelter.PROFESSION, PoiTypes.ARMORER);
        smelter.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        smelter.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
        smelter.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.LEATHER_LEGGINGS));
        smelter.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
        BlockPos composter = new BlockPos(19, 1, 12);
        helper.setBlock(composter, Blocks.COMPOSTER);
        Villager farmer = GameTestHelpers.spawnEmployed(helper, composter.west(), composter, VillagerProfession.FARMER, PoiTypes.FARMER);
        farmer.getInventory().addItem(new ItemStack(Items.WOODEN_HOE));
        farmer.getInventory().addItem(new ItemStack(Items.BREAD, 12));
        boolean[] freeOrder = {false};
        helper.onEachTick(() -> {
            if (freeOrder[0] || Commissions.orderFor(armorer, farmer).filter(order -> order.paid() == 0).isEmpty()) return;
            freeOrder[0] = true;
            smelter.getInventory().addItem(new ItemStack(Items.COPPER_INGOT, 64));
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(freeOrder[0], "A farmer with no emeralds commissions a piece nobody has in stock, paying nothing; orders " + armorer.getData(ModVillagers.COMMISSIONS));
            helper.assertTrue(Stream.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET).anyMatch(slot -> !farmer.getItemBySlot(slot).isEmpty()),
                    "...the armorer gets copper from the smelter, makes it and the farmer wears it; armorer has "
                            + armorer.getInventory().getItems().stream().filter(stack -> !stack.isEmpty()).toList());
            helper.assertTrue(Stream.of(farmer, armorer, smelter).allMatch(villager -> Economy.emeralds(helper.getLevel(), villager) == 0), "...and no emeralds move");
        });
    }

    public static void withoutPaymentABrokeSmeltersDebtsStayAsTheyAre(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        BlockPos blastFurnace = new BlockPos(5, 1, 12);
        helper.setBlock(blastFurnace, Blocks.BLAST_FURNACE);
        Villager smelter = GameTestHelpers.spawnEmployed(helper, blastFurnace.east(), blastFurnace, com.sheepfromheaven.minenow.smelter.Smelter.PROFESSION, PoiTypes.ARMORER);
        smelter.getInventory().addItem(new ItemStack(Items.EMERALD, Economy.FOOD_MONEY));
        smelter.getInventory().addItem(new ItemStack(Items.COAL, 4));
        BlockPos cart = new BlockPos(18, 1, 12);
        helper.setBlock(cart, com.sheepfromheaven.minenow.miner.Miner.FURNACE_CART.get());
        Villager miner = GameTestHelpers.spawnEmployed(helper, cart.west(), cart, com.sheepfromheaven.minenow.miner.Miner.PROFESSION, com.sheepfromheaven.minenow.miner.Miner.POI);
        miner.getInventory().addItem(new ItemStack(Items.WOODEN_PICKAXE));
        miner.getInventory().addItem(new ItemStack(Items.RAW_IRON, 40));
        smelter.setData(ModVillagers.DEBTS, Map.of(miner.getUUID(), 2));
        boolean[] gotOre = {false};
        helper.onEachTick(() -> {
            if (gotOre[0] || smelter.getInventory().countItem(Items.RAW_IRON) + smelter.getInventory().countItem(Items.IRON_INGOT) == 0) return;
            gotOre[0] = true;
            smelter.getInventory().addItem(new ItemStack(Items.EMERALD, 10));
        });

        helper.runAfterDelay(1200, () -> {
            helper.assertTrue(gotOre[0], "A broke smelter takes the miner's raw iron for free");
            helper.assertTrue(BuyFromVillagers.debt(smelter) == 2, "...its old debt neither grows nor is paid back, even with money to spare, owes " + BuyFromVillagers.debt(smelter));
            helper.assertTrue(Economy.emeralds(helper.getLevel(), miner) == 0, "...so the miner gets no emeralds, has " + Economy.emeralds(helper.getLevel(), miner));
            helper.succeed();
        });
    }

    public static void withoutPaymentAPlayerStillPaysAndIsPaid(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        BlockPos smoker = new BlockPos(4, 1, 12);
        helper.setBlock(smoker, Blocks.SMOKER);
        Villager butcher = GameTestHelpers.spawnEmployed(helper, smoker.east(), smoker, VillagerProfession.BUTCHER, PoiTypes.BUTCHER);
        butcher.getInventory().addItem(new ItemStack(Items.STONE_AXE));
        butcher.getInventory().addItem(new ItemStack(Items.PORKCHOP, 3));
        butcher.getInventory().addItem(new ItemStack(Items.COOKED_PORKCHOP, 20));
        butcher.getInventory().addItem(new ItemStack(Items.EMERALD, 5));
        @SuppressWarnings("removal")
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        openTrades(player, butcher);
        MerchantOffer meat = offer(butcher, Items.EMERALD, Items.COOKED_PORKCHOP);
        helper.assertTrue(meat != null, "The butcher sells cooked pork to a player, offers " + describe(butcher));
        int before = Economy.emeralds(helper.getLevel(), butcher);
        ItemStack bought = trade(player, new ItemStack(Items.EMERALD, meat.getCostA().getCount()));
        helper.assertTrue(bought.is(Items.COOKED_PORKCHOP), "The player gets the pork, got " + bought);
        helper.assertTrue(Economy.emeralds(helper.getLevel(), butcher) == before + meat.getCostA().getCount(),
                "...and pays its emeralds to the butcher, which has " + Economy.emeralds(helper.getLevel(), butcher) + " of " + before + " plus " + meat.getCostA().getCount());

        openTrades(player, butcher);
        MerchantOffer coal = offer(butcher, Items.COAL, Items.EMERALD);
        helper.assertTrue(coal != null, "The butcher buys coal from a player, offers " + describe(butcher));
        before = Economy.emeralds(helper.getLevel(), butcher);
        ItemStack paid = trade(player, new ItemStack(Items.COAL, coal.getCostA().getCount()));
        helper.assertTrue(paid.is(Items.EMERALD) && paid.getCount() == coal.getResult().getCount(), "The butcher pays the player emeralds for coal, got " + paid);
        helper.assertTrue(Economy.emeralds(helper.getLevel(), butcher) == before - paid.getCount(), "...out of its own emeralds, has " + Economy.emeralds(helper.getLevel(), butcher));
        helper.succeed();
    }

    private static void openTrades(ServerPlayer player, Villager villager) {
        player.closeContainer();
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.interactOn(villager, InteractionHand.MAIN_HAND, villager.position());
    }

    private static ItemStack trade(ServerPlayer player, ItemStack payment) {
        MerchantMenu menu = (MerchantMenu) player.containerMenu;
        menu.getSlot(0).set(payment);
        menu.clicked(2, 0, ContainerInput.PICKUP, player);
        ItemStack carried = menu.getCarried().copy();
        menu.setCarried(ItemStack.EMPTY);
        return carried;
    }

    private static MerchantOffer offer(Villager villager, net.minecraft.world.item.Item cost, net.minecraft.world.item.Item result) {
        return villager.getOffers().stream().filter(offer -> offer.getCostA().is(cost) && offer.getResult().is(result)).findFirst().orElse(null);
    }

    private static String describe(Villager villager) {
        return villager.getOffers().stream().map(o -> o.getCostA() + " -> " + o.getResult()).toList().toString();
    }
}
