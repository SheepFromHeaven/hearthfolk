package com.sheepfromheaven.minenow.item;

import com.sheepfromheaven.minenow.MineNow;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MineNow.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MINENOW_TAB = CREATIVE_MODE_TABS.register("minenow",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.minenow"))
                    .icon(() -> ModItems.TOWN_BANNER.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.TOWN_BANNER.get());
                        output.accept(ModItems.STRUCTURE_MARKER.get());
                        output.accept(ModItems.INVITATION.get());
                        output.accept(ModItems.TREE_SCANNER_WAND.get());
                    })
                    .build());

    public static void register(IEventBus modBus) {
        CREATIVE_MODE_TABS.register(modBus);
    }
}
