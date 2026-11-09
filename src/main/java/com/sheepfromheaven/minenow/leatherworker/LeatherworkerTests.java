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

    public static void aLeatherworkerHangsAsManyTanningRacksAsItsLevel(GameTestHelper helper) {
        Villager leatherworker = spawnLeatherworker(helper, 2);
        leatherworker.getInventory().addItem(new ItemStack(Items.ITEM_FRAME, 4));
        leatherworker.getInventory().addItem(new ItemStack(Items.LEATHER, 8));
        wall(helper);

        helper.succeedWhen(() -> helper.assertTrue(racks(helper).size() == 2,
                "An apprentice hangs two racks with hides on its workshop wall, hung " + racks(helper).size()));
    }

    public static void aLeatherworkersRacksAreLockedAndComeBackWhenBroken(GameTestHelper helper) {
        Villager leatherworker = spawnLeatherworker(helper, 1);
        leatherworker.getInventory().addItem(new ItemStack(Items.ITEM_FRAME, 1));
        leatherworker.getInventory().addItem(new ItemStack(Items.LEATHER, 2));
        wall(helper);
        @SuppressWarnings("removal")
        net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        boolean[] broken = {false};

        helper.succeedWhen(() -> {
            if (!broken[0]) {
                var hung = racks(helper);
                helper.assertTrue(hung.size() == 1, "It hangs its rack first, hung " + hung.size());
                var rack = hung.getFirst();
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                player.interactOn(rack, net.minecraft.world.InteractionHand.MAIN_HAND, rack.position());
                helper.assertTrue(!rack.getItem().isEmpty() || !rack.isAlive(), "A player can't take the hide off a rack");
                player.attack(rack);
                broken[0] = true;
            }
            helper.assertTrue(racks(helper).isEmpty(), "Breaking the rack is allowed");
            helper.assertTrue(leatherworker.getInventory().countItem(Items.ITEM_FRAME) + racks(helper).size() >= 1 && leatherworker.getInventory().countItem(Items.LEATHER) >= 1,
                    "...the frame and the hide go back to the leatherworker, has " + leatherworker.getInventory().countItem(Items.ITEM_FRAME) + " frames, " + leatherworker.getInventory().countItem(Items.LEATHER) + " leather");
            helper.assertTrue(leatherworker.getGossips().getReputation(player.getUUID(), type -> true) == 0, "...and it costs nothing");
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, helper.getBounds()).isEmpty(), "...nothing drops for the player");
        });
    }

    public static void aLeatherworkerUsesYourEmptyItemFramesFirst(GameTestHelper helper) {
        Villager leatherworker = spawnLeatherworker(helper, 1);
        leatherworker.getInventory().addItem(new ItemStack(Items.ITEM_FRAME, 2));
        leatherworker.getInventory().addItem(new ItemStack(Items.LEATHER, 4));
        wall(helper);
        var yours = new net.minecraft.world.entity.decoration.ItemFrame(helper.getLevel(), helper.absolutePos(new BlockPos(7, 2, 14)), net.minecraft.core.Direction.WEST);
        helper.getLevel().addFreshEntity(yours);

        helper.succeedWhen(() -> {
            helper.assertTrue(!yours.getItem().isEmpty(), "It hangs its hide in the empty frame you put up, it holds " + yours.getItem());
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.decoration.ItemFrame.class, helper.getBounds()).size() == 1,
                    "...rather than hanging a frame of its own");
            helper.assertTrue(leatherworker.getInventory().countItem(Items.ITEM_FRAME) == 2, "...so its own frames stay in its goods");
        });
    }

    /** A stone wall at x 8 the leatherworker can hang racks on, facing its cauldron. */
    private static void wall(GameTestHelper helper) {
        for (int z = 9; z <= 15; z++) {
            for (int y = 1; y <= 3; y++) helper.setBlock(new BlockPos(8, y, z), Blocks.STONE);
        }
    }

    private static java.util.List<net.minecraft.world.entity.decoration.ItemFrame> racks(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.decoration.ItemFrame.class, helper.getBounds(), frame -> !frame.getItem().isEmpty());
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
