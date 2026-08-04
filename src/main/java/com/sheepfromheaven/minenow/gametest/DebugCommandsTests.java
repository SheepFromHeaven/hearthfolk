package com.sheepfromheaven.minenow.gametest;

import com.sheepfromheaven.minenow.command.DebugCommands;
import com.sheepfromheaven.minenow.dominion.Dominion;
import com.sheepfromheaven.minenow.dominion.DominionSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;

import java.lang.reflect.Method;
import java.util.UUID;

// Registered into the vanilla test_function registry by MineNow's RegisterEvent listener;
// the matching data/minenow/test_instance/*.json files supply the structure and tick budget.
public class DebugCommandsTests {

    /**
     * Reproduces the "debug village has barrels but no tools in them" gap: a freshly-built debug
     * lumberyard's barrel should already contain an axe, since a lumberjack can't work without one
     * and the whole point of /minenow debugvillage is a village that's immediately playable.
     */
    public static void debugLumberyardBarrelIsStockedWithAnAxe(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);
        var level = helper.getLevel();
        var center = helper.absolutePos(new BlockPos(4, 1, 4));
        var dominion = DominionSavedData.get(level).createDominion(UUID.randomUUID(), "Test Dominion", center);

        invokeBuild("buildLumberyard", level, dominion, center);

        var barrelPos = center.offset(1, 0, 0);
        helper.succeedWhen(() -> helper.assertTrue(
                level.getBlockEntity(barrelPos) instanceof Container container && containerHas(container, stack -> stack.is(ItemTags.AXES)),
                "Debug lumberyard barrel should be stocked with an axe"));
    }

    /**
     * Same gap as the lumberyard, but for the fishery's fishing rod.
     */
    public static void debugFisheryBarrelIsStockedWithAFishingRod(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);
        var level = helper.getLevel();
        var center = helper.absolutePos(new BlockPos(4, 1, 4));
        var dominion = DominionSavedData.get(level).createDominion(UUID.randomUUID(), "Test Dominion", center);

        invokeBuild("buildFishery", level, dominion, center);

        var barrelPos = center.offset(1, 0, -1);
        helper.succeedWhen(() -> helper.assertTrue(
                level.getBlockEntity(barrelPos) instanceof Container container && containerHas(container, stack -> stack.is(Items.FISHING_ROD)),
                "Debug fishery barrel should be stocked with a fishing rod"));
    }

    /**
     * Same gap, but for the tavern's cook, who needs both raw food and fuel to start working.
     */
    public static void debugTavernBarrelIsStockedWithFoodAndFuel(GameTestHelper helper) {
        GameTestHelpers.makeFloor(helper);
        var level = helper.getLevel();
        var center = helper.absolutePos(new BlockPos(4, 1, 4));
        var dominion = DominionSavedData.get(level).createDominion(UUID.randomUUID(), "Test Dominion", center);

        invokeBuild("buildTavern", level, dominion, center);

        var barrelPos = center.offset(1, 0, -1);
        helper.succeedWhen(() -> {
            helper.assertTrue(
                    level.getBlockEntity(barrelPos) instanceof Container container && containerHas(container, stack -> stack.is(Items.BEEF)),
                    "Debug tavern barrel should be stocked with raw food");
            helper.assertTrue(
                    level.getBlockEntity(barrelPos) instanceof Container container && containerHas(container, stack -> stack.is(Items.COAL)),
                    "Debug tavern barrel should be stocked with fuel");
        });
    }

    private static boolean containerHas(Container container, java.util.function.Predicate<net.minecraft.world.item.ItemStack> matcher) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (matcher.test(container.getItem(i))) return true;
        }
        return false;
    }

    private static void invokeBuild(String methodName, net.minecraft.server.level.ServerLevel level, Dominion dominion, BlockPos center) {
        try {
            Method method = DebugCommands.class.getDeclaredMethod(methodName, net.minecraft.server.level.ServerLevel.class, Dominion.class, BlockPos.class);
            method.setAccessible(true);
            method.invoke(null, level, dominion, center);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
