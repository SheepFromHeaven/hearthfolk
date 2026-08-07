package com.sheepfromheaven.minenow.storage.gametest;

import com.sheepfromheaven.minenow.storage.inventory.ModMenus;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Items;

// Registered into the vanilla test_function registry by MineNow's RegisterEvent listener;
// the matching data/minenow/test_instance/*.json file supplies the (small, room_5x4x5) structure.
public class VanillaRowMenuTests {

    /**
     * Reproduces the "shift-clicking a tool in the inhabitant's own inventory slots sends it to the
     * player's inventory instead of equipping it" bug: it should land in the (empty) tool/main-hand
     * slot first, only falling back to the player's inventory once that slot is occupied.
     */
    public static void shiftClickingToolInGeneralSlotsEquipsMainHandFirst(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();

        var toolContainer = new SimpleContainer(1);
        var generalInventory = new SimpleContainer(2);
        generalInventory.setItem(0, Items.IRON_AXE.getDefaultInstance());

        var menu = new com.sheepfromheaven.minenow.storage.inventory.VanillaRowMenu(
                ModMenus.INHABITANT_INVENTORY_TYPE.get(), 1, player.getInventory(),
                new Container[] {toolContainer}, generalInventory, 2, 1);

        // Slot 0 is the tool slot; slot 1 is the first general-grid slot (holding the axe).
        menu.quickMoveStack(player, 1);

        helper.assertTrue(toolContainer.getItem(0).is(Items.IRON_AXE),
                "Shift-clicking the axe should move it into the empty tool slot");
        helper.assertTrue(generalInventory.getItem(0).isEmpty(),
                "The axe should have left the general inventory slot");

        helper.succeed();
    }

    /**
     * Reproduces the "shift-right-clicking a netherite axe from the player's own inventory into the
     * lumberjack's inventory lands it in the first general slot instead of the equipment one" bug:
     * shift-clicking a tool IN from the player's inventory should equip the empty tool slot first,
     * mirroring the fix for shift-clicking a tool back OUT of the general grid.
     */
    public static void shiftClickingToolFromPlayerInventoryEquipsMainHandFirst(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();

        var toolContainer = new SimpleContainer(1);
        var generalInventory = new SimpleContainer(2);

        var menu = new com.sheepfromheaven.minenow.storage.inventory.VanillaRowMenu(
                ModMenus.INHABITANT_INVENTORY_TYPE.get(), 1, player.getInventory(),
                new Container[] {toolContainer}, generalInventory, 2, 1);

        // Container slots run tool(1) + general grid(2) + player main inventory (27, added before the
        // hotbar) + hotbar - so container index 3 is Inventory slot 9, the first main-inventory slot.
        player.getInventory().setItem(9, Items.NETHERITE_AXE.getDefaultInstance());
        int containerSlotForInventorySlotNine = 1 + 2;
        menu.quickMoveStack(player, containerSlotForInventorySlotNine);

        helper.assertTrue(toolContainer.getItem(0).is(Items.NETHERITE_AXE),
                "Shift-clicking the axe in should land it in the empty tool/equipment slot");
        helper.assertTrue(generalInventory.getItem(0).isEmpty() && generalInventory.getItem(1).isEmpty(),
                "The axe should not have landed in the general inventory grid");

        helper.succeed();
    }
}
