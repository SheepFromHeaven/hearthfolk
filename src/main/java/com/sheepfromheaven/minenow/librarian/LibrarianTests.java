package com.sheepfromheaven.minenow.librarian;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import com.sheepfromheaven.minenow.villager.Exports;
import com.sheepfromheaven.minenow.villager.ProfessionTrades;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;

public final class LibrarianTests {
    static final BlockPos LECTERN = new BlockPos(5, 1, 12);

    private LibrarianTests() {}

    public static void aLibrarianMakesPaperFromSugarCane(GameTestHelper helper) {
        Villager librarian = spawnLibrarian(helper, 1);
        librarian.getInventory().addItem(new ItemStack(Items.SUGAR_CANE, 3));

        helper.succeedWhen(() -> helper.assertTrue(librarian.getInventory().countItem(Items.PAPER) == 3,
                "Three sugar cane make three paper at its lectern, has " + librarian.getInventory().countItem(Items.PAPER) + " paper, " + librarian.getInventory().countItem(Items.SUGAR_CANE) + " cane"));
    }

    public static void aLibrarianBindsBooksFromPaperAndLeather(GameTestHelper helper) {
        Villager librarian = spawnLibrarian(helper, 1);
        librarian.getInventory().addItem(new ItemStack(Items.PAPER, 3));
        librarian.getInventory().addItem(new ItemStack(Items.LEATHER, 1));

        helper.succeedWhen(() -> helper.assertTrue(librarian.getInventory().countItem(Items.BOOK) == 1,
                "Three paper and a leather make a book, has " + librarian.getInventory().countItem(Items.BOOK) + " books, " + librarian.getInventory().countItem(Items.PAPER) + " paper"));
    }

    public static void aLibrarianEnchantsABookAtItsLectern(GameTestHelper helper) {
        Villager librarian = spawnLibrarian(helper, 1);
        librarian.getInventory().addItem(new ItemStack(Items.BOOK, 1));
        librarian.getInventory().addItem(new ItemStack(Items.LAPIS_LAZULI, 4));

        helper.succeedWhen(() -> {
            ItemStack book = enchantedBook(librarian);
            helper.assertTrue(!book.isEmpty(), "A book and lapis make an enchanted book, has " + librarian.getInventory().countItem(Items.BOOK) + " books, " + librarian.getInventory().countItem(Items.LAPIS_LAZULI) + " lapis");
            helper.assertTrue(!book.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty(), "...with enchantments stored in it");
            helper.assertTrue(librarian.getInventory().countItem(Items.BOOK) == 0 && librarian.getInventory().countItem(Items.LAPIS_LAZULI) < 4, "...and the book and some lapis are used up");
        });
    }

    public static void aLibrarianWithoutLapisLeavesItsBookPlain(GameTestHelper helper) {
        Villager librarian = spawnLibrarian(helper, 1);
        librarian.getInventory().addItem(new ItemStack(Items.BOOK, 1));

        helper.runAfterDelay(400, () -> {
            helper.assertTrue(enchantedBook(librarian).isEmpty(), "No lapis, no enchanting");
            helper.assertTrue(librarian.getInventory().countItem(Items.BOOK) == 1, "...the book stays a book");
            helper.succeed();
        });
    }

