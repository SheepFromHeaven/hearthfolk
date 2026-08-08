package com.sheepfromheaven.minenow.inhabitant.inventory;

import com.sheepfromheaven.minenow.inhabitant.InhabitantEntity;
import com.sheepfromheaven.minenow.inhabitant.InhabitantMenus;
import com.sheepfromheaven.minenow.storage.inventory.EquipmentSlotContainer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import sheepfromheaven.screenspec.runtime.ScreenSpec;
import sheepfromheaven.screenspec.runtime.ScreenSpecLoader;
import sheepfromheaven.screenspec.runtime.SpecSlots;

/**
 * Real drag-and-drop menu for an inhabitant's own 2-slot inventory plus their main-hand equipment
 * slot, laid out at the coordinates {@code inhabitant_screen.json} declares (see {@link
 * com.sheepfromheaven.minenow.inhabitant.client.InhabitantScreen}). No hotbar area is included -
 * only the player's own main inventory grid, per the current spec.
 */
public class InhabitantInventoryMenu extends AbstractContainerMenu {

    /** Server-safe (no ResourceManager dependency) - see {@link ScreenSpecLoader#fromClasspath}. */
    public static final ScreenSpec SPEC = ScreenSpecLoader.fromClasspath("minenow", "inhabitant_screen");

    private final int entityId;
    private final int inhabitantInventoryEnd;
    private final int mainHandEnd;

    /** Server-side: opened for {@code inhabitant}'s own containers. */
    public InhabitantInventoryMenu(int containerId, Inventory playerInventory, InhabitantEntity inhabitant) {
        super(InhabitantMenus.INHABITANT_INVENTORY_TYPE.get(), containerId);
        this.entityId = inhabitant.getId();
        Container inventory = inhabitant.getInventory();
        Container mainHand = new EquipmentSlotContainer(inhabitant, EquipmentSlot.MAINHAND);
        this.inhabitantInventoryEnd = inventory.getContainerSize();
        this.mainHandEnd = inhabitantInventoryEnd + mainHand.getContainerSize();
        addSlots(playerInventory, inventory, mainHand);
    }

    /** Client-side reconstruction: only the entity id arrives via extra data; slot contents sync afterward as usual. */
    public InhabitantInventoryMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        super(InhabitantMenus.INHABITANT_INVENTORY_TYPE.get(), containerId);
        this.entityId = extraData.readVarInt();
        Container inventory = new SimpleContainer(InhabitantEntity.INVENTORY_SIZE);
        Container mainHand = new SimpleContainer(1);
        this.inhabitantInventoryEnd = inventory.getContainerSize();
        this.mainHandEnd = inhabitantInventoryEnd + mainHand.getContainerSize();
        addSlots(playerInventory, inventory, mainHand);
    }

    private void addSlots(Inventory playerInventory, Container inventory, Container mainHand) {
        var container = SPEC.container;
        SpecSlots.forArea(container.area("inventory"), inventory).forEach(this::addSlot);
        SpecSlots.forArea(container.area("inventory_main_hand"), mainHand).forEach(this::addSlot);
        SpecSlots.forPlayerInventory(container.area("inventory_player"), playerInventory).forEach(this::addSlot);
    }

    public int entityId() {
        return entityId;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        var slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;

        var stack = slot.getItem();
        var result = stack.copy();
        if (index < mainHandEnd) {
            if (!moveItemStackTo(stack, mainHandEnd, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, mainHandEnd, false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return result;
    }
}
