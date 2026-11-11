package com.sheepfromheaven.minenow.cleric;

import com.sheepfromheaven.minenow.villager.Economy;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;

/**
 * Potions are all one item and differ in their components, so the cleric's goods are told apart by form and potion:
 * counted over its inventory and chests, taken exactly (the one the player pays for is the one that leaves).
 */
public final class PotionGoods {
    private PotionGoods() {}

    public static ItemStack stack(Item form, Holder<Potion> potion) {
        return PotionContents.createItemStack(form, potion);
    }

    public static boolean is(ItemStack stack, Item form, Holder<Potion> potion) {
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        return stack.is(form) && contents != null && contents.potion().filter(held -> held.equals(potion)).isPresent();
    }

    public static int count(ServerLevel level, Villager villager, Item form, Holder<Potion> potion) {
        return Economy.stacks(level, villager, form).stream().filter(stack -> is(stack, form, potion)).mapToInt(ItemStack::getCount).sum();
    }

    /** One potion of this kind, from its inventory first, then its chests; empty if it has none. */
    public static ItemStack takeOne(ServerLevel level, Villager villager, Item form, Holder<Potion> potion) {
        return Economy.takeExact(level, villager, stack(form, potion));
    }
}
