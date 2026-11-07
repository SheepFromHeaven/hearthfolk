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
 * Real drag-and-drop menu for an inhabitant's own 2-slot inventory plus their main-hand and off-hand
 * equipment slots (the pickaxe/axe/... and the off-hand torch stack a miner carries - see {@link
 * com.sheepfromheaven.minenow.jobs.miner.MinerJob}), laid out at the coordinates {@code
 * inhabitant_screen.json} declares (see {@link com.sheepfromheaven.minenow.inhabitant.client.InhabitantScreen}).
 */
public class InhabitantInventoryMenu extends AbstractContainerMenu {

    /** Server-safe (no ResourceManager dependency) - see {@link ScreenSpecLoader#fromClasspath}. */
    public static final ScreenSpec SPEC = ScreenSpecLoader.fromClasspath("minenow", "inhabitant_screen");

    private final int entityId;
    private final int inhabitantInventoryEnd;
    private final int mainHandEnd;
    private final int offHandEnd;

    /** Server-side: opened for {@code inhabitant}'s own containers. */
    public InhabitantInventoryMenu(int containerId, Inventory playerInventory, InhabitantEntity inhabitant) {
        super(InhabitantMenus.INHABITANT_INVENTORY_TYPE.get(), containerId);
        this.entityId = inhabitant.getId();
        Container inventory = inhabitant.getInventory();
        Container mainHand = new EquipmentSlotContainer(inhabitant, EquipmentSlot.MAINHAND);
        Container offHand = new EquipmentSlotContainer(inhabitant, EquipmentSlot.OFFHAND);
        this.inhabitantInventoryEnd = inventory.getContainerSize();
        this.mainHandEnd = inhabitantInventoryEnd + mainHand.getContainerSize();
        this.offHandEnd = mainHandEnd + offHand.getContainerSize();
        addSlots(playerInventory, inventory, mainHand, offHand);
    }

    /** Client-side reconstruction: only the entity id arrives via extra data; slot contents sync afterward as usual. */
    public InhabitantInventoryMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        super(InhabitantMenus.INHABITANT_INVENTORY_TYPE.get(), containerId);
        this.entityId = extraData.readVarInt();
        Container inventory = new SimpleContainer(InhabitantEntity.INVENTORY_SIZE);
        Container mainHand = new SimpleContainer(1);
        Container offHand = new SimpleContainer(1);
        this.inhabitantInventoryEnd = inventory.getContainerSize();
        this.mainHandEnd = inhabitantInventoryEnd + mainHand.getContainerSize();
        this.offHandEnd = mainHandEnd + offHand.getContainerSize();
        addSlots(playerInventory, inventory, mainHand, offHand);
    }

    private void addSlots(Inventory playerInventory, Container inventory, Container mainHand, Container offHand) {
        var container = SPEC.container;
        SpecSlots.forArea(container.area("inventory"), inventory).forEach(this::addSlot);
        SpecSlots.forArea(container.area("inventory_main_hand"), mainHand).forEach(this::addSlot);
        SpecSlots.forArea(container.area("inventory_off_hand"), offHand).forEach(this::addSlot);
        SpecSlots.forPlayerInventory(container.area("inventory_player"), playerInventory).forEach(this::addSlot);
        SpecSlots.forPlayerHotbar(container.area("player_hotbar"), playerInventory).forEach(this::addSlot);
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
        if (index < offHandEnd) {
            if (!moveItemStackTo(stack, offHandEnd, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, offHandEnd, false)) {
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
