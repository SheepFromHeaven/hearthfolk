package com.sheepfromheaven.minenow.butcher;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * Culling takes as long as the axe dictates, the way a player would fight with it: as many swings as the animal has
 * health for the axe's attack damage, one swing per attack cooldown. A better axe kills in fewer, quicker swings.
 */
final class Butchering {
    private static final double BASE_ATTACK_DAMAGE = 1.0;
    private static final double BASE_ATTACK_SPEED = 4.0;

    private Butchering() {}

    static int swings(float health, double attackDamage) {
        return Math.max(1, (int) Math.ceil(health / Math.max(1.0, attackDamage)));
    }

    static int swingTicks(double attackSpeed) {
        return Math.max(1, (int) Math.round(20.0 / Math.max(0.1, attackSpeed)));
    }

    static double attackDamage(ItemStack axe) {
        return modifiers(axe).compute(Attributes.ATTACK_DAMAGE, BASE_ATTACK_DAMAGE, EquipmentSlot.MAINHAND);
    }

    static double attackSpeed(ItemStack axe) {
        return modifiers(axe).compute(Attributes.ATTACK_SPEED, BASE_ATTACK_SPEED, EquipmentSlot.MAINHAND);
    }

    private static ItemAttributeModifiers modifiers(ItemStack axe) {
        return axe.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
    }
}
