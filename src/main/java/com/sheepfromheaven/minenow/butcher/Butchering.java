package com.sheepfromheaven.minenow.butcher;

import net.minecraft.world.item.ItemStack;

/**
 * Culling takes as long as the axe dictates, the way a player would fight with it: as many swings as the animal has
 * health for the axe's attack damage, one swing per attack cooldown. A better axe kills in fewer, quicker swings.
 */
final class Butchering {
    private Butchering() {}

    static int swings(float health, double attackDamage) {
        return Math.max(1, (int) Math.ceil(health / Math.max(1.0, attackDamage)));
    }

    static int swingTicks(double attackSpeed) {
        return Math.max(1, (int) Math.round(20.0 / Math.max(0.1, attackSpeed)));
    }

    static double attackDamage(ItemStack axe) {
        return com.sheepfromheaven.minenow.villager.Weapons.attackDamage(axe);
    }

    static double attackSpeed(ItemStack axe) {
        return com.sheepfromheaven.minenow.villager.Weapons.attackSpeed(axe);
    }
}
