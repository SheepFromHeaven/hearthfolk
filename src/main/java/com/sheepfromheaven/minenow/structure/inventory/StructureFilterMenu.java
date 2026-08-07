package com.sheepfromheaven.minenow.structure.inventory;

import com.sheepfromheaven.minenow.dominion.DominionSavedData;
import com.sheepfromheaven.minenow.storage.inventory.ModMenus;
import com.sheepfromheaven.minenow.structure.DominionStructure;
import com.sheepfromheaven.minenow.storage.item.FilterItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import sheepfromheaven.screenspec.runtime.ScreenSpec;
import sheepfromheaven.screenspec.runtime.ScreenSpecLoader;
import sheepfromheaven.screenspec.runtime.SpecSlots;

/**
 * Lets a player drag a {@link FilterItem} in and out of a storehouse's filter slot using their own
 * inventory, same as any other container. The slot is backed by {@link StructureFilterContainer},
 * which reads/writes the {@link DominionStructure}'s filter fields directly - there's no separate
 * state to reconcile on close.
 */
public class StructureFilterMenu extends AbstractContainerMenu {
    private static final int FILTER_SLOT = 0;

    /** Server-safe (no ResourceManager dependency) - see {@link ScreenSpecLoader#fromClasspath}. */
    public static final ScreenSpec SPEC = ScreenSpecLoader.fromClasspath("minenow", "structure_filter_screen");

    /** Server-side: bound to the real structure's filter. */
    public StructureFilterMenu(int containerId, Inventory playerInventory, DominionStructure structure, DominionSavedData data) {
        super(ModMenus.STRUCTURE_FILTER_TYPE.get(), containerId);
        addSlots(playerInventory, new StructureFilterContainer(structure, data));
    }

    /** Client-side reconstruction: slot contents sync afterward as usual. */
    public StructureFilterMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        super(ModMenus.STRUCTURE_FILTER_TYPE.get(), containerId);
        addSlots(playerInventory, new SimpleContainer(1));
    }

    private void addSlots(Inventory playerInventory, net.minecraft.world.Container filterContainer) {
        var container = SPEC.container;
        var filterArea = container.area("filter_slot");
        addSlot(new Slot(filterContainer, 0, filterArea.slotX(0), filterArea.slotY(0)) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof FilterItem;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        SpecSlots.forPlayerInventory(container.area("player_inv"), playerInventory).forEach(this::addSlot);
        SpecSlots.forPlayerHotbar(container.area("player_hotbar"), playerInventory).forEach(this::addSlot);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        var slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;
        var stack = slot.getItem();
        var result = stack.copy();
        if (index == FILTER_SLOT) {
            if (!moveItemStackTo(stack, 1, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, FILTER_SLOT, FILTER_SLOT + 1, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
