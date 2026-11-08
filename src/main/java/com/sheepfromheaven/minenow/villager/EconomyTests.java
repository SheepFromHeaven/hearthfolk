package com.sheepfromheaven.minenow.villager;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Blocks;

import java.util.Optional;

public final class EconomyTests {
    private static final BlockPos SMOKER = new BlockPos(4, 1, 12);

    private EconomyTests() {}

    public static void startingPurseComesWithTheJob(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(new BlockPos(10, 1, 12), Blocks.COMPOSTER);
        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(13, 1, 12));
        ServerPlayer player = player(helper);
        helper.assertTrue(Economy.emeralds(helper.getLevel(), villager) == 0, "No purse before it has a job");

        helper.succeedWhen(() -> {
            helper.assertTrue(villager.getVillagerData().profession().is(VillagerProfession.FARMER), "The villager should take the free composter");
            helper.assertTrue(Economy.emeralds(helper.getLevel(), villager) == Economy.STARTING_PURSE,
                    "Taking a job comes with the starting purse, has " + Economy.emeralds(helper.getLevel(), villager));
            openTrades(player, villager);
            player.closeContainer();
            helper.assertTrue(Economy.emeralds(helper.getLevel(), villager) == Economy.STARTING_PURSE, "Opening its trades adds nothing more");
        });
    }

    public static void villagerKeepsFoodAndToolMoneyWhenBuyingFromPlayers(GameTestHelper helper) {
        Villager butcher = spawnButcher(helper);
        butcher.getInventory().addItem(new ItemStack(Items.STONE_AXE));
        butcher.getInventory().addItem(new ItemStack(Items.EMERALD, 5));
        ServerPlayer player = player(helper);
        int reserve = Economy.FOOD_MONEY + 1;

        openTrades(player, butcher);
        MerchantOffer coal = offerFor(butcher, Items.COAL).orElseThrow(() -> helper.assertionException("The butcher should buy coal, offers " + describe(butcher)));
        helper.assertTrue(coal.getMaxUses() == 2, "It spends only what's above its food money and the price of a new axe, " + coal.getMaxUses() + " trades offered");
        trade(player, new ItemStack(Items.COAL, coal.getCostA().getCount()));
        trade(player, new ItemStack(Items.COAL, coal.getCostA().getCount()));

        helper.assertTrue(Economy.emeralds(helper.getLevel(), butcher) == reserve, "It keeps " + reserve + " emeralds back, has " + Economy.emeralds(helper.getLevel(), butcher));
        butcher.getOffers().stream().filter(offer -> offer.getResult().is(Items.EMERALD)).forEach(offer ->
                helper.assertTrue(offer.isOutOfStock(), "Once it's down to its reserve it buys nothing else, still buys " + offer.getCostA()));
        openTrades(player, butcher);
        helper.assertTrue(butcher.getOffers().stream().noneMatch(offer -> offer.getResult().is(Items.EMERALD)), "...nor the next time you open its trades, " + describe(butcher));
        helper.succeed();
    }

    public static void villagerWithoutAToolKeepsItsFoodMoney(GameTestHelper helper) {
        Villager butcher = spawnButcher(helper);
        butcher.getInventory().addItem(new ItemStack(Items.EMERALD, Economy.FOOD_MONEY));
        ServerPlayer player = player(helper);

        openTrades(player, butcher);
        helper.assertTrue(offerFor(butcher, Items.STONE_AXE).isEmpty(), "With only food money left it doesn't buy an axe, " + describe(butcher));
        butcher.getInventory().addItem(new ItemStack(Items.EMERALD, 1));
        openTrades(player, butcher);
        helper.assertTrue(offerFor(butcher, Items.STONE_AXE).isPresent(), "One emerald more and it buys one, " + describe(butcher));
        helper.succeed();
    }

    private static Villager spawnButcher(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(SMOKER, Blocks.SMOKER);
        return GameTestHelpers.spawnEmployed(helper, SMOKER.east(), SMOKER, VillagerProfession.BUTCHER, PoiTypes.BUTCHER);
    }

    @SuppressWarnings("removal")
    private static ServerPlayer player(GameTestHelper helper) {
        return helper.makeMockServerPlayerInLevel();
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

    private static Optional<MerchantOffer> offerFor(Villager villager, Item cost) {
        return villager.getOffers().stream().filter(offer -> offer.getCostA().is(cost)).findFirst();
    }

    private static String describe(Villager villager) {
        return villager.getOffers().stream().map(o -> o.getCostA() + " -> " + o.getResult() + " x" + (o.getMaxUses() - o.getUses())).toList().toString();
    }
}
