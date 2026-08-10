package com.sheepfromheaven.minenow.core;

import com.sheepfromheaven.minenow.core.MineNow;
import com.sheepfromheaven.minenow.dominion.townbanner.TownBannerItems;
import com.sheepfromheaven.minenow.structure.StructureItems;
import com.sheepfromheaven.minenow.inhabitant.InhabitantItems;
import com.sheepfromheaven.minenow.pathfinding.PathfindingItems;
import com.sheepfromheaven.minenow.jobs.lumberjack.LumberjackItems;
import com.sheepfromheaven.minenow.storage.StorageItems;
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
                    .icon(() -> TownBannerItems.TOWN_BANNER.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(TownBannerItems.TOWN_BANNER.get());
                        output.accept(StructureItems.STRUCTURE_BASE.get());
                        output.accept(StructureItems.FISHERY_MARKER.get());
                        output.accept(StructureItems.LUMBERYARD_MARKER.get());
                        output.accept(StructureItems.TAVERN_MARKER.get());
                        output.accept(StructureItems.STOREHOUSE_MARKER.get());
                        output.accept(StructureItems.MINE_MARKER.get());
                        output.accept(StructureItems.NURSERY_MARKER.get());
                        output.accept(StructureItems.CHARCOAL_KILN_MARKER.get());
                        output.accept(StructureItems.FARM_MARKER.get());
                        output.accept(StructureItems.FIELD_MARKER.get());
                        output.accept(InhabitantItems.INVITATION.get());
                        output.accept(LumberjackItems.TREE_SCANNER_WAND.get());
                        output.accept(StructureItems.STRUCTURE_WAND.get());
                        output.accept(InhabitantItems.INHABITANT_SPAWN_EGG.get());
                        output.accept(PathfindingItems.PATH_NODE_WAND.get());
                        output.accept(StorageItems.FILTER.get());
                    })
                    .build());

    public static void register(IEventBus modBus) {
        CREATIVE_MODE_TABS.register(modBus);
    }
}
