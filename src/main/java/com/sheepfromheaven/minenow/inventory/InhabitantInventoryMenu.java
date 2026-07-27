package com.sheepfromheaven.minenow.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A tool slot (mirroring the player's offhand) followed by a gap, then however many general slots
 * the inhabitant currently has (2 by default, more once backpack/pouch items grow their capacity
 * later). All of it sits on vanilla's own single-row chest grid so the real chest texture can be
 * reused as-is - the gap cell and any grid cells beyond the current capacity simply never become
 * slots at all (see {@link com.sheepfromheaven.minenow.client.InhabitantInventoryScreen}, which
 * paints over those unused cells rather than showing them as dead/locked slots).
 */
public class InhabitantInventoryMenu extends AbstractContainerMenu {
    public static final int TOOL_GRID_INDEX = 0;
    public static final int GAP_GRID_INDEX = 1;
    public static final int GENERAL_GRID_START = 2;
    public static final int GRID_X = 8;
    public static final int SLOT_Y = 18;

    private final Container inventory;
    private final int capacity;

    public InhabitantInventoryMenu(int containerId, Inventory playerInventory, LivingEntity inhabitant, Container inventory) {
        this(containerId, playerInventory, new ToolContainer(inhabitant, EquipmentSlot.MAINHAND), inventory);
    }

    public InhabitantInventoryMenu(int containerId, Inventory playerInventory, Container toolContainer, Container inventory) {
        super(ModMenus.INHABITANT_INVENTORY.get(), containerId);
        this.inventory = inventory;
        this.capacity = inventory.getContainerSize();
        inventory.startOpen(playerInventory.player);

        addSlot(new Slot(toolContainer, 0, gridX(TOOL_GRID_INDEX), SLOT_Y));
        for (int i = 0; i < capacity; i++) {
            addSlot(new Slot(inventory, i, gridX(GENERAL_GRID_START + i), SLOT_Y));
        }
        addStandardInventorySlots(playerInventory, GRID_X, SLOT_Y + 18 + 13);
    }

    private static int gridX(int gridIndex) {
        return GRID_X + gridIndex * 18;
    }

    @Override
    public boolean stillValid(Player player) {
        return inventory.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        var result = ItemStack.EMPTY;
        var slot = slots.get(index);
        if (slot != null && slot.hasItem()) {
            var stack = slot.getItem();
            result = stack.copy();
            int generalEnd = 1 + capacity;
            if (index < generalEnd) {
                if (!moveItemStackTo(stack, generalEnd, slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, 1, generalEnd, false)) {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return result;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        inventory.stopOpen(player);
    }

    /** Exposes a single {@link LivingEntity} equipment slot as a one-slot {@link Container} for menu use. */
    private static final class ToolContainer implements Container {
        private final LivingEntity entity;
        private final EquipmentSlot slot;

        ToolContainer(LivingEntity entity, EquipmentSlot slot) {
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
}
