package com.sheepfromheaven.minenow.structure.field.inventory;

import com.sheepfromheaven.minenow.storage.inventory.ModMenus;
import com.sheepfromheaven.minenow.structure.field.CropKind;
import com.sheepfromheaven.minenow.structure.field.block.FieldMarkerBlockEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import sheepfromheaven.screenspec.runtime.ScreenSpec;
import sheepfromheaven.screenspec.runtime.ScreenSpecLoader;
import sheepfromheaven.screenspec.runtime.SpecSlots;

import javax.annotation.Nullable;
import java.util.ArrayList;

/**
 * The field marker's 2x2 seed-template slots: whichever seeds a player drops in here are the crops a
 * farmer may plant on this field (see {@link FieldMarkerBlockEntity#getAllowedCrops}), replacing the
 * old right-click cycle/toggle mechanic.
 *
 * <p>Mirrors {@code FilterMenu}'s non-consuming template pattern: the seed slots (backed by a
 * throwaway {@link SimpleContainer}, never the block entity's own data directly) never hold - or
 * move - a real item. {@link #clicked} and {@link #quickMoveStack} intercept every interaction with
 * them and only ever copy the clicked stack's identity, leaving the real stack (in hand or in the
 * player's inventory) completely untouched. {@link #removed} writes those identities back onto the
 * block entity.
 */
public class FieldMarkerMenu extends AbstractContainerMenu {
    private static final int SEED_SLOTS = FieldMarkerBlockEntity.SLOT_COUNT;

    /** Server-safe (no ResourceManager dependency) - see {@link ScreenSpecLoader#fromClasspath}. */
    public static final ScreenSpec SPEC = ScreenSpecLoader.fromClasspath("minenow", "field_marker_screen");

    private final SimpleContainer templates = new SimpleContainer(SEED_SLOTS);
    @Nullable
    private final FieldMarkerBlockEntity blockEntity;

    /** Server-side: seeded from the real marker's current templates. */
    public FieldMarkerMenu(int containerId, Inventory playerInventory, FieldMarkerBlockEntity blockEntity) {
        super(ModMenus.FIELD_MARKER_TYPE.get(), containerId);
        this.blockEntity = blockEntity;
        for (int i = 0; i < SEED_SLOTS; i++) {
            templates.setItem(i, blockEntity.getTemplate(i).copy());
        }
        addSlots(playerInventory);
    }

    /** Client-side reconstruction: slot contents sync afterward as usual. */
    public FieldMarkerMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        super(ModMenus.FIELD_MARKER_TYPE.get(), containerId);
        this.blockEntity = null;
        addSlots(playerInventory);
    }

    private void addSlots(Inventory playerInventory) {
        var container = SPEC.container;
        SpecSlots.forArea(container.area("seed_slots"), templates).forEach(this::addSlot);
        SpecSlots.forPlayerInventory(container.area("player_inv"), playerInventory).forEach(this::addSlot);
        SpecSlots.forPlayerHotbar(container.area("player_hotbar"), playerInventory).forEach(this::addSlot);
    }

    /**
     * Every interaction with a seed-template slot is intercepted so it can never transfer a real
     * item - same handling as {@code FilterMenu#clicked}.
     */
    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < SEED_SLOTS) {
            switch (clickType) {
                case PICKUP -> templates.setItem(slotId, toTemplate(getCarried()));
                case SWAP -> templates.setItem(slotId, toTemplate(player.getInventory().getItem(button)));
                case THROW, CLONE, QUICK_CRAFT -> { /* never let vanilla touch a real stack here */ }
                default -> super.clicked(slotId, button, clickType, player);
            }
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    /** Shift-click: same non-consuming capture as {@code FilterMenu#quickMoveStack}. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < SEED_SLOTS) {
            templates.setItem(index, ItemStack.EMPTY);
            return ItemStack.EMPTY;
        }
        var slot = slots.get(index);
        if (slot != null && slot.hasItem()) {
            int emptySlot = firstEmptyTemplateSlot();
            if (emptySlot >= 0) {
                templates.setItem(emptySlot, toTemplate(slot.getItem()));
            }
        }
        return ItemStack.EMPTY;
    }

    private int firstEmptyTemplateSlot() {
        for (int i = 0; i < SEED_SLOTS; i++) {
            if (templates.getItem(i).isEmpty()) return i;
        }
        return -1;
    }

    /** A template only ever remembers a valid crop seed's identity at count 1 - anything else (an
     * empty cursor, or an item that isn't a recognized seed) clears the slot instead. */
    private ItemStack toTemplate(ItemStack source) {
        if (source.isEmpty() || CropKind.fromSeedItem(source.getItem()).isEmpty()) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(source.getItem());
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (player.level().isClientSide() || blockEntity == null) return;

        var savedTemplates = new ArrayList<ItemStack>();
        for (int i = 0; i < templates.getContainerSize(); i++) {
            savedTemplates.add(templates.getItem(i).copy());
        }
        blockEntity.setTemplates(savedTemplates);
        templates.clearContent();
    }
}
