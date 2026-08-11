package com.sheepfromheaven.minenow.structure;

import com.sheepfromheaven.minenow.core.MineNow;
import com.sheepfromheaven.minenow.structure.item.StructureWandItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class StructureItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, MineNow.MOD_ID);

    public static final DeferredHolder<Item, Item> FISHERY_MARKER = ITEMS.register("fishery_marker",
            () -> new net.minecraft.world.item.BlockItem(
                    StructureBlocks.FISHERY_MARKER.get(),
                    new Item.Properties()
                            .setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "fishery_marker")))
                            .stacksTo(16)
            ));

    public static final DeferredHolder<Item, Item> LUMBERYARD_MARKER = ITEMS.register("lumberyard_marker",
            () -> new net.minecraft.world.item.BlockItem(
                    StructureBlocks.LUMBERYARD_MARKER.get(),
                    new Item.Properties()
                            .setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "lumberyard_marker")))
                            .stacksTo(16)
            ));

    public static final DeferredHolder<Item, Item> TAVERN_MARKER = ITEMS.register("tavern_marker",
            () -> new net.minecraft.world.item.BlockItem(
                    StructureBlocks.TAVERN_MARKER.get(),
                    new Item.Properties()
                            .setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "tavern_marker")))
                            .stacksTo(16)
            ));

    public static final DeferredHolder<Item, Item> STOREHOUSE_MARKER = ITEMS.register("storehouse_marker",
            () -> new net.minecraft.world.item.BlockItem(
                    StructureBlocks.STOREHOUSE_MARKER.get(),
                    new Item.Properties()
                            .setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "storehouse_marker")))
                            .stacksTo(16)
            ));

    public static final DeferredHolder<Item, Item> MINE_MARKER = ITEMS.register("mine_marker",
            () -> new net.minecraft.world.item.BlockItem(
                    StructureBlocks.MINE_MARKER.get(),
                    new Item.Properties()
                            .setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "mine_marker")))
                            .stacksTo(16)
            ));

    public static final DeferredHolder<Item, Item> NURSERY_MARKER = ITEMS.register("nursery_marker",
            () -> new net.minecraft.world.item.BlockItem(
                    StructureBlocks.NURSERY_MARKER.get(),
                    new Item.Properties()
                            .setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "nursery_marker")))
                            .stacksTo(16)
            ));

    public static final DeferredHolder<Item, Item> CHARCOAL_KILN_MARKER = ITEMS.register("charcoal_kiln_marker",
            () -> new net.minecraft.world.item.BlockItem(
                    StructureBlocks.CHARCOAL_KILN_MARKER.get(),
                    new Item.Properties()
                            .setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "charcoal_kiln_marker")))
                            .stacksTo(16)
            ));

    public static final DeferredHolder<Item, Item> FARM_MARKER = ITEMS.register("farm_marker",
            () -> new net.minecraft.world.item.BlockItem(
                    StructureBlocks.FARM_MARKER.get(),
                    new Item.Properties()
                            .setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "farm_marker")))
                            .stacksTo(16)
            ));

    public static final DeferredHolder<Item, Item> MINE_CLAIM_MARKER = ITEMS.register("mine_claim_marker",
            () -> new net.minecraft.world.item.BlockItem(
                    StructureBlocks.MINE_CLAIM_MARKER.get(),
                    new Item.Properties()
                            .setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "mine_claim_marker")))
                            .stacksTo(16)
            ));

    public static final DeferredHolder<Item, Item> FIELD_MARKER = ITEMS.register("field_marker",
            () -> new net.minecraft.world.item.BlockItem(
                    StructureBlocks.FIELD_MARKER.get(),
                    new Item.Properties()
                            .setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "field_marker")))
                            .stacksTo(16)
            ));

    public static final DeferredHolder<Item, Item> STRUCTURE_BASE = ITEMS.register("structure_base",
            () -> new Item(
                    new Item.Properties()
                            .setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "structure_base")))
            ));

    public static final DeferredHolder<Item, StructureWandItem> STRUCTURE_WAND = ITEMS.register("structure_wand",
            () -> new StructureWandItem(
                    new Item.Properties()
                            .setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "structure_wand")))
                            .stacksTo(1)
            ));

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
