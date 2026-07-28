package com.sheepfromheaven.minenow.gametest;

import com.sheepfromheaven.minenow.dominion.DominionSavedData;
import com.sheepfromheaven.minenow.dominion.DominionStructure;
import com.sheepfromheaven.minenow.dominion.StructureType;
import com.sheepfromheaven.minenow.entity.InhabitantEntity;
import com.sheepfromheaven.minenow.entity.ModEntities;
import com.sheepfromheaven.minenow.entity.job.WorkGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.Set;
import java.util.UUID;

// Registered into the vanilla test_function registry by MineNow's RegisterEvent listener;
// the matching data/minenow/test_instance/*.json files supply the structure and tick budget.
public class CookJobTests {

    /**
     * Reproduces the "cook takes food and fuel from the barrel but never uses the smoker" bug:
     * once ingredients are found, they should end up loaded into the tavern's actual Smoker block
     * entity so vanilla furnace mechanics cook them, not just held in the job's own memory.
     */
    public static void cookLoadsRawFoodAndFuelIntoTheSmoker(GameTestHelper helper) {
        var setup = setUpCookWithIngredients(helper);

        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getLevel().getBlockEntity(setup.smokerPos) instanceof Container smoker
                            && !smoker.getItem(0).isEmpty(),
                    "Raw food should be loaded into the smoker's input slot");
            helper.assertTrue(helper.getLevel().getBlockEntity(setup.smokerPos) instanceof Container smoker
                            && !smoker.getItem(1).isEmpty(),
                    "Fuel should be loaded into the smoker's fuel slot");
            helper.assertTrue("Cooking a meal".equals(setup.workGoal.getStatusDescription()),
                    "Job should have moved to the cooking phase once ingredients are loaded into the smoker");
        });
    }

    /**
     * Reproduces the "issue says 'Needs Raw Beef'/'Needs Oak Log(s)'" bug: the cook is blocked on
     * a category (any raw food, any fuel), not one specific item, so the reported label should
     * say so generically instead of naming an arbitrary example item.
     */
    public static void missingRawFoodReportsGenericLabelInsteadOfASpecificItem(GameTestHelper helper) {
        helper.makeFloor();

        var markerRelPos = new BlockPos(2, 1, 2);
        var barrelRelPos = new BlockPos(1, 1, 1);
        var smokerRelPos = new BlockPos(3, 1, 1);
        helper.setBlock(barrelRelPos, Blocks.BARREL);
        helper.setBlock(smokerRelPos, Blocks.SMOKER);

        var level = helper.getLevel();
        var markerPos = helper.absolutePos(markerRelPos);
        var barrelPos = helper.absolutePos(barrelRelPos);
        var smokerPos = helper.absolutePos(smokerRelPos);

        var dominion = DominionSavedData.get(level).createDominion(UUID.randomUUID(), "Test Dominion", markerPos);
        var structure = new DominionStructure("Tavern", StructureType.TAVERN, markerPos, Set.of(markerPos, barrelPos, smokerPos));
        dominion.addStructure(structure);

        var inhabitant = helper.spawn(ModEntities.INHABITANT.get(), markerPos);
        inhabitant.setDominionId(dominion.getId());
        inhabitant.setWorkStructureId(structure.getId());

        var workGoal = new WorkGoal(inhabitant);
        workGoal.canUse();
        workGoal.start();

        inhabitant.snapTo(markerPos.getX() + 0.5, markerPos.getY(), markerPos.getZ() + 0.5);
        workGoal.tick();
        workGoal.tick();

        helper.succeedWhen(() -> {
            helper.assertTrue("raw food".equals(workGoal.getMissingItemLabel()),
                    "Missing raw food should be reported generically, not as a specific item");
            helper.assertTrue(workGoal.getMissingItem() == null,
                    "No single item represents a whole category, so getMissingItem should stay null");
        });
    }

    /**
     * Reproduces the "stuck at 'Cooking a meal' forever" bug: if the fuel that was loaded burns out
     * before the food finishes smoking, the smoker just sits unlit with no result, and the job should
     * notice and go fetch more fuel instead of waiting on a result that will never appear.
     */
    public static void cookingWithBurnedOutFuelGoesBackToFetchMoreFuel(GameTestHelper helper) {
        var setup = setUpCookWithIngredients(helper);

        // Simulate the loaded fuel having burned out completely before the food finished cooking:
        // no result yet, no fuel left, and the smoker isn't actively lit.
        if (helper.getLevel().getBlockEntity(setup.smokerPos) instanceof Container smoker) {
            smoker.setItem(1, ItemStack.EMPTY);
            smoker.setItem(2, ItemStack.EMPTY);
        }
        setup.workGoal.tick();

        helper.succeedWhen(() ->
                helper.assertTrue(!"Cooking a meal".equals(setup.workGoal.getStatusDescription()),
                        "Job should stop waiting on a smoker that ran out of fuel and go fetch more instead"));
    }

    private record CookSetup(InhabitantEntity inhabitant, WorkGoal workGoal, BlockPos smokerPos) {}

    private static CookSetup setUpCookWithIngredients(GameTestHelper helper) {
        helper.makeFloor();

        var markerRelPos = new BlockPos(2, 1, 2);
        var barrelRelPos = new BlockPos(1, 1, 1);
        var smokerRelPos = new BlockPos(3, 1, 1);
        helper.setBlock(barrelRelPos, Blocks.BARREL);
        helper.setBlock(smokerRelPos, Blocks.SMOKER);

        var level = helper.getLevel();
        var markerPos = helper.absolutePos(markerRelPos);
        var barrelPos = helper.absolutePos(barrelRelPos);
        var smokerPos = helper.absolutePos(smokerRelPos);

        if (level.getBlockEntity(barrelPos) instanceof Container container) {
            container.setItem(0, new ItemStack(Items.BEEF));
            container.setItem(1, new ItemStack(Items.COAL, 8));
        }

        var dominion = DominionSavedData.get(level).createDominion(UUID.randomUUID(), "Test Dominion", markerPos);
        var structure = new DominionStructure("Tavern", StructureType.TAVERN, markerPos, Set.of(markerPos, barrelPos, smokerPos));
        dominion.addStructure(structure);

        var inhabitant = helper.spawn(ModEntities.INHABITANT.get(), markerPos);
        inhabitant.setDominionId(dominion.getId());
        inhabitant.setWorkStructureId(structure.getId());

        var workGoal = new WorkGoal(inhabitant);
        workGoal.canUse();
        workGoal.start();

        // Drive the job through GOTO_HUT -> FIND_INGREDIENTS -> COOKING. Each ingredient is now a
        // pick-up-from-barrel leg and a carry-to-smoker leg, each paced ~1/second
        // (CookJob.ACTION_COOLDOWN_TICKS), so this needs well more than a couple of ticks.
        inhabitant.snapTo(markerPos.getX() + 0.5, markerPos.getY(), markerPos.getZ() + 0.5);
        for (int i = 0; i < 150 && !"Cooking a meal".equals(workGoal.getStatusDescription()); i++) {
            workGoal.tick();
        }

        return new CookSetup(inhabitant, workGoal, smokerPos);
    }
}
