package com.sheepfromheaven.minenow.gametest;

import com.sheepfromheaven.minenow.dominion.Dominion;
import com.sheepfromheaven.minenow.dominion.DominionStructure;
import com.sheepfromheaven.minenow.dominion.StructureType;
import com.sheepfromheaven.minenow.entity.job.CourierJob;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.Set;
import java.util.UUID;

// Registered into the vanilla test_function registry by MineNow's RegisterEvent listener;
// the matching data/minenow/test_instance/*.json files supply the structure and tick budget.
public class CourierJobTests {

    public static void storehouseWithFilterRejectsDisallowedItem(GameTestHelper helper) {
        var markerPos = helper.absolutePos(new BlockPos(2, 1, 2));
        var structure = new DominionStructure("Storehouse", StructureType.STOREHOUSE, markerPos, Set.of());

        structure.setItemFilter(Set.of(Items.COAL));

        helper.succeedWhen(() -> {
            helper.assertTrue(structure.accepts(Items.COAL), "Filtered storehouse should accept a listed item");
            helper.assertTrue(!structure.accepts(Items.IRON_INGOT), "Filtered storehouse should reject an item not on its list");
        });
    }

    public static void storehouseWithoutFilterAcceptsAnyItem(GameTestHelper helper) {
        var markerPos = helper.absolutePos(new BlockPos(2, 1, 2));
        var structure = new DominionStructure("Storehouse", StructureType.STOREHOUSE, markerPos, Set.of());

        helper.succeedWhen(() -> helper.assertTrue(structure.accepts(Items.IRON_INGOT),
                "Storehouse with no filter configured should accept any item"));
    }

    public static void courierPrefersNearestStorehouseOverFartherOne(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);

        var nearChestRel = new BlockPos(2, 1, 1);
        var farChestRel = new BlockPos(4, 1, 4);
        helper.setBlock(nearChestRel, Blocks.CHEST);
        helper.setBlock(farChestRel, Blocks.CHEST);

        var nearChestPos = helper.absolutePos(nearChestRel);
        var farChestPos = helper.absolutePos(farChestRel);
        var destMarkerPos = helper.absolutePos(new BlockPos(0, 1, 0));

        var dest = new DominionStructure("House", StructureType.HOUSE, destMarkerPos, Set.of(destMarkerPos));
        var nearStorehouse = new DominionStructure("Near Storehouse", StructureType.STOREHOUSE,
                helper.absolutePos(nearChestRel), Set.of(nearChestPos));
        var farStorehouse = new DominionStructure("Far Storehouse", StructureType.STOREHOUSE,
                helper.absolutePos(farChestRel), Set.of(farChestPos));

        var dominion = new Dominion(UUID.randomUUID(), UUID.randomUUID(), "Test", destMarkerPos,
                com.sheepfromheaven.minenow.dominion.DominionTier.SETTLEMENT);
        dominion.addStructure(dest);
        dominion.addStructure(nearStorehouse);
        dominion.addStructure(farStorehouse);

        ((net.minecraft.world.Container) helper.getLevel().getBlockEntity(nearChestPos)).setItem(0, Items.COAL.getDefaultInstance());
        ((net.minecraft.world.Container) helper.getLevel().getBlockEntity(farChestPos)).setItem(0, Items.COAL.getDefaultInstance());

        var source = CourierJob.findSource(dominion, helper.getLevel(), dest, Items.COAL);

        helper.succeedWhen(() -> helper.assertTrue(nearChestPos.equals(source),
                "Courier should source from the storehouse nearest to the requesting structure"));
    }

    public static void courierSkipsStorehouseThatFiltersOutTheItem(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);

        var nearChestRel = new BlockPos(2, 1, 1);
        var farChestRel = new BlockPos(4, 1, 4);
        helper.setBlock(nearChestRel, Blocks.CHEST);
        helper.setBlock(farChestRel, Blocks.CHEST);

        var nearChestPos = helper.absolutePos(nearChestRel);
        var farChestPos = helper.absolutePos(farChestRel);
        var destMarkerPos = helper.absolutePos(new BlockPos(0, 1, 0));

        var dest = new DominionStructure("House", StructureType.HOUSE, destMarkerPos, Set.of(destMarkerPos));
        var nearStorehouse = new DominionStructure("Near Storehouse", StructureType.STOREHOUSE,
                helper.absolutePos(nearChestRel), Set.of(nearChestPos));
        nearStorehouse.setItemFilter(Set.of(Items.IRON_INGOT)); // does not include coal
        var farStorehouse = new DominionStructure("Far Storehouse", StructureType.STOREHOUSE,
                helper.absolutePos(farChestRel), Set.of(farChestPos));

        var dominion = new Dominion(UUID.randomUUID(), UUID.randomUUID(), "Test", destMarkerPos,
                com.sheepfromheaven.minenow.dominion.DominionTier.SETTLEMENT);
        dominion.addStructure(dest);
        dominion.addStructure(nearStorehouse);
        dominion.addStructure(farStorehouse);

        ((net.minecraft.world.Container) helper.getLevel().getBlockEntity(nearChestPos)).setItem(0, Items.COAL.getDefaultInstance());
        ((net.minecraft.world.Container) helper.getLevel().getBlockEntity(farChestPos)).setItem(0, Items.COAL.getDefaultInstance());

        var source = CourierJob.findSource(dominion, helper.getLevel(), dest, Items.COAL);

        helper.succeedWhen(() -> helper.assertTrue(farChestPos.equals(source),
                "Courier should skip a nearer storehouse whose filter rejects the item and use the farther one instead"));
    }
}
