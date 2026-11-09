package com.sheepfromheaven.minenow.villager;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/** A weapon or tool's attack, as a player would swing it: its damage, and how many swings a second it allows. */
public final class Weapons {
    private static final double BASE_ATTACK_DAMAGE = 1.0;
    private static final double BASE_ATTACK_SPEED = 4.0;

    private Weapons() {}

    public static double attackDamage(ItemStack stack) {
        return modifiers(stack).compute(Attributes.ATTACK_DAMAGE, BASE_ATTACK_DAMAGE, EquipmentSlot.MAINHAND);
    }

    public static double attackSpeed(ItemStack stack) {
        return modifiers(stack).compute(Attributes.ATTACK_SPEED, BASE_ATTACK_SPEED, EquipmentSlot.MAINHAND);
    }

    /** Ticks between full-strength swings. */
    public static int swingTicks(ItemStack stack) {
        return Math.max(1, (int) Math.round(20.0 / Math.max(0.1, attackSpeed(stack))));
    }

    private static ItemAttributeModifiers modifiers(ItemStack stack) {
        return stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
    }
}
