package com.sheepfromheaven.minenow.inhabitant.gametest;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;

import com.sheepfromheaven.minenow.inhabitant.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;

// Registered into the vanilla test_function registry by MineNow's RegisterEvent listener;
// the matching data/minenow/test_instance/*.json files supply the structure and tick budget.
public class InhabitantPickupTests {

    /**
     * Reproduces the "inhabitant picks up a dropped axe into its general inventory instead of
     * equipping it" bug: a job tool dropped at an inhabitant's feet should go straight into the
     * empty main-hand equipment slot, exactly like manually equipping it from storage would.
     */
    public static void pickingUpAToolEquipsMainHandInsteadOfGeneralInventory(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);

        var markerRelPos = new BlockPos(2, 1, 2);
        var markerPos = helper.absolutePos(markerRelPos);
        var level = helper.getLevel();

        var inhabitant = helper.spawn(ModEntities.INHABITANT.get(), markerRelPos);
        inhabitant.snapTo(markerPos.getX() + 0.5, markerPos.getY(), markerPos.getZ() + 0.5);
        var itemEntity = new ItemEntity(level, markerPos.getX() + 0.5, markerPos.getY(), markerPos.getZ() + 0.5,
                Items.NETHERITE_AXE.getDefaultInstance());
        itemEntity.setNoPickUpDelay();
        level.addFreshEntity(itemEntity);

        helper.succeedWhen(() -> {
            helper.assertTrue(inhabitant.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.NETHERITE_AXE),
                    "The dropped axe should be equipped into the main-hand slot");
            helper.assertTrue(inhabitant.getInventory().countItem(Items.NETHERITE_AXE) == 0,
                    "The axe should not also end up in the general job inventory");
        });
    }

    /**
     * Non-tool drops (raw food, logs, ...) should still go to the general job inventory, not the
     * main-hand slot - only job tools get the equipment-slot treatment.
     */
    public static void pickingUpNonToolItemGoesToGeneralInventory(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);

        var markerRelPos = new BlockPos(2, 1, 2);
        var markerPos = helper.absolutePos(markerRelPos);
        var level = helper.getLevel();

        var inhabitant = helper.spawn(ModEntities.INHABITANT.get(), markerRelPos);
        inhabitant.snapTo(markerPos.getX() + 0.5, markerPos.getY(), markerPos.getZ() + 0.5);
        var itemEntity = new ItemEntity(level, markerPos.getX() + 0.5, markerPos.getY(), markerPos.getZ() + 0.5,
                Items.BEEF.getDefaultInstance());
        itemEntity.setNoPickUpDelay();
        level.addFreshEntity(itemEntity);

        helper.succeedWhen(() -> {
            helper.assertTrue(inhabitant.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty(),
                    "Raw food shouldn't be equipped into the main-hand slot");
            helper.assertTrue(inhabitant.getInventory().countItem(Items.BEEF) > 0,
                    "Raw food should be picked up into the general job inventory");
        });
    }
}
