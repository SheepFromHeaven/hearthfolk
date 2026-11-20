package com.sheepfromheaven.minenow.armorer;

import com.sheepfromheaven.minenow.MineNowConfig.Profession;
import com.sheepfromheaven.minenow.gametest.SwitchedTrades;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** An armorer switched off crafts and dresses nothing, its earlier displays stay its own, and it trades the vanilla way. */
public final class SwitchedArmorerTests {
    private static final BlockPos STAND = new BlockPos(9, 1, 12);

    private SwitchedArmorerTests() {}

    public static void aSwitchedOffArmorerCraftsNothing(GameTestHelper helper) {
        Villager armorer = ArmorerTests.spawnArmorer(helper);
        armorer.getInventory().addItem(new ItemStack(Items.COPPER_INGOT, 5));
        armorer.getInventory().addItem(new ItemStack(Items.OAK_PLANKS, 6));
        armorer.getInventory().addItem(new ItemStack(Items.IRON_INGOT, 2));
        helper.runAfterDelay(400, () -> {
            helper.assertTrue(armorer.getInventory().countItem(Items.COPPER_HELMET) == 0 && armorer.getInventory().countItem(Items.SHIELD) == 0,
                    "Expected no armor or shield crafted");
            helper.assertTrue(armorer.getInventory().countItem(Items.COPPER_INGOT) == 5 && armorer.getInventory().countItem(Items.OAK_PLANKS) == 6,
                    "Expected the materials untouched");
            helper.succeed();
        });
    }

    public static void aSwitchedOffArmorerDressesNoArmorStand(GameTestHelper helper) {
        Villager armorer = ArmorerTests.spawnArmorer(helper);
        armorer.getInventory().addItem(new ItemStack(Items.IRON_CHESTPLATE));
        ArmorStand stand = helper.spawn(EntityTypes.ARMOR_STAND, STAND);
        helper.runAfterDelay(600, () -> {
            helper.assertTrue(stand.getItemBySlot(EquipmentSlot.CHEST).isEmpty(), "Expected the stand bare, it wears " + stand.getItemBySlot(EquipmentSlot.CHEST));
            helper.assertTrue(armorer.getInventory().countItem(Items.IRON_CHESTPLATE) == 1, "Expected the chestplate kept");
            helper.succeed();
        });
    }

    /** A stand dressed before the switch went off: the armorer doesn't swap its armor any more, and players still can't take it. */
    public static void aSwitchedOffArmorersEarlierDisplayStaysLocked(GameTestHelper helper) {
        Villager armorer = ArmorerTests.spawnArmorer(helper);
        armorer.getInventory().addItem(new ItemStack(Items.DIAMOND_CHESTPLATE));
        ArmorStand stand = helper.spawn(EntityTypes.ARMOR_STAND, STAND);
        stand.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        stand.setData(Armorer.DISPLAYED_BY, armorer.getUUID());
        @SuppressWarnings("removal")
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        helper.runAfterDelay(400, () -> {
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.interactOn(stand, InteractionHand.MAIN_HAND, stand.position().add(0, 1.2, 0));
            helper.assertTrue(stand.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE), "Expected no swap and no taking, it wears " + stand.getItemBySlot(EquipmentSlot.CHEST));
            helper.assertTrue(armorer.getInventory().countItem(Items.DIAMOND_CHESTPLATE) == 1, "Expected the better piece kept in its goods");
            helper.succeed();
        });
    }

    public static void aSwitchedOffArmorerShowsVanillaTrades(GameTestHelper helper) {
        SwitchedTrades.assertVanillaTrades(helper, Profession.ARMORER, ArmorerTests.spawnArmorer(helper));
        helper.succeed();
    }
}
