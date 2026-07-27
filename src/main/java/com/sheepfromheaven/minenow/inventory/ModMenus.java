package com.sheepfromheaven.minenow.inventory;

import com.sheepfromheaven.minenow.MineNow;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, MineNow.MOD_ID);

    // The client reconstructs its own menu purely for slot layout/rendering (legality and real item
    // data always come from the server's own instance), so this dummy just needs the same slot count
    // and grid shape as the real inhabitant inventory. It has to match InhabitantEntity's default
    // capacity - if that capacity ever becomes variable (backpack/pouch items), this needs to
    // become sync-aware too.
    private static final int INHABITANT_INVENTORY_COLUMNS = 2;
    private static final int INHABITANT_INVENTORY_ROWS = 1;

    public static final RegistryObject<MenuType<VanillaRowMenu>> INHABITANT_INVENTORY_TYPE =
            MENUS.register("inhabitant_inventory", () -> new MenuType<>(
                    (containerId, playerInventory) -> new VanillaRowMenu(
                            ModMenus.INHABITANT_INVENTORY_TYPE.get(), containerId, playerInventory,
                            new Container[] {new SimpleContainer(1)},
                            new SimpleContainer(INHABITANT_INVENTORY_COLUMNS * INHABITANT_INVENTORY_ROWS),
                            INHABITANT_INVENTORY_COLUMNS, INHABITANT_INVENTORY_ROWS),
                    FeatureFlags.VANILLA_SET));

    public static final VanillaRowInventoryKind INHABITANT_INVENTORY =
            new VanillaRowInventoryKind(INHABITANT_INVENTORY_TYPE, INHABITANT_INVENTORY_COLUMNS, INHABITANT_INVENTORY_ROWS);

    public static void register(BusGroup modBusGroup) {
        MENUS.register(modBusGroup);
    }
}
