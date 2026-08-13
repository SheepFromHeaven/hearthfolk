package com.sheepfromheaven.minenow.structure.field.inventory;

import com.sheepfromheaven.minenow.storage.inventory.ModMenus;
import com.sheepfromheaven.minenow.structure.field.CropKind;
import com.sheepfromheaven.minenow.structure.field.block.FieldMarkerBlockEntity;
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
 * The field marker's 2x2 seed-template slots: whichever seeds a player drops in here are the crops a
 * farmer may plant on this field (see {@link FieldMarkerBlockEntity#getAllowedCrops}), replacing the
 * old right-click cycle/toggle mechanic.
 */
public class FieldMarkerMenu extends AbstractContainerMenu {
    private static final int SEED_SLOTS = FieldMarkerBlockEntity.SLOT_COUNT;

    /** Server-safe (no ResourceManager dependency) - see {@link ScreenSpecLoader#fromClasspath}. */
    public static final ScreenSpec SPEC = ScreenSpecLoader.fromClasspath("minenow", "field_marker_screen");

    /** Server-side: bound to the real marker's seed slots. */
    public FieldMarkerMenu(int containerId, Inventory playerInventory, FieldMarkerBlockEntity blockEntity) {
        super(ModMenus.FIELD_MARKER_TYPE.get(), containerId);
        addSlots(playerInventory, blockEntity);
    }

    /** Client-side reconstruction: slot contents sync afterward as usual. */
    public FieldMarkerMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        super(ModMenus.FIELD_MARKER_TYPE.get(), containerId);
        addSlots(playerInventory, new SimpleContainer(SEED_SLOTS));
    }

    private void addSlots(Inventory playerInventory, net.minecraft.world.Container seedContainer) {
        var container = SPEC.container;
        var seedArea = container.area("seed_slots");
        for (int i = 0; i < SEED_SLOTS; i++) {
            int col = i % 2;
            int row = i / 2;
            addSlot(new Slot(seedContainer, i, seedArea.slotX(col), seedArea.slotY(row)) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return CropKind.fromSeedItem(stack.getItem()).isPresent();
                }
            });
        }
        SpecSlots.forPlayerInventory(container.area("player_inv"), playerInventory).forEach(this::addSlot);
        SpecSlots.forPlayerHotbar(container.area("player_hotbar"), playerInventory).forEach(this::addSlot);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        var slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;
        var stack = slot.getItem();
        var result = stack.copy();
        if (index < SEED_SLOTS) {
            if (!moveItemStackTo(stack, SEED_SLOTS, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, SEED_SLOTS, false)) {
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
