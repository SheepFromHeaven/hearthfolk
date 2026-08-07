package com.sheepfromheaven.minenow.pathfinding;

import com.sheepfromheaven.minenow.core.MineNow;
import com.sheepfromheaven.minenow.pathfinding.item.PathNodeWandItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class PathfindingItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, MineNow.MOD_ID);

    public static final DeferredHolder<Item, PathNodeWandItem> PATH_NODE_WAND = ITEMS.register("path_node_wand",
            () -> new PathNodeWandItem(
                    new Item.Properties()
                            .setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "path_node_wand")))
                            .stacksTo(1)
            ));

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
