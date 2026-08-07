package com.sheepfromheaven.minenow.inventory;

import com.sheepfromheaven.minenow.MineNow;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(net.minecraft.core.registries.Registries.MENU, MineNow.MOD_ID);

    // The client reconstructs its own menu purely for slot layout/rendering (legality and real item
    // data always come from the server's own instance), so this dummy just needs the same slot count
    // and grid shape as the real inhabitant inventory. It has to match InhabitantEntity's default
    // capacity - if that capacity ever becomes variable (backpack/pouch items), this needs to
    // become sync-aware too.
    private static final int INHABITANT_INVENTORY_COLUMNS = 2;
    private static final int INHABITANT_INVENTORY_ROWS = 1;

    public static final DeferredHolder<MenuType<?>, MenuType<VanillaRowMenu>> INHABITANT_INVENTORY_TYPE =
            MENUS.register("inhabitant_inventory", () -> new MenuType<>(
                    (containerId, playerInventory) -> new VanillaRowMenu(
                            ModMenus.INHABITANT_INVENTORY_TYPE.get(), containerId, playerInventory,
                            new Container[] {new SimpleContainer(1)},
                            new SimpleContainer(INHABITANT_INVENTORY_COLUMNS * INHABITANT_INVENTORY_ROWS),
                            INHABITANT_INVENTORY_COLUMNS, INHABITANT_INVENTORY_ROWS),
                    FeatureFlags.VANILLA_SET));

    public static final VanillaRowInventoryKind INHABITANT_INVENTORY =
            new VanillaRowInventoryKind(INHABITANT_INVENTORY_TYPE, INHABITANT_INVENTORY_COLUMNS, INHABITANT_INVENTORY_ROWS);

    public static final DeferredHolder<MenuType<?>, MenuType<FilterMenu>> FILTER_TYPE =
            MENUS.register("filter", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(
                    (net.neoforged.neoforge.network.IContainerFactory<FilterMenu>) FilterMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<StructureFilterMenu>> STRUCTURE_FILTER_TYPE =
            MENUS.register("structure_filter", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(
                    (net.neoforged.neoforge.network.IContainerFactory<StructureFilterMenu>) StructureFilterMenu::new));

    public static void register(IEventBus modBus) {
        MENUS.register(modBus);
    }
}
