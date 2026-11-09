package com.sheepfromheaven.minenow.leatherworker;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;

public final class LeatherworkerTests {
    static final BlockPos CAULDRON = new BlockPos(5, 1, 12);

    private LeatherworkerTests() {}

    public static void aNoviceLeatherworkerMakesLeatherArmor(GameTestHelper helper) {
        Villager leatherworker = spawnLeatherworker(helper, 1);
        leatherworker.getInventory().addItem(new ItemStack(Items.LEATHER, 5));

        helper.succeedWhen(() -> helper.assertTrue(leatherworker.getInventory().countItem(Items.LEATHER_HELMET) == 1,
                "Five leather make a cap at its cauldron, has " + leatherworker.getInventory().countItem(Items.LEATHER_HELMET)));
    }

    public static void aLeatherworkerTurnsRabbitHidesIntoLeather(GameTestHelper helper) {
        Villager leatherworker = spawnLeatherworker(helper, 1);
        leatherworker.getInventory().addItem(new ItemStack(Items.RABBIT_HIDE, 4));

        helper.succeedWhen(() -> helper.assertTrue(leatherworker.getInventory().countItem(Items.RABBIT_HIDE) == 0,
                "Four rabbit hides make a leather, has " + leatherworker.getInventory().countItem(Items.RABBIT_HIDE) + " hides, " + leatherworker.getInventory().countItem(Items.LEATHER) + " leather"));
    }

    public static void aLeatherworkerGrindsBonesIntoBoneMeal(GameTestHelper helper) {
        Villager leatherworker = spawnLeatherworker(helper, 1);
        leatherworker.getInventory().addItem(new ItemStack(Items.BONE, 1));

        helper.succeedWhen(() -> helper.assertTrue(leatherworker.getInventory().countItem(Items.BONE_MEAL) == 3,
                "A bone makes three bone meal, has " + leatherworker.getInventory().countItem(Items.BONE_MEAL)));
    }

    public static void aLeatherworkerBoneMealsTallFlowersForDye(GameTestHelper helper) {
        Villager leatherworker = spawnLeatherworker(helper, 1);
        leatherworker.getInventory().addItem(new ItemStack(Items.BONE_MEAL, 4));
        BlockPos bush = new BlockPos(10, 1, 14);
        helper.setBlock(bush.below(), Blocks.GRASS_BLOCK);
        DoublePlantBlock.placeAt(helper.getLevel(), Blocks.ROSE_BUSH.defaultBlockState(), helper.absolutePos(bush), 3);

        helper.succeedWhen(() -> {
            int red = leatherworker.getInventory().countItem(Items.DYE.red()) + leatherworker.getInventory().countItem(Items.ROSE_BUSH);
            helper.assertTrue(red >= 1, "Bone meal on a rose bush gives a copy, which becomes red dye, has " + leatherworker.getInventory().countItem(Items.ROSE_BUSH)
                    + " rose bushes, " + leatherworker.getInventory().countItem(Items.DYE.red()) + " red dye, " + leatherworker.getInventory().countItem(Items.BONE_MEAL) + " bone meal");
            helper.assertTrue(helper.getBlockState(bush).is(Blocks.ROSE_BUSH), "...and the bush stays where it grows");
        });
    }

    public static void aLeatherworkerDyesItsLeatherArmor(GameTestHelper helper) {
        Villager leatherworker = spawnLeatherworker(helper, 1);
        leatherworker.getInventory().addItem(new ItemStack(Items.LEATHER_CHESTPLATE));
        leatherworker.getInventory().addItem(new ItemStack(Items.DYE.red()));

        helper.succeedWhen(() -> {
            ItemStack tunic = leatherworker.getInventory().getItems().stream().filter(stack -> stack.is(Items.LEATHER_CHESTPLATE)).findFirst().orElse(ItemStack.EMPTY);
            helper.assertTrue(tunic.has(DataComponents.DYED_COLOR), "At its cauldron it dyes its plain tunic, " + tunic.getComponents());
            helper.assertTrue(leatherworker.getInventory().countItem(Items.DYE.red()) == 0, "...using up the dye");
        });
    }

    public static void aLeatherworkerDressesAStandWithItsLeatherArmor(GameTestHelper helper) {
        Villager leatherworker = spawnLeatherworker(helper, 1);
        leatherworker.getInventory().addItem(new ItemStack(Items.LEATHER_BOOTS));
        ArmorStand stand = helper.spawn(EntityTypes.ARMOR_STAND, new BlockPos(9, 1, 12));

        helper.succeedWhen(() -> helper.assertTrue(stand.getItemBySlot(EquipmentSlot.FEET).is(Items.LEATHER_BOOTS),
                "It shows off its leather boots on the stand, it wears " + stand.getItemBySlot(EquipmentSlot.FEET)));
    }

    static Villager spawnLeatherworker(GameTestHelper helper, int level) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(CAULDRON, Blocks.CAULDRON);
        Villager villager = GameTestHelpers.spawnEmployed(helper, CAULDRON.east(), CAULDRON, VillagerProfession.LEATHERWORKER, PoiTypes.LEATHERWORKER);
        villager.setVillagerData(villager.getVillagerData().withLevel(level));
        return villager;
    }
}
