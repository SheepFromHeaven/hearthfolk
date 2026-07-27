package com.sheepfromheaven.minenow.item;

import com.sheepfromheaven.minenow.MineNow;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MineNow.MOD_ID);

    public static final RegistryObject<CreativeModeTab> MINENOW_TAB = CREATIVE_MODE_TABS.register("minenow",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.minenow"))
                    .icon(() -> ModItems.TOWN_BANNER.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.TOWN_BANNER.get());
                        output.accept(ModItems.STRUCTURE_MARKER.get());
                        output.accept(ModItems.INVITATION.get());
                    })
                    .build());

    public static void register(BusGroup modBusGroup) {
        CREATIVE_MODE_TABS.register(modBusGroup);
    }
}
