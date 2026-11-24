package com.sheepfromheaven.minenow.villager;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.food.VillagerFood;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;

/**
 * Every food is breeding food except raw and unsafe ({@code #minenow:villager_unfit_food}): an item that is food but has no {@code VILLAGER_FOOD} of vanilla's gets one worth its
 * saturation over {@link #SATURATION_PER_POINT}, at least 1. Vanilla's own (bread 4, carrot, potato, beetroot 1) are kept.
 * Vanilla's willingness check, its item pickup and everything here that asks for the component follow.
 */
public final class BreedingFood {
    static final float SATURATION_PER_POINT = 1.5f;

    private BreedingFood() {}

    static int pointsFor(FoodProperties food) {
        return Math.max(1, Math.round(food.saturation() / SATURATION_PER_POINT));
    }

    static final net.minecraft.tags.TagKey<net.minecraft.world.item.Item> UNFIT = net.minecraft.tags.TagKey.create(
            net.minecraft.core.registries.Registries.ITEM, net.minecraft.resources.Identifier.fromNamespaceAndPath(com.sheepfromheaven.minenow.MineNow.MOD_ID, "villager_unfit_food"));

    /** Item tags may not be loaded yet when default components are bound, so vanilla's raw and unsafe foods are listed here too; the tag adds other mods'. */
    private static final java.util.Set<net.minecraft.world.item.Item> VANILLA_UNFIT = java.util.Set.of(
            net.minecraft.world.item.Items.BEEF, net.minecraft.world.item.Items.PORKCHOP, net.minecraft.world.item.Items.MUTTON, net.minecraft.world.item.Items.CHICKEN,
            net.minecraft.world.item.Items.RABBIT, net.minecraft.world.item.Items.COD, net.minecraft.world.item.Items.SALMON, net.minecraft.world.item.Items.TROPICAL_FISH,
            net.minecraft.world.item.Items.PUFFERFISH, net.minecraft.world.item.Items.ROTTEN_FLESH, net.minecraft.world.item.Items.POISONOUS_POTATO,
            net.minecraft.world.item.Items.SPIDER_EYE, net.minecraft.world.item.Items.CHORUS_FRUIT, net.minecraft.world.item.Items.GOLDEN_APPLE,
            net.minecraft.world.item.Items.ENCHANTED_GOLDEN_APPLE);

    private static boolean unfit(net.minecraft.core.HolderLookup.Provider context, net.minecraft.world.item.Item item) {
        if (VANILLA_UNFIT.contains(item)) return true;
        return context.lookupOrThrow(net.minecraft.core.registries.Registries.ITEM).get(UNFIT)
                .map(tag -> tag.contains(item.builtInRegistryHolder())).orElse(false);
    }

    public static void onModifyDefaultComponents(ModifyDefaultComponentsEvent event) {
        event.modifyMatching((item, components) -> components.has(DataComponents.FOOD) && !components.has(DataComponents.VILLAGER_FOOD),
                (components, context, item) -> {
                    FoodProperties food = components.get(DataComponents.FOOD);
                    if (food != null && !unfit(context, item)) components.set(DataComponents.VILLAGER_FOOD, new VillagerFood(pointsFor(food)));
                });
    }
}
