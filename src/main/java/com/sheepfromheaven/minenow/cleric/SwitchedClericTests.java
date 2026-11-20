package com.sheepfromheaven.minenow.cleric;

import com.sheepfromheaven.minenow.MineNowConfig;
import com.sheepfromheaven.minenow.MineNowConfig.Profession;
import com.sheepfromheaven.minenow.gametest.VanillaOffers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potions;

/** A cleric switched off brews, heals and throws nothing, panics like any villager, and trades as vanilla's. */
public final class SwitchedClericTests {
    private SwitchedClericTests() {}

    public static void aSwitchedOffClericBrewsNothing(GameTestHelper helper) {
        helper.assertTrue(!MineNowConfig.professionEnabled(Profession.CLERIC), "The cleric's environment switches the cleric off");
        Villager cleric = ClericTests.spawnCleric(helper, 3);
        cleric.getInventory().addItem(new ItemStack(Items.GLASS_BOTTLE, 3));
        cleric.getInventory().addItem(new ItemStack(Items.NETHER_WART, 3));
        cleric.getInventory().addItem(new ItemStack(Items.BLAZE_POWDER, 2));

        helper.runAfterDelay(400, () -> {
            helper.assertTrue(!cleric.getInventory().hasAnyMatching(stack -> stack.is(Items.POTION)), "A switched-off cleric brews no potion");
            helper.assertTrue(cleric.getInventory().countItem(Items.GLASS_BOTTLE) == 3 && cleric.getInventory().countItem(Items.NETHER_WART) == 3,
                    "...and uses up none of its ingredients");
            helper.succeed();
        });
    }

    public static void aSwitchedOffClericSellsNoHealingToAHurtVillager(GameTestHelper helper) {
        Villager cleric = ClericTests.spawnCleric(helper, 3);
        cleric.getInventory().addItem(PotionGoods.stack(Items.POTION, Potions.HEALING));
        Villager patient = helper.spawn(EntityTypes.VILLAGER, new BlockPos(6, 1, 12));
        patient.getInventory().addItem(new ItemStack(Items.EMERALD, 5));
        patient.setHealth(5.0F);

        helper.runAfterDelay(400, () -> {
            helper.assertTrue(patient.getHealth() <= 5.5F && patient.getInventory().countItem(Items.EMERALD) == 5,
                    "A hurt villager is not healed or charged, health " + patient.getHealth());
            helper.assertTrue(PotionGoods.count(helper.getLevel(), cleric, Items.POTION, Potions.HEALING) == 1, "...and the cleric keeps its potion");
            helper.succeed();
        });
    }

    public static void aSwitchedOffClericIsUnarmedAndThrowsNothing(GameTestHelper helper) {
        Villager cleric = ClericTests.spawnCleric(helper, 4);
        cleric.getInventory().addItem(PotionGoods.stack(Items.SPLASH_POTION, Potions.HARMING));
        helper.assertTrue(!ClericFight.armed(cleric), "Splash potions do not make a switched-off cleric armed");
        var pillager = helper.spawnWithNoFreeWill(EntityTypes.PILLAGER, new BlockPos(18, 1, 12));
        float before = pillager.getHealth();

        helper.runAfterDelay(300, () -> {
            helper.assertTrue(pillager.getHealth() == before && cleric.getInventory().hasAnyMatching(stack -> stack.is(Items.SPLASH_POTION)),
                    "A switched-off cleric throws no splash potion, pillager health " + pillager.getHealth());
            helper.succeed();
        });
    }

    public static void aSwitchedOffClericOffersOnlyVanillaTrades(GameTestHelper helper) {
        Villager cleric = ClericTests.spawnCleric(helper, 3);
        cleric.getInventory().addItem(PotionGoods.stack(Items.POTION, Potions.HEALING));
        for (var offer : VanillaOffers.assertVanilla(helper, cleric)) {
            helper.assertTrue(!offer.endsWith("minecraft:potion"), "Expected none of Hearthfolk's potions, got " + offer);
        }
        helper.succeed();
    }
}
