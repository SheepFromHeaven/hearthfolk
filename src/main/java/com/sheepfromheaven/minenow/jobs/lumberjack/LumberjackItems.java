package com.sheepfromheaven.minenow.jobs.lumberjack;

import com.sheepfromheaven.minenow.core.MineNow;
import com.sheepfromheaven.minenow.jobs.lumberjack.item.TreeScannerWandItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class LumberjackItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, MineNow.MOD_ID);

    public static final DeferredHolder<Item, TreeScannerWandItem> TREE_SCANNER_WAND = ITEMS.register("tree_scanner_wand",
            () -> new TreeScannerWandItem(
                    new Item.Properties()
                            .setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "tree_scanner_wand")))
                            .stacksTo(1)
            ));

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
