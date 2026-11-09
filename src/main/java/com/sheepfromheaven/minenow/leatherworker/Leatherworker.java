package com.sheepfromheaven.minenow.leatherworker;

import com.sheepfromheaven.minenow.armorer.Armorer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The vanilla leatherworker at its cauldron: what it shows on its armor stands. */
public final class Leatherworker {
    private Leatherworker() {}

    /** Leather armor only, a dyed piece shown before a plain one; anything else isn't its to show. */
    public static double displayWorth(ItemStack stack) {
        boolean leather = stack.is(Items.LEATHER_HELMET) || stack.is(Items.LEATHER_CHESTPLATE) || stack.is(Items.LEATHER_LEGGINGS) || stack.is(Items.LEATHER_BOOTS);
        if (!leather) return 0;
        return Armorer.protection(stack) + (stack.has(DataComponents.DYED_COLOR) ? 0.5 : 0);
    }
}
