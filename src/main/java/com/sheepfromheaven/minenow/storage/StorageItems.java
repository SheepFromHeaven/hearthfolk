package com.sheepfromheaven.minenow.storage;

import com.sheepfromheaven.minenow.core.MineNow;
import com.sheepfromheaven.minenow.storage.item.FilterItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class StorageItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, MineNow.MOD_ID);

    public static final DeferredHolder<Item, FilterItem> FILTER = ITEMS.register("filter",
            () -> new FilterItem(
                    new Item.Properties()
                            .setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "filter")))
                            .stacksTo(1)
            ));

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
