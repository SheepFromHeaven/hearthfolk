package com.sheepfromheaven.minenow.inhabitant;

import com.sheepfromheaven.minenow.core.MineNow;
import com.sheepfromheaven.minenow.inhabitant.inventory.InhabitantInventoryMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.network.IContainerFactory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class InhabitantMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, MineNow.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<InhabitantInventoryMenu>> INHABITANT_INVENTORY_TYPE =
            MENUS.register("inhabitant_inventory", () -> IMenuTypeExtension.create(
                    (IContainerFactory<InhabitantInventoryMenu>) InhabitantInventoryMenu::new));

    public static void register(IEventBus modBus) {
        MENUS.register(modBus);
    }
}
