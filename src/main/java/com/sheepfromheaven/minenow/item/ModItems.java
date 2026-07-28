package com.sheepfromheaven.minenow.item;

import com.sheepfromheaven.minenow.MineNow;
import com.sheepfromheaven.minenow.block.ModBlocks;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MineNow.MOD_ID);

    public static final RegistryObject<Item> TOWN_BANNER = ITEMS.register("town_banner",
            () -> new StandingAndWallBlockItem(
                    ModBlocks.TOWN_BANNER.get(),
                    ModBlocks.TOWN_WALL_BANNER.get(),
                    Direction.DOWN,
                    new Item.Properties()
                            .setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "town_banner")))
                            .stacksTo(1)
                            .rarity(Rarity.UNCOMMON)
            ));

    public static final RegistryObject<Item> STRUCTURE_MARKER = ITEMS.register("structure_marker",
            () -> new net.minecraft.world.item.BlockItem(
                    ModBlocks.STRUCTURE_MARKER.get(),
                    new Item.Properties()
                            .setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "structure_marker")))
                            .stacksTo(16)
            ));

    public static final RegistryObject<InvitationItem> INVITATION = ITEMS.register("invitation",
            () -> new InvitationItem(
                    new Item.Properties()
                            .setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "invitation")))
                            .stacksTo(16)
            ));

    public static final RegistryObject<TreeScannerWandItem> TREE_SCANNER_WAND = ITEMS.register("tree_scanner_wand",
            () -> new TreeScannerWandItem(
                    new Item.Properties()
                            .setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "tree_scanner_wand")))
                            .stacksTo(1)
            ));

    public static void register(BusGroup modBusGroup) {
        ITEMS.register(modBusGroup);
    }
}