    public static void aNoviceLibrariansBooksNeverHoldTreasureEnchantments(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        var tableEnchantments = registries.lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(EnchantmentTags.IN_ENCHANTING_TABLE);
        for (int roll = 0; roll < 300; roll++) {
            ItemStack book = Librarian.enchantedBook(helper.getLevel(), helper.getLevel().getRandom(), 1);
            helper.assertTrue(book.is(Items.ENCHANTED_BOOK), "Rolled an enchanted book, got " + book);
            for (var entry : book.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).keySet()) {
                helper.assertTrue(tableEnchantments.contains(entry), "A novice only binds what an enchanting table gives, not " + entry.getRegisteredName());
            }
        }
        helper.succeed();
    }

    public static void aMastersBooksCanHoldWhatOnlyTradersSell(GameTestHelper helper) {
        var tradeable = helper.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(EnchantmentTags.TRADEABLE);
        boolean[] mending = {false};
        for (int roll = 0; roll < 3000 && !mending[0]; roll++) {
            ItemStack book = Librarian.enchantedBook(helper.getLevel(), helper.getLevel().getRandom(), 5);
            for (var entry : book.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).keySet()) {
                helper.assertTrue(tradeable.contains(entry), "A master only binds tradeable enchantments, not " + entry.getRegisteredName());
                if (entry.is(net.minecraft.world.item.enchantment.Enchantments.MENDING)) mending[0] = true;
            }
        }
        helper.assertTrue(mending[0], "A master's books can hold mending");
        helper.succeed();
    }

    public static void aLibrarianSellsItsEnchantedBookWithItsEnchantments(GameTestHelper helper) {
        Villager librarian = spawnLibrarian(helper, 1);
        ItemStack book = Librarian.enchantedBook(helper.getLevel(), helper.getLevel().getRandom(), 3);
        librarian.getInventory().addItem(book.copy());
        @SuppressWarnings("removal")
        net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();

        var offers = ProfessionTrades.currentOffers(helper.getLevel(), librarian);
        MerchantOffer offer = offers.stream().filter(o -> o.getResult().is(Items.ENCHANTED_BOOK)).findFirst().orElse(null);
        helper.assertTrue(offer != null, "A player is offered its enchanted book, offers: " + offers.size());
        helper.assertTrue(offer.getResult().getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).equals(book.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY)),
                "...the very book it holds, enchantments and all, not a blank one");
        helper.assertTrue(offer.getCostA().is(Items.EMERALD) && offer.getCostA().getCount() >= 1, "...for emeralds");

        NeoForge.EVENT_BUS.post(new TradeWithVillagerEvent(player, offer, librarian));
        helper.assertTrue(enchantedBook(librarian).isEmpty(), "Selling it takes that book from the librarian's goods");
        helper.succeed();
    }

    public static void aLibrarianBuysSugarCaneAndLeatherFromPlayers(GameTestHelper helper) {
        Villager librarian = spawnLibrarian(helper, 1);
        helper.runAfterDelay(20, () -> {
            var offers = ProfessionTrades.currentOffers(helper.getLevel(), librarian);
            GameTestHelpers.giveStartingPurse(helper, librarian);
            offers = ProfessionTrades.currentOffers(helper.getLevel(), librarian);
            boolean cane = offers.stream().anyMatch(o -> o.getCostA().is(Items.SUGAR_CANE));
            boolean leather = offers.stream().anyMatch(o -> o.getCostA().is(Items.LEATHER));
            boolean lapis = offers.stream().anyMatch(o -> o.getCostA().is(Items.LAPIS_LAZULI));
            helper.assertTrue(cane && leather && lapis, "It buys sugar cane, leather and lapis from players, cane " + cane + ", leather " + leather + ", lapis " + lapis);
            helper.succeed();
        });
    }

    public static void aLibrarianLevelsUpThroughItsWork(GameTestHelper helper) {
        Villager librarian = spawnLibrarian(helper, 1);
        GameTestHelpers.allowWorkXp(librarian);
        librarian.getInventory().addItem(new ItemStack(Items.SUGAR_CANE, 3));

        helper.succeedWhen(() -> helper.assertTrue(librarian.getVillagerXp() > 0,
                "Making paper earns experience, xp " + librarian.getVillagerXp()));
    }

    public static void aLibrarianBuysSugarCaneFromATravellingMerchant(GameTestHelper helper) {
        Villager librarian = spawnLibrarian(helper, 1);
        librarian.getInventory().addItem(new ItemStack(Items.EMERALD, 20));
        var merchant = Exports.sendTrader(helper.getLevel(), librarian);
        helper.assertTrue(merchant != null, "A travelling merchant comes");

        helper.succeedWhen(() -> {
            int cane = librarian.getInventory().countItem(Items.SUGAR_CANE) + librarian.getInventory().countItem(Items.PAPER);
            helper.assertTrue(cane >= 8, "The librarian buys sugar cane from the merchant (and turns it into paper), cane and paper: " + cane);
            helper.assertTrue(librarian.getInventory().countItem(Items.EMERALD) < 20, "...and pays for it, has " + librarian.getInventory().countItem(Items.EMERALD) + " emeralds");
            helper.assertTrue(merchant.getOffers().stream().anyMatch(offer -> offer.getResult().is(Items.SUGAR_CANE) && offer.getUses() > 0), "...out of the merchant's stock");
        });
    }

    private static ItemStack enchantedBook(Villager librarian) {
        return librarian.getInventory().getItems().stream().filter(stack -> stack.is(Items.ENCHANTED_BOOK)).findFirst().orElse(ItemStack.EMPTY);
    }

    static Villager spawnLibrarian(GameTestHelper helper, int level) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(LECTERN, Blocks.LECTERN);
        Villager villager = GameTestHelpers.spawnEmployed(helper, LECTERN.east(), LECTERN, VillagerProfession.LIBRARIAN, PoiTypes.LIBRARIAN);
        villager.setVillagerData(villager.getVillagerData().withLevel(level));
        return villager;
    }
}
