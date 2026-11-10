package com.sheepfromheaven.minenow.villager;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

import java.util.Optional;

public final class CommissionTests {
    private static final BlockPos ANVIL = new BlockPos(5, 1, 12);

    private CommissionTests() {}

    public static void aWealthyVillagerCommissionsArmorNobodyHasInStock(GameTestHelper helper) {
        Villager armorer = spawnArmorer(helper);
        BlockPos furnace = new BlockPos(5, 1, 18);
        helper.setBlock(furnace, Blocks.BLAST_FURNACE);
        Villager smelter = GameTestHelpers.spawnEmployed(helper, furnace.east(), furnace, com.sheepfromheaven.minenow.smelter.Smelter.PROFESSION, PoiTypes.ARMORER);
        smelter.getInventory().addItem(new ItemStack(Items.COPPER_INGOT, 64));
        Villager farmer = wealthyFarmer(helper);

        helper.succeedWhen(() -> helper.assertTrue(!farmer.getItemBySlot(EquipmentSlot.CHEST).isEmpty() || !farmer.getItemBySlot(EquipmentSlot.HEAD).isEmpty()
                        || !farmer.getItemBySlot(EquipmentSlot.LEGS).isEmpty() || !farmer.getItemBySlot(EquipmentSlot.FEET).isEmpty(),
                "The farmer pays the broke armorer up front, the armorer buys copper from the smelter with it, makes the piece and the farmer wears it; farmer has "
                        + Economy.emeralds(helper.getLevel(), farmer) + " emeralds, armorer " + armorer.getInventory().getItems().stream().filter(stack -> !stack.isEmpty()).toList()
                        + ", orders " + armorer.getData(ModVillagers.COMMISSIONS)));
    }

    public static void aCommissionSurvivesARestart(GameTestHelper helper) {
        Villager armorer = spawnArmorer(helper);
        Villager farmer = wealthyFarmer(helper);
        boolean[] restarted = {false};
        Villager[] after = new Villager[2];

        helper.onEachTick(() -> {
            if (restarted[0] || armorer.getData(ModVillagers.COMMISSIONS).isEmpty()) return;
            restarted[0] = true;
            after[0] = reload(helper, armorer);
            after[1] = reload(helper, farmer);
            helper.assertTrue(Commissions.orderFor(after[0], after[1]).isPresent(), "The armorer still has the farmer's order after the save and load");
            helper.assertTrue(Commissions.placed(after[1]).filter(placed -> placed.maker().equals(after[0].getUUID())).isPresent(), "...and the farmer still knows it paid the armorer");
            after[0].getInventory().addItem(new ItemStack(Items.COPPER_INGOT, 32));
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(restarted[0], "The farmer commissions a piece from the armorer");
            helper.assertTrue(java.util.stream.Stream.of(EquipmentSlot.CHEST, EquipmentSlot.HEAD, EquipmentSlot.LEGS, EquipmentSlot.FEET).anyMatch(slot -> !after[1].getItemBySlot(slot).isEmpty()),
                    "After the restart the armorer makes the paid piece and the farmer collects and wears it; orders " + after[0].getData(ModVillagers.COMMISSIONS));
            helper.assertTrue(after[0].getData(ModVillagers.COMMISSIONS).isEmpty() && Commissions.placed(after[1]).isEmpty(), "...and the order is done on both sides");
        });
    }

    public static void aBuyerGetsItsMoneyBackWhenTheOrderNeverComes(GameTestHelper helper) {
        Villager armorer = spawnArmorer(helper);
        Villager farmer = wealthyFarmer(helper);
        Economy.take(helper.getLevel(), farmer, Items.EMERALD, 64);
        armorer.getInventory().addItem(new ItemStack(Items.EMERALD, 2));
        long longAgo = helper.getLevel().getGameTime() - Commissions.EXPIRES - 1;
        Commissions.place(farmer, armorer, new Commissions.Order(farmer.getUUID(), Optional.of(Items.DIAMOND_CHESTPLATE), 1, Optional.empty(), 2, longAgo));

        helper.succeedWhen(() -> {
            helper.assertTrue(Economy.emeralds(helper.getLevel(), farmer) == 2, "An order that never came is paid back, farmer has " + Economy.emeralds(helper.getLevel(), farmer));
            helper.assertTrue(armorer.getData(ModVillagers.COMMISSIONS).isEmpty() && Commissions.placed(farmer).isEmpty(), "...and forgotten on both sides");
        });
    }

    /** Saves the villager and loads it back in its place, as a restart does. */
    private static Villager reload(GameTestHelper helper, Villager villager) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
        villager.save(output);
        CompoundTag tag = output.buildResult();
        villager.discard();
        Villager loaded = (Villager) EntityType.loadEntityRecursive(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), tag),
                helper.getLevel(), EntitySpawnReason.LOAD, entity -> entity);
        helper.getLevel().addFreshEntity(loaded);
        return loaded;
    }

    private static Villager spawnArmorer(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(ANVIL, Blocks.ANVIL);
        return GameTestHelpers.spawnEmployed(helper, ANVIL.east(), ANVIL, VillagerProfession.ARMORER, PoiTypes.ARMORER);
    }

    private static Villager wealthyFarmer(GameTestHelper helper) {
        BlockPos composter = new BlockPos(19, 1, 12);
        helper.setBlock(composter, Blocks.COMPOSTER);
        Villager farmer = GameTestHelpers.spawnEmployed(helper, composter.west(), composter, VillagerProfession.FARMER, PoiTypes.FARMER);
        farmer.getInventory().addItem(new ItemStack(Items.WOODEN_HOE));
        farmer.getInventory().addItem(new ItemStack(Items.EMERALD, 20));
        farmer.getInventory().addItem(new ItemStack(Items.BREAD, 12));
        return farmer;
    }
}
