package com.sheepfromheaven.minenow.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Exposes a single {@link LivingEntity} equipment slot as a one-slot {@link Container} for menu use. */
public final class EquipmentSlotContainer implements Container {
    private final LivingEntity entity;
    private final EquipmentSlot slot;

    public EquipmentSlotContainer(LivingEntity entity, EquipmentSlot slot) {
        this.entity = entity;
        this.slot = slot;
    }

    @Override
    public int getContainerSize() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return entity.getItemBySlot(slot).isEmpty();
    }

    @Override
    public ItemStack getItem(int i) {
        return entity.getItemBySlot(slot);
    }

    @Override
    public ItemStack removeItem(int i, int count) {
        var split = entity.getItemBySlot(slot).split(count);
        if (!split.isEmpty()) {
            setChanged();
        }
        return split;
    }

    @Override
    public ItemStack removeItemNoUpdate(int i) {
        var current = entity.getItemBySlot(slot);
        entity.setItemSlot(slot, ItemStack.EMPTY);
        return current;
    }

    @Override
    public void setItem(int i, ItemStack stack) {
        entity.setItemSlot(slot, stack);
    }

    @Override
    public void setChanged() {}

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        entity.setItemSlot(slot, ItemStack.EMPTY);
    }
}
