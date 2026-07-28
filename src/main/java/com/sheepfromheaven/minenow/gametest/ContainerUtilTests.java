package com.sheepfromheaven.minenow.gametest;

import com.sheepfromheaven.minenow.entity.job.ContainerUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

// Registered into the vanilla test_function registry by MineNow's RegisterEvent listener;
// the matching data/minenow/test_instance/*.json files supply the structure and tick budget.
public class ContainerUtilTests {

    public static void extractOneRemovesExactlyOneItem(GameTestHelper helper) {
        helper.makeFloor();

        var barrelRelPos = new BlockPos(2, 1, 2);
        helper.setBlock(barrelRelPos, Blocks.BARREL);

        var level = helper.getLevel();
        var barrelPos = helper.absolutePos(barrelRelPos);

        if (!(level.getBlockEntity(barrelPos) instanceof Container container)) {
            helper.fail("Expected a container block entity at the barrel position");
            return;
        }
        container.setItem(0, new ItemStack(Items.APPLE, 5));

        var extracted = ContainerUtil.extractOne(container, stack -> stack.is(Items.APPLE));

        helper.succeedWhen(() -> {
            helper.assertTrue(extracted.getCount() == 1, "Exactly one apple should be extracted");
            helper.assertTrue(container.getItem(0).getCount() == 4, "Four apples should remain in the barrel");
        });
    }

    public static void extractOneReturnsEmptyWhenNoMatch(GameTestHelper helper) {
        helper.makeFloor();

        var barrelRelPos = new BlockPos(2, 1, 2);
        helper.setBlock(barrelRelPos, Blocks.BARREL);

        var level = helper.getLevel();
        var barrelPos = helper.absolutePos(barrelRelPos);

        if (!(level.getBlockEntity(barrelPos) instanceof Container container)) {
            helper.fail("Expected a container block entity at the barrel position");
            return;
        }
        container.setItem(0, new ItemStack(Items.STONE, 5));

        var extracted = ContainerUtil.extractOne(container, stack -> stack.is(Items.APPLE));

        helper.succeedWhen(() -> {
            helper.assertTrue(extracted.isEmpty(), "No apple should be extracted when none is present");
            helper.assertTrue(container.getItem(0).getCount() == 5, "The unrelated stone stack should be untouched");
        });
    }
}
