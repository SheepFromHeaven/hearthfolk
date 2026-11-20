package com.sheepfromheaven.minenow.librarian;

import com.sheepfromheaven.minenow.MineNowConfig;
import com.sheepfromheaven.minenow.MineNowConfig.Profession;
import com.sheepfromheaven.minenow.gametest.VanillaOffers;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** A librarian switched off makes no paper and binds no books, and trades as vanilla's. */
public final class SwitchedLibrarianTests {
    private SwitchedLibrarianTests() {}

    public static void aSwitchedOffLibrarianBindsNoBooks(GameTestHelper helper) {
        helper.assertTrue(!MineNowConfig.professionEnabled(Profession.LIBRARIAN), "The librarian's environment switches the librarian off");
        Villager librarian = LibrarianTests.spawnLibrarian(helper, 1);
        librarian.getInventory().addItem(new ItemStack(Items.SUGAR_CANE, 3));
        librarian.getInventory().addItem(new ItemStack(Items.PAPER, 3));
        librarian.getInventory().addItem(new ItemStack(Items.LEATHER, 1));
        librarian.getInventory().addItem(new ItemStack(Items.BOOK, 1));
        librarian.getInventory().addItem(new ItemStack(Items.LAPIS_LAZULI, 4));

        helper.runAfterDelay(400, () -> {
            var goods = librarian.getInventory();
            helper.assertTrue(goods.countItem(Items.SUGAR_CANE) == 3 && goods.countItem(Items.PAPER) == 3 && goods.countItem(Items.LEATHER) == 1,
                    "Expected no paper or book to be made, it has " + goods.countItem(Items.PAPER) + " paper, " + goods.countItem(Items.SUGAR_CANE) + " cane");
            helper.assertTrue(goods.countItem(Items.BOOK) == 1 && goods.countItem(Items.LAPIS_LAZULI) == 4 && goods.countItem(Items.ENCHANTED_BOOK) == 0,
                    "Expected no enchanted book, it has " + goods.countItem(Items.ENCHANTED_BOOK));
            helper.succeed();
        });
    }

    public static void aSwitchedOffLibrarianOffersOnlyVanillaTrades(GameTestHelper helper) {
        Villager librarian = LibrarianTests.spawnLibrarian(helper, 1);
        java.util.List<String> vanilla = VanillaOffers.assertVanilla(helper, librarian);
        librarian.getInventory().addItem(Librarian.enchantedBook(helper.getLevel(), helper.getLevel().getRandom(), 3));
        helper.assertTrue(vanilla.equals(VanillaOffers.assertVanilla(helper, librarian)),
                "Expected no offer for a book it holds, and the same vanilla trades, got " + VanillaOffers.assertVanilla(helper, librarian));
        helper.succeed();
    }
}
