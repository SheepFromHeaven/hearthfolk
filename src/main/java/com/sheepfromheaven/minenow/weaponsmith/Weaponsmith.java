package com.sheepfromheaven.minenow.weaponsmith;

import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.util.Comparator;

/** The vanilla weaponsmith at its grindstone: makes swords (trade file), restores worn blades ({@link RestoreWorn}), shows its best on the wall. */
public final class Weaponsmith {
    private Weaponsmith() {}

    /** The best whole sword it holds, to hang on its wall; an empty stack if it has none. */
    public static ItemStack bestSword(Villager body) {
        return body.getInventory().getItems().stream()
                .filter(stack -> stack.is(ItemTags.SWORDS) && !stack.isDamaged())
                .max(Comparator.comparingDouble(Weaponsmith::attackDamage))
                .orElse(ItemStack.EMPTY);
    }

    static double attackDamage(ItemStack stack) {
        return stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers().stream()
                .filter(entry -> entry.attribute().equals(Attributes.ATTACK_DAMAGE))
                .mapToDouble(entry -> entry.modifier().amount())
                .sum();
    }
}
