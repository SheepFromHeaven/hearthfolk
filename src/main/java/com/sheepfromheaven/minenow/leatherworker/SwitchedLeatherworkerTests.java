package com.sheepfromheaven.minenow.leatherworker;

import com.sheepfromheaven.minenow.MineNowConfig.Profession;
import com.sheepfromheaven.minenow.gametest.SwitchedTrades;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;

/** A leatherworker switched off crafts, tends, dyes, racks and dresses nothing, and trades the vanilla way. */
public final class SwitchedLeatherworkerTests {
    private SwitchedLeatherworkerTests() {}

    public static void aSwitchedOffLeatherworkerCraftsNothing(GameTestHelper helper) {
        Villager leatherworker = LeatherworkerTests.spawnLeatherworker(helper, 1);
        leatherworker.getInventory().addItem(new ItemStack(Items.RABBIT_HIDE, 8));
        leatherworker.getInventory().addItem(new ItemStack(Items.BONE, 4));
        helper.runAfterDelay(400, () -> {
            helper.assertTrue(leatherworker.getInventory().countItem(Items.LEATHER) == 0 && leatherworker.getInventory().countItem(Items.BONE_MEAL) == 0,
                    "Expected no leather or bone meal made");
            helper.assertTrue(leatherworker.getInventory().countItem(Items.RABBIT_HIDE) == 8 && leatherworker.getInventory().countItem(Items.BONE) == 4,
                    "Expected the materials untouched");
            helper.succeed();
        });
    }

    public static void aSwitchedOffLeatherworkerBoneMealsNoFlower(GameTestHelper helper) {
        Villager leatherworker = LeatherworkerTests.spawnLeatherworker(helper, 1);
        leatherworker.getInventory().addItem(new ItemStack(Items.BONE_MEAL, 4));
        BlockPos bush = new BlockPos(10, 1, 14);
        helper.setBlock(bush.below(), Blocks.GRASS_BLOCK);
        DoublePlantBlock.placeAt(helper.getLevel(), Blocks.ROSE_BUSH.defaultBlockState(), helper.absolutePos(bush), 3);
        helper.runAfterDelay(600, () -> {
            helper.assertTrue(leatherworker.getInventory().countItem(Items.BONE_MEAL) == 4, "Expected its bone meal kept");
            helper.assertTrue(leatherworker.getInventory().countItem(Items.ROSE_BUSH) == 0 && leatherworker.getInventory().countItem(Items.DYE.red()) == 0, "Expected no flower or dye gained");
            helper.succeed();
        });
    }

    public static void aSwitchedOffLeatherworkerDyesNoArmor(GameTestHelper helper) {
        Villager leatherworker = LeatherworkerTests.spawnLeatherworker(helper, 1);
        leatherworker.getInventory().addItem(new ItemStack(Items.LEATHER_CHESTPLATE));
        leatherworker.getInventory().addItem(new ItemStack(Items.DYE.red()));
        helper.runAfterDelay(400, () -> {
            ItemStack tunic = leatherworker.getInventory().getItems().stream().filter(stack -> stack.is(Items.LEATHER_CHESTPLATE)).findFirst().orElse(ItemStack.EMPTY);
            helper.assertTrue(!tunic.isEmpty() && !tunic.has(DataComponents.DYED_COLOR), "Expected the tunic left plain, " + tunic.getComponents());
            helper.assertTrue(leatherworker.getInventory().countItem(Items.DYE.red()) == 1, "Expected the dye kept");
            helper.succeed();
        });
    }

    public static void aSwitchedOffLeatherworkerDressesNoArmorStand(GameTestHelper helper) {
        Villager leatherworker = LeatherworkerTests.spawnLeatherworker(helper, 1);
        leatherworker.getInventory().addItem(new ItemStack(Items.LEATHER_BOOTS));
        ArmorStand stand = helper.spawn(EntityTypes.ARMOR_STAND, new BlockPos(9, 1, 12));
        helper.runAfterDelay(600, () -> {
            helper.assertTrue(stand.getItemBySlot(EquipmentSlot.FEET).isEmpty(), "Expected the stand bare, it wears " + stand.getItemBySlot(EquipmentSlot.FEET));
            helper.assertTrue(leatherworker.getInventory().countItem(Items.LEATHER_BOOTS) == 1, "Expected the boots kept");
            helper.succeed();
        });
    }

    public static void aSwitchedOffLeatherworkerHangsNoTanningRack(GameTestHelper helper) {
        Villager leatherworker = LeatherworkerTests.spawnLeatherworker(helper, 2);
        leatherworker.getInventory().addItem(new ItemStack(Items.ITEM_FRAME, 4));
        leatherworker.getInventory().addItem(new ItemStack(Items.LEATHER, 8));
        for (int z = 9; z <= 15; z++) {
            for (int y = 1; y <= 3; y++) helper.setBlock(new BlockPos(8, y, z), Blocks.STONE);
        }
        helper.runAfterDelay(600, () -> {
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemFrame.class, helper.getBounds()).isEmpty(), "Expected no frame hung");
            helper.assertTrue(leatherworker.getInventory().countItem(Items.ITEM_FRAME) == 4, "Expected its frames kept");
            helper.succeed();
        });
    }

    public static void aSwitchedOffLeatherworkerShowsVanillaTrades(GameTestHelper helper) {
        SwitchedTrades.assertVanillaTrades(helper, Profession.LEATHERWORKER, LeatherworkerTests.spawnLeatherworker(helper, 1));
        helper.succeed();
    }
}
