package com.sheepfromheaven.minenow.jobs.gametest;

import com.sheepfromheaven.minenow.jobs.cook.CookingRecipes;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

// Registered into the vanilla test_function registry by MineNow's RegisterEvent listener;
// the matching data/minenow/test_instance/*.json files supply the structure and tick budget.
public class CookingRecipesTests {

    public static void findSmokingRecipeReturnsCookedResultForRawBeef(GameTestHelper helper) {
        var level = helper.getLevel();
        var recipe = CookingRecipes.findSmokingRecipe(level, new ItemStack(Items.BEEF));

        helper.succeedWhen(() -> {
            helper.assertTrue(recipe.isPresent(), "Raw beef should have a smoking recipe");
            var result = recipe.get().value().assemble(
                    new net.minecraft.world.item.crafting.SingleRecipeInput(new ItemStack(Items.BEEF)), level.registryAccess());
            helper.assertTrue(result.is(Items.COOKED_BEEF), "Smoking raw beef should produce cooked beef");
        });
    }

    public static void findSmokingRecipeIsEmptyForCookedBeef(GameTestHelper helper) {
        var level = helper.getLevel();
        var recipe = CookingRecipes.findSmokingRecipe(level, new ItemStack(Items.COOKED_BEEF));

        helper.succeedWhen(() ->
                helper.assertTrue(recipe.isEmpty(), "Cooked beef should not itself have a smoking recipe"));
    }

    public static void isFuelTrueForLog(GameTestHelper helper) {
        var level = helper.getLevel();
        boolean isFuel = CookingRecipes.isFuel(level, new ItemStack(Items.OAK_LOG));

        helper.succeedWhen(() -> helper.assertTrue(isFuel, "Oak log should be valid furnace fuel"));
    }

    public static void isFuelFalseForStone(GameTestHelper helper) {
        var level = helper.getLevel();
        boolean isFuel = CookingRecipes.isFuel(level, new ItemStack(Items.STONE));

        helper.succeedWhen(() -> helper.assertTrue(!isFuel, "Stone should not be valid furnace fuel"));
    }

    public static void isReadyToEatTrueForCookedBeef(GameTestHelper helper) {
        var level = helper.getLevel();
        boolean ready = CookingRecipes.isReadyToEat(level, new ItemStack(Items.COOKED_BEEF));

        helper.succeedWhen(() -> helper.assertTrue(ready, "Cooked beef should be ready to eat"));
    }

    public static void isReadyToEatFalseForRawBeef(GameTestHelper helper) {
        var level = helper.getLevel();
        boolean ready = CookingRecipes.isReadyToEat(level, new ItemStack(Items.BEEF));

        helper.succeedWhen(() ->
                helper.assertTrue(!ready, "Raw beef is a cooking ingredient, not ready to eat directly"));
    }

    public static void isReadyToEatFalseForNonFoodItem(GameTestHelper helper) {
        var level = helper.getLevel();
        boolean ready = CookingRecipes.isReadyToEat(level, new ItemStack(Items.STONE));

        helper.succeedWhen(() -> helper.assertTrue(!ready, "A non-food item should never be ready to eat"));
    }

    public static void isReadyToEatTrueForBreadWithoutAnySmokingRecipe(GameTestHelper helper) {
        var level = helper.getLevel();
        boolean ready = CookingRecipes.isReadyToEat(level, new ItemStack(Items.BREAD));

        helper.succeedWhen(() ->
                helper.assertTrue(ready, "Bread is directly edible and has no smoking recipe, so it should be ready to eat"));
    }
}
