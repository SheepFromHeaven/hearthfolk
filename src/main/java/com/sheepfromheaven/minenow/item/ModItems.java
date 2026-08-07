package com.sheepfromheaven.minenow.item;

import com.sheepfromheaven.minenow.MineNow;
import com.sheepfromheaven.minenow.block.ModBlocks;
import com.sheepfromheaven.minenow.entity.ModEntities;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(net.minecraft.core.registries.Registries.ITEM, MineNow.MOD_ID);

    public static final DeferredHolder<Item, Item> TOWN_BANNER = ITEMS.register("town_banner",
            () -> new StandingAndWallBlockItem(
                    ModBlocks.TOWN_BANNER.get(),
                    ModBlocks.TOWN_WALL_BANNER.get(),
                    Direction.DOWN,
                    new Item.Properties()
                            .setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "town_banner")))
                            .stacksTo(1)
                            .rarity(Rarity.UNCOMMON)
            ));

    public static final DeferredHolder<Item, Item> STRUCTURE_MARKER = ITEMS.register("structure_marker",
            () -> new net.minecraft.world.item.BlockItem(
                    ModBlocks.STRUCTURE_MARKER.get(),
                    new Item.Properties()
                            .setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "structure_marker")))
                            .stacksTo(16)
            ));

    public static final DeferredHolder<Item, Item> FISHERY_MARKER = ITEMS.register("fishery_marker",
            () -> new net.minecraft.world.item.BlockItem(
                    ModBlocks.FISHERY_MARKER.get(),
                    new Item.Properties()
                            .setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "fishery_marker")))
                            .stacksTo(16)
            ));

    public static final DeferredHolder<Item, Item> LUMBERYARD_MARKER = ITEMS.register("lumberyard_marker",
            () -> new net.minecraft.world.item.BlockItem(
                    ModBlocks.LUMBERYARD_MARKER.get(),
                    new Item.Properties()
                            .setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "lumberyard_marker")))
                            .stacksTo(16)
            ));

    public static final DeferredHolder<Item, Item> TAVERN_MARKER = ITEMS.register("tavern_marker",
            () -> new net.minecraft.world.item.BlockItem(
                    ModBlocks.TAVERN_MARKER.get(),
                    new Item.Properties()
                            .setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "tavern_marker")))
                            .stacksTo(16)
            ));

    public static final DeferredHolder<Item, Item> STOREHOUSE_MARKER = ITEMS.register("storehouse_marker",
            () -> new net.minecraft.world.item.BlockItem(
                    ModBlocks.STOREHOUSE_MARKER.get(),
                    new Item.Properties()
                            .setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "storehouse_marker")))
                            .stacksTo(16)
            ));

    public static final DeferredHolder<Item, InvitationItem> INVITATION = ITEMS.register("invitation",
            () -> new InvitationItem(
                    new Item.Properties()
                            .setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "invitation")))
                            .stacksTo(16)
            ));

    public static final DeferredHolder<Item, TreeScannerWandItem> TREE_SCANNER_WAND = ITEMS.register("tree_scanner_wand",
            () -> new TreeScannerWandItem(
                    new Item.Properties()
                            .setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "tree_scanner_wand")))
                            .stacksTo(1)
            ));

    public static final DeferredHolder<Item, StructureWandItem> STRUCTURE_WAND = ITEMS.register("structure_wand",
            () -> new StructureWandItem(
                    new Item.Properties()
                            .setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "structure_wand")))
                            .stacksTo(1)
            ));

    public static final DeferredHolder<Item, InhabitantSpawnEggItem> INHABITANT_SPAWN_EGG = ITEMS.register("inhabitant_spawn_egg",
            () -> new InhabitantSpawnEggItem(
                    new Item.Properties()
                            .setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "inhabitant_spawn_egg")))
                            .spawnEgg(ModEntities.INHABITANT.get())
            ));

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
