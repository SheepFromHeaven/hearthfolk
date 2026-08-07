package com.sheepfromheaven.minenow.inhabitant;

import com.sheepfromheaven.minenow.core.MineNow;
import com.sheepfromheaven.minenow.inhabitant.item.InhabitantSpawnEggItem;
import com.sheepfromheaven.minenow.inhabitant.item.InvitationItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class InhabitantItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, MineNow.MOD_ID);

    public static final DeferredHolder<Item, InvitationItem> INVITATION = ITEMS.register("invitation",
            () -> new InvitationItem(
                    new Item.Properties()
                            .setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "invitation")))
                            .stacksTo(16)
            ));

    public static final DeferredHolder<Item, InhabitantSpawnEggItem> INHABITANT_SPAWN_EGG = ITEMS.register("inhabitant_spawn_egg",
            () -> new InhabitantSpawnEggItem(
                    new Item.Properties()
                            .setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "inhabitant_spawn_egg")))
                            .spawnEgg(ModEntities.INHABITANT.get())
            ));

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
