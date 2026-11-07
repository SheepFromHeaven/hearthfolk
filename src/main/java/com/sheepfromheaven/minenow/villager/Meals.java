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
        int total = 0;
        for (Item item : Economy.items(level, villager)) {
            if (!isMeal(new ItemStack(item))) continue;
            total += Economy.count(level, villager, item) * nutrition(item);
            if (total >= MEAL_NUTRITION) return true;
        }
        return false;
    }

    /** Eats a meal from its goods, the most filling food first. Does nothing unless the goods add up to a whole meal. */
    static boolean eat(ServerLevel level, Villager villager) {
        if (!hasAMeal(level, villager)) return false;
        List<Item> foods = new ArrayList<>(Economy.items(level, villager).stream().filter(item -> isMeal(new ItemStack(item))).toList());
        foods.sort((a, b) -> Integer.compare(nutrition(b), nutrition(a)));
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
