package com.sheepfromheaven.minenow.cartographer;

import com.sheepfromheaven.minenow.MineNowConfig;
import com.sheepfromheaven.minenow.MineNowConfig.Profession;
import com.sheepfromheaven.minenow.gametest.VanillaOffers;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Items;

/** A cartographer switched off draws no map, and trades as vanilla's. */
public final class SwitchedCartographerTests {
    private SwitchedCartographerTests() {}

    public static void aSwitchedOffCartographerDrawsNoMap(GameTestHelper helper) {
        helper.assertTrue(!MineNowConfig.professionEnabled(Profession.CARTOGRAPHER), "The cartographer's environment switches the cartographer off");
        Villager cartographer = CartographerTests.spawn(helper, 1, true);
        CartographerTests.stock(cartographer);

        helper.runAfterDelay(400, () -> {
            helper.assertTrue(cartographer.getInventory().countItem(Items.FILLED_MAP) == 0 && cartographer.getInventory().countItem(Items.PAPER) == 8,
                    "A switched-off cartographer draws no map, paper left " + cartographer.getInventory().countItem(Items.PAPER));
            helper.succeed();
        });
    }

    public static void aSwitchedOffCartographerOffersOnlyVanillaTrades(GameTestHelper helper) {
        Villager cartographer = CartographerTests.spawn(helper, 1, true);
        VanillaOffers.assertVanilla(helper, cartographer);
        helper.succeed();
    }
}
