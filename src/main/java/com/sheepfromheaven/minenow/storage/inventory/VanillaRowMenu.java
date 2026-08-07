package com.sheepfromheaven.minenow.storage.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A small inventory that reuses vanilla's own chest texture instead of needing a custom background:
 * some number of leading one-slot "tool" containers (e.g. an equipment slot adapter, mirroring the
 * player's offhand) on the first row, a one-cell gap for visual separation, then a
 * {@code columns} x {@code rows} grid of general slots (as many of them as the backing container
 * actually has). Everything sits on vanilla's own 18px slot grid (9 columns wide, up to 6 rows tall,
 * matching {@code generic_54.png}) so {@link com.sheepfromheaven.minenow.storage.client.VanillaRowBackground}
 * can draw a pixel-accurate background by reusing the real texture and hiding whichever grid cells
 * aren't turned into slots here.
 *
 * <p>This class is concrete and needs no subclass - see {@link VanillaRowInventoryKind} for the
 * usual way to declare and open one of these without writing any menu/screen code at all.
 */
public class VanillaRowMenu extends AbstractContainerMenu {
    public static final int GRID_X = 8;
    public static final int SLOT_Y = 18;
    public static final int GRID_CELLS = 9;
    public static final int GAP_CELLS = 1;

    private final Container[] toolContainers;
    private final Container generalInventory;
    private final int generalCapacity;
    private final int generalGridStart;
    private final int columns;
    private final int rows;

    /** Convenience for the common case: everything fits on a single row. */
    public VanillaRowMenu(MenuType<?> type, int containerId, Inventory playerInventory,
                           Container[] toolContainers, Container generalInventory) {
        this(type, containerId, playerInventory, toolContainers, generalInventory,
                GRID_CELLS - toolContainers.length - GAP_CELLS, 1);
    }

    public VanillaRowMenu(MenuType<?> type, int containerId, Inventory playerInventory,
                           Container[] toolContainers, Container generalInventory, int columns, int rows) {
        super(type, containerId);
        this.toolContainers = toolContainers;
        this.generalInventory = generalInventory;
        this.generalCapacity = generalInventory.getContainerSize();
        this.generalGridStart = toolContainers.length + GAP_CELLS;
        this.columns = columns;
        this.rows = rows;
        if (generalGridStart + columns > GRID_CELLS) {
            throw new IllegalArgumentException(
                    "Row layout (" + toolContainers.length + " tool slots + gap + " + columns
                            + " columns) doesn't fit in one vanilla chest row");
        }
        if (generalCapacity > columns * rows) {
            throw new IllegalArgumentException(
                    "General inventory (" + generalCapacity + " slots) doesn't fit in a " + columns + "x" + rows + " grid");
        }
        generalInventory.startOpen(playerInventory.player);

        for (int i = 0; i < toolContainers.length; i++) {
            addSlot(new Slot(toolContainers[i], 0, gridX(i), SLOT_Y));
        }
        for (int i = 0; i < generalCapacity; i++) {
            int col = i % columns;
            int row = i / columns;
            addSlot(new Slot(generalInventory, i, gridX(generalGridStart + col), SLOT_Y + row * 18));
        }
        addStandardInventorySlots(playerInventory, GRID_X, SLOT_Y + rows * 18 + 13);
    }

    public static int gridX(int gridIndex) {
        return GRID_X + gridIndex * 18;
    }

    public int getToolSlotCount() {
        return toolContainers.length;
    }

    public int getGeneralGridStart() {
        return generalGridStart;
    }

    public int getGeneralCapacity() {
        return generalCapacity;
    }

    public int getColumns() {
        return columns;
    }

    public int getRows() {
        return rows;
    }

    @Override
    public boolean stillValid(Player player) {
        return generalInventory.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        var result = ItemStack.EMPTY;
        var slot = slots.get(index);
        if (slot != null && slot.hasItem()) {
            var stack = slot.getItem();
            result = stack.copy();
            int generalEnd = toolContainers.length + generalCapacity;
            boolean hasToolSlots = toolContainers.length > 0;
            if (index < generalEnd) {
                boolean isGeneralSlot = index >= toolContainers.length;
                boolean movedToTool = isGeneralSlot && hasToolSlots
                        && moveItemStackTo(stack, 0, toolContainers.length, false);
                if (!movedToTool && !moveItemStackTo(stack, generalEnd, slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // From the player's own inventory: try the tool slot first too, same as the reverse
                // direction above - otherwise shift-clicking a tool in always lands it in the general
                // grid instead of equipping it.
                boolean movedToTool = hasToolSlots && moveItemStackTo(stack, 0, toolContainers.length, false);
                if (!movedToTool && !moveItemStackTo(stack, toolContainers.length, generalEnd, false)) {
                    return ItemStack.EMPTY;
                }
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
        generalInventory.stopOpen(player);
    }
}
