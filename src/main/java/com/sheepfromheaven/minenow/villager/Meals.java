package com.sheepfromheaven.minenow.villager;

import com.sheepfromheaven.minenow.MineNow;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.npc.villager.Villager;

import java.util.ArrayList;
import java.util.List;

/**
 * Every adult villager eats a meal twice a day: food from {@link #MEALS} adding up to {@link #MEAL_NUTRITION}, taken
 * from its inventory or home chest. A hungry villager without enough food buys some ({@link BuyFromVillagers}).
 */
public final class Meals {
    public static final TagKey<Item> MEALS = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "villager_meals"));
    private static final TagKey<Item> COOKED_MEAT = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "foods/cooked_meat"));
    private static final TagKey<Item> COOKED_FISH = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "foods/cooked_fish"));
    static final int MEAL_NUTRITION = 6;
    static final long MEAL_INTERVAL = 12000L;

    private Meals() {}

    public static boolean isHungry(Villager villager) {
        long now = villager.level().getGameTime();
        if (!villager.hasData(ModVillagers.LAST_MEAL)) villager.setData(ModVillagers.LAST_MEAL, now);
        return now - villager.getData(ModVillagers.LAST_MEAL) >= MEAL_INTERVAL;
    }

    public static void makeHungry(Villager villager) {
        villager.setData(ModVillagers.LAST_MEAL, villager.level().getGameTime() - MEAL_INTERVAL);
    }

    public static boolean isMeal(ItemStack stack) {
        return stack.is(MEALS) && stack.has(DataComponents.FOOD);
    }

    static int nutrition(Item item) {
        FoodProperties food = new ItemStack(item).get(DataComponents.FOOD);
        return food == null ? 0 : food.nutrition();
    }

    static boolean hasAMeal(ServerLevel level, Villager villager) {
        return mealNutrition(level, villager, item -> true) >= MEAL_NUTRITION;
    }

    private static int mealNutrition(ServerLevel level, Villager villager, java.util.function.Predicate<Item> kind) {
        int total = 0;
        for (Item item : Economy.items(level, villager)) {
            if (!isMeal(new ItemStack(item)) || !kind.test(item)) continue;
            total += Economy.count(level, villager, item) * nutrition(item);
        }
        return total;
    }

    /** Cooked meat or fish, as opposed to bread, vegetables and the like. */
    static boolean isMeat(Item item) {
        ItemStack stack = new ItemStack(item);
        return stack.is(COOKED_MEAT) || stack.is(COOKED_FISH);
    }

    /** The kind of food its next meal should be: meat or fish after bread and vegetables, and the other way round. */
    static boolean wantsMeat(Villager villager) {
        return !villager.getExistingData(ModVillagers.LAST_MEAL_MEAT).orElse(false);
    }

    /** It has a meal, but not of the kind it would like next, so it might buy some for a change. */
    static boolean wantsAChange(ServerLevel level, Villager villager) {
        boolean meat = wantsMeat(villager);
        return mealNutrition(level, villager, item -> isMeat(item) == meat) < MEAL_NUTRITION;
    }

    /**
     * Eats a meal from its goods: the other kind than last time (bread or vegetables, meat or fish) when it has a meal's
     * worth of it, the most filling food first. Does nothing unless the goods add up to a whole meal.
     */
    static boolean eat(ServerLevel level, Villager villager) {
        if (!hasAMeal(level, villager)) return false;
        boolean meat = wantsMeat(villager);
        boolean varied = mealNutrition(level, villager, item -> isMeat(item) == meat) >= MEAL_NUTRITION;
        List<Item> foods = new ArrayList<>(Economy.items(level, villager).stream().filter(item -> isMeal(new ItemStack(item))).toList());
        foods.sort(java.util.Comparator.comparing((Item item) -> varied && isMeat(item) != meat).thenComparing(Meals::nutrition, java.util.Comparator.reverseOrder()));
        villager.setData(ModVillagers.LAST_MEAL_MEAT, !foods.isEmpty() && isMeat(foods.getFirst()));
        int eaten = 0;
        for (Item item : foods) {
            while (eaten < MEAL_NUTRITION && Economy.take(level, villager, item, 1) == 1) {
                eaten += nutrition(item);
                level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, item),
                        villager.getX(), villager.getEyeY() - 0.3, villager.getZ(), 8, 0.15, 0.1, 0.15, 0.05);
            }
            if (eaten >= MEAL_NUTRITION) break;
        }
        level.playSound(null, villager.blockPosition(), SoundEvents.GENERIC_EAT.value(), SoundSource.NEUTRAL, 0.8F, villager.getVoicePitch());
        villager.setData(ModVillagers.LAST_MEAL, level.getGameTime());
        return true;
    }
}
