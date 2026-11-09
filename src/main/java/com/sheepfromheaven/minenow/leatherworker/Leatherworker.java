package com.sheepfromheaven.minenow.leatherworker;

import com.sheepfromheaven.minenow.armorer.Armorer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The vanilla leatherworker at its cauldron: what it shows on its armor stands. */
public final class Leatherworker {
    private Leatherworker() {}

    /** What it hangs on its tanning racks: rabbit hide, else leather. */
    public static ItemStack hide(net.minecraft.world.entity.npc.villager.Villager body) {
        for (var item : java.util.List.of(Items.RABBIT_HIDE, Items.LEATHER)) {
            for (ItemStack stack : body.getInventory().getItems()) if (stack.is(item)) return stack;
        }
        return ItemStack.EMPTY;
    }

    /** Leather armor only, a dyed piece shown before a plain one; anything else isn't its to show. */
    public static double displayWorth(ItemStack stack) {
        boolean leather = stack.is(Items.LEATHER_HELMET) || stack.is(Items.LEATHER_CHESTPLATE) || stack.is(Items.LEATHER_LEGGINGS) || stack.is(Items.LEATHER_BOOTS);
        if (!leather) return 0;
        return Armorer.protection(stack) + (stack.has(DataComponents.DYED_COLOR) ? 0.5 : 0);
    }
}
