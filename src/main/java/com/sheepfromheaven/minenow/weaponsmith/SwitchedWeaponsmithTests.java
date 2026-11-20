package com.sheepfromheaven.minenow.weaponsmith;

import com.sheepfromheaven.minenow.MineNowConfig.Profession;
import com.sheepfromheaven.minenow.gametest.SwitchedTrades;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** A weaponsmith switched off crafts, grinds and racks nothing, and trades the vanilla way. */
public final class SwitchedWeaponsmithTests {
    private SwitchedWeaponsmithTests() {}

    public static void aSwitchedOffWeaponsmithCraftsNothing(GameTestHelper helper) {
        Villager smith = WeaponsmithTests.spawnWeaponsmith(helper, 1);
        smith.getInventory().addItem(new ItemStack(Items.COBBLESTONE, 4));
        smith.getInventory().addItem(new ItemStack(Items.STICK, 2));
        helper.runAfterDelay(400, () -> {
            helper.assertTrue(smith.getInventory().countItem(Items.STONE_SWORD) == 0, "Expected no sword crafted");
            helper.assertTrue(smith.getInventory().countItem(Items.COBBLESTONE) == 4 && smith.getInventory().countItem(Items.STICK) == 2, "Expected the materials untouched");
            helper.succeed();
        });
    }

    public static void aSwitchedOffWeaponsmithGrindsNoWornBlades(GameTestHelper helper) {
        Villager smith = WeaponsmithTests.spawnWeaponsmith(helper, 1);
        smith.getInventory().addItem(worn());
        smith.getInventory().addItem(worn());
        boolean[] ground = {false};
        helper.onEachTick(() -> ground[0] |= smith.getBrain().getRunningBehaviors().stream().anyMatch(RestoreWorn.class::isInstance));
        helper.runAfterDelay(400, () -> {
            helper.assertTrue(!ground[0], "Expected RestoreWorn to stay out");
            helper.assertTrue(smith.getInventory().countItem(Items.IRON_SWORD) == 2, "Expected both worn swords kept, has " + smith.getInventory().countItem(Items.IRON_SWORD));
            helper.succeed();
        });
    }

    public static void aSwitchedOffWeaponsmithRacksNoSword(GameTestHelper helper) {
        Villager smith = WeaponsmithTests.spawnWeaponsmith(helper, 2);
        smith.getInventory().addItem(new ItemStack(Items.IRON_SWORD));
        smith.getInventory().addItem(new ItemStack(Items.ITEM_FRAME, 3));
        for (int z = 9; z <= 15; z++) {
            for (int y = 1; y <= 3; y++) helper.setBlock(new BlockPos(8, y, z), Blocks.STONE);
        }
        helper.runAfterDelay(600, () -> {
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemFrame.class, helper.getBounds()).isEmpty(), "Expected no frame hung");
            helper.assertTrue(smith.getInventory().countItem(Items.IRON_SWORD) == 1 && smith.getInventory().countItem(Items.ITEM_FRAME) == 3, "Expected its goods kept");
            helper.succeed();
        });
    }

    public static void aSwitchedOffWeaponsmithShowsVanillaTrades(GameTestHelper helper) {
        SwitchedTrades.assertVanillaTrades(helper, Profession.WEAPONSMITH, WeaponsmithTests.spawnWeaponsmith(helper, 1));
        helper.succeed();
    }

    private static ItemStack worn() {
        ItemStack stack = new ItemStack(Items.IRON_SWORD);
        stack.setDamageValue(200);
        return stack;
    }
}
