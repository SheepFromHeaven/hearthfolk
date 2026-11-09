package com.sheepfromheaven.minenow.villager;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;

/** The tool of a villager's trade (see {@link Unloading#isTool}): held while working and worn down by use. */
public final class Tools {
    private Tools() {}

    public static int slot(Villager villager) {
        SimpleContainer inventory = villager.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (Unloading.isTool(villager, inventory.getItem(i))) return i;
        }
        return -1;
    }

    public static boolean has(Villager villager) {
        return slot(villager) >= 0;
    }

    /** Shows a copy in the main hand; vanilla's trade display empties that hand, so the real tool stays in the inventory. */
    public static void showInHand(Villager villager, boolean show) {
        int slot = slot(villager);
        if (show && slot >= 0) {
            villager.setItemSlot(EquipmentSlot.MAINHAND, villager.getInventory().getItem(slot).copy());
            villager.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        } else if (Unloading.isTool(villager, villager.getMainHandItem())) {
            villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }
    }

    public static void wear(ServerLevel level, Villager villager) {
        int slot = slot(villager);
        if (slot < 0) return;
        ItemStack tool = villager.getInventory().getItem(slot);
        tool.hurtAndBreak(1, level, villager, broken ->
                level.playSound(null, villager.blockPosition(), SoundEvents.ITEM_BREAK.value(), SoundSource.NEUTRAL, 0.8F, 1.0F));
        if (tool.isEmpty()) villager.getInventory().setItem(slot, ItemStack.EMPTY);
        showInHand(villager, has(villager));
    }
}
