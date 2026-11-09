package com.sheepfromheaven.minenow.armorer;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

public final class ArmorerTests {
    private static final BlockPos ANVIL = new BlockPos(5, 1, 12);
    private static final BlockPos STAND = new BlockPos(9, 1, 12);

    private ArmorerTests() {}

    public static void aNoviceArmorerMakesCopperArmor(GameTestHelper helper) {
        Villager armorer = spawnArmorer(helper);
        armorer.getInventory().addItem(new ItemStack(Items.COPPER_INGOT, 5));

        helper.succeedWhen(() -> helper.assertTrue(armorer.getInventory().countItem(Items.COPPER_HELMET) == 1,
                "Five copper make a helmet at the anvil, has " + armorer.getInventory().countItem(Items.COPPER_HELMET)));
    }

    public static void anApprenticeArmorerMakesShieldsFromAnyPlanks(GameTestHelper helper) {
        Villager armorer = spawnArmorer(helper);
        armorer.setVillagerData(armorer.getVillagerData().withLevel(2));
        armorer.getInventory().addItem(new ItemStack(Items.SPRUCE_PLANKS, 6));
        armorer.getInventory().addItem(new ItemStack(Items.IRON_INGOT, 1));

        helper.succeedWhen(() -> helper.assertTrue(armorer.getInventory().countItem(Items.SHIELD) == 1,
                "Six planks of any wood and an iron ingot make a shield, has " + armorer.getInventory().countItem(Items.SHIELD)));
    }

    public static void armorerDressesAnArmorStandWithItsArmor(GameTestHelper helper) {
        Villager armorer = spawnArmorer(helper);
        armorer.getInventory().addItem(new ItemStack(Items.IRON_CHESTPLATE));
        ArmorStand stand = helper.spawn(EntityTypes.ARMOR_STAND, STAND);

        helper.succeedWhen(() -> {
            helper.assertTrue(stand.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE), "The armorer shows its chestplate on the stand, it wears " + stand.getItemBySlot(EquipmentSlot.CHEST));
            helper.assertTrue(armorer.getInventory().countItem(Items.IRON_CHESTPLATE) == 0, "...taking it from its goods");
        });
    }

    public static void armorerShowsItsBestArmor(GameTestHelper helper) {
        Villager armorer = spawnArmorer(helper);
        ArmorStand stand = helper.spawn(EntityTypes.ARMOR_STAND, STAND);
        armorer.getInventory().addItem(new ItemStack(Items.IRON_HELMET));

        helper.runAfterDelay(400, () -> {
            helper.assertTrue(stand.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET), "First it shows the iron helmet, it wears " + stand.getItemBySlot(EquipmentSlot.HEAD));
            armorer.getInventory().addItem(new ItemStack(Items.DIAMOND_HELMET));
            helper.succeedWhen(() -> {
                helper.assertTrue(stand.getItemBySlot(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET), "A better helmet takes its place, it wears " + stand.getItemBySlot(EquipmentSlot.HEAD));
                helper.assertTrue(armorer.getInventory().countItem(Items.IRON_HELMET) == 1, "...and the iron one goes back to its goods");
            });
        });
    }

    public static void playersCannotTakeTheArmorOffAnArmorersStand(GameTestHelper helper) {
        Villager armorer = spawnArmorer(helper);
        armorer.getInventory().addItem(new ItemStack(Items.IRON_CHESTPLATE));
        ArmorStand stand = helper.spawn(EntityTypes.ARMOR_STAND, STAND);
        ServerPlayer player = survivalPlayer(helper);

        helper.succeedWhen(() -> {
            helper.assertTrue(stand.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE), "The armorer dresses the stand first");
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.interactOn(stand, InteractionHand.MAIN_HAND, stand.position().add(0, 1.2, 0));
            helper.assertTrue(stand.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE), "A player can't take the armor off an armorer's stand");
        });
    }

    public static void breakingAnArmorersStandCostsNothingAndReturnsItsArmor(GameTestHelper helper) {
        Villager armorer = spawnArmorer(helper);
        armorer.getInventory().addItem(new ItemStack(Items.IRON_CHESTPLATE));
        ArmorStand stand = helper.spawn(EntityTypes.ARMOR_STAND, STAND);
        ServerPlayer player = survivalPlayer(helper);
        boolean[] broken = {false};

        helper.succeedWhen(() -> {
            if (!broken[0]) {
                helper.assertTrue(stand.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE), "The armorer dresses the stand first");
                player.attack(stand);
                player.attack(stand);
                broken[0] = true;
            }
            helper.assertFalse(stand.isAlive(), "A player can still break the stand");
            int reputation = armorer.getGossips().getReputation(player.getUUID(), type -> true);
            helper.assertTrue(reputation == 0, "Moving a display isn't griefing, reputation " + reputation);
            helper.assertTrue(armorer.getInventory().countItem(Items.IRON_CHESTPLATE) == 1, "The armor on display goes back to the armorer, has " + armorer.getInventory().countItem(Items.IRON_CHESTPLATE));
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, helper.getBounds(), item -> item.getItem().is(Items.IRON_CHESTPLATE)).isEmpty(),
                    "...rather than dropping for the player to take");
        });
    }

    private static ServerPlayer survivalPlayer(GameTestHelper helper) {
        @SuppressWarnings("removal")
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        return player;
    }

    static Villager spawnArmorer(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(ANVIL, Blocks.ANVIL);
        return GameTestHelpers.spawnEmployed(helper, ANVIL.east(), ANVIL, VillagerProfession.ARMORER, PoiTypes.ARMORER);
    }
}
