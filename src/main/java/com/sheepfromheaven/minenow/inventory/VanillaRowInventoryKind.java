package com.sheepfromheaven.minenow.inventory;

import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * One reusable "vanilla row" inventory shape (tool slot count, general grid size), bound to a single
 * registered {@link MenuType}. Declare one of these per distinct on-screen shape you need (see
 * {@link ModMenus} for the registration), then call {@link #createView} with the real per-instance
 * containers whenever you want to open it for a player - no menu/screen subclass needed.
 *
 * <pre>{@code
 * player.openMenu(ModMenus.WOODCUTTER_INVENTORY.createView(
 *         inhabitant.getName(),
 *         new Container[] { new EquipmentSlotContainer(inhabitant, EquipmentSlot.MAINHAND) },
 *         inhabitant.getInventory()));
 * }</pre>
 */
public final class VanillaRowInventoryKind {
    private final DeferredHolder<MenuType<?>, MenuType<VanillaRowMenu>> menuType;
    private final int columns;
    private final int rows;

    public VanillaRowInventoryKind(DeferredHolder<MenuType<?>, MenuType<VanillaRowMenu>> menuType, int columns, int rows) {
        this.menuType = menuType;
        this.columns = columns;
        this.rows = rows;
    }

    public MenuProvider createView(Component title, Container[] toolContainers, Container generalInventory) {
        return new SimpleMenuProvider(
                (containerId, playerInventory, player) -> new VanillaRowMenu(
                        menuType.get(), containerId, playerInventory, toolContainers, generalInventory, columns, rows),
                title);
    }
}
