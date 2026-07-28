package com.sheepfromheaven.minenow.gametest;

import com.sheepfromheaven.minenow.dominion.DominionSavedData;
import com.sheepfromheaven.minenow.dominion.DominionStructure;
import com.sheepfromheaven.minenow.dominion.StructureType;
import com.sheepfromheaven.minenow.entity.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.Set;
import java.util.UUID;

// Registered into the vanilla test_function registry by MineNow's RegisterEvent listener;
// the matching data/minenow/test_instance/*.json files supply the structure and tick budget.
public class FinishWorkGoalTests {

    public static void inhabitantStoresToolInStorageAtNight(GameTestHelper helper) {
        helper.makeFloor();

        var barrelRelPos = new BlockPos(1, 1, 1);
        helper.setBlock(barrelRelPos, Blocks.BARREL);

        var level = helper.getLevel();
        var barrelPos = helper.absolutePos(barrelRelPos);
        var markerPos = helper.absolutePos(new BlockPos(2, 1, 2));

        var dominion = DominionSavedData.get(level).createDominion(UUID.randomUUID(), "Test Dominion", markerPos);
        var structure = new DominionStructure("Lumberyard", StructureType.LUMBERYARD, markerPos, Set.of(markerPos, barrelPos));
        dominion.addStructure(structure);

        var inhabitant = helper.spawn(ModEntities.INHABITANT.get(), new BlockPos(3, 1, 3));
        inhabitant.setDominionId(dominion.getId());
        inhabitant.setWorkStructureId(structure.getId());
        inhabitant.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));

        // Night window from GoHomeGoal/FinishWorkGoal: dayTime in [12542, 23460).
        level.setDayTime(13000L);

        helper.succeedWhen(() -> {
            helper.assertTrue(inhabitant.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty(),
                    "Inhabitant should have stowed its equipped tool before nightfall");
            helper.assertTrue(level.getBlockEntity(barrelPos) instanceof Container container
                            && !container.getItem(0).isEmpty() && container.getItem(0).is(Items.IRON_AXE),
                    "The axe should have been deposited into the workplace's storage");
        });
    }
}
