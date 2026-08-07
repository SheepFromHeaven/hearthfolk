package com.sheepfromheaven.minenow.dominion.townbanner;

import com.sheepfromheaven.minenow.core.MineNow;
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

public class TownBannerItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, MineNow.MOD_ID);

    public static final DeferredHolder<Item, Item> TOWN_BANNER = ITEMS.register("town_banner",
            () -> new StandingAndWallBlockItem(
                    TownBannerBlocks.TOWN_BANNER.get(),
                    TownBannerBlocks.TOWN_WALL_BANNER.get(),
                    Direction.DOWN,
                    new Item.Properties()
                            .setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "town_banner")))
                            .stacksTo(1)
                            .rarity(Rarity.UNCOMMON)
            ));

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
