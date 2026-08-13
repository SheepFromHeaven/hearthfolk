package com.sheepfromheaven.minenow.storage.inventory;

import com.sheepfromheaven.minenow.core.MineNow;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import com.sheepfromheaven.minenow.structure.inventory.StructureFilterMenu;
import com.sheepfromheaven.minenow.dominion.inventory.DominionMenu;
import com.sheepfromheaven.minenow.structure.field.inventory.FieldMarkerMenu;

public class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(net.minecraft.core.registries.Registries.MENU, MineNow.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<FilterMenu>> FILTER_TYPE =
            MENUS.register("filter", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(
                    (net.neoforged.neoforge.network.IContainerFactory<FilterMenu>) FilterMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<StructureFilterMenu>> STRUCTURE_FILTER_TYPE =
            MENUS.register("structure_filter", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(
                    (net.neoforged.neoforge.network.IContainerFactory<StructureFilterMenu>) StructureFilterMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<DominionMenu>> DOMINION_TYPE =
            MENUS.register("dominion", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(
                    (net.neoforged.neoforge.network.IContainerFactory<DominionMenu>) DominionMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<FieldMarkerMenu>> FIELD_MARKER_TYPE =
            MENUS.register("field_marker", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(
                    (net.neoforged.neoforge.network.IContainerFactory<FieldMarkerMenu>) FieldMarkerMenu::new));

    public static void register(IEventBus modBus) {
        MENUS.register(modBus);
    }
}
