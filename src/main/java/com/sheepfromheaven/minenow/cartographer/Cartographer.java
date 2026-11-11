package com.sheepfromheaven.minenow.cartographer;

import com.sheepfromheaven.minenow.MineNow;
import com.sheepfromheaven.minenow.villager.CraftResults;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The vanilla cartographer at its cartography table: its tool is the ink and quill, its work a map of its village ({@link CartographerMaps}). */
public final class Cartographer {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MineNow.MOD_ID);

    private static final DeferredRegister<net.minecraft.world.item.crafting.RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(net.minecraft.core.registries.Registries.RECIPE_SERIALIZER, MineNow.MOD_ID);
    static {
        RECIPE_SERIALIZERS.register("switched_shapeless", () -> SwitchedShapelessRecipe.SERIALIZER);
    }

    public static final int QUILL_USES = 32;
    public static final DeferredItem<Item> INK_AND_QUILL = ITEMS.registerItem("ink_and_quill", Item::new, properties -> properties.durability(QUILL_USES));

    private Cartographer() {}

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        RECIPE_SERIALIZERS.register(modBus);
        modBus.addListener(BuildCreativeModeTabContentsEvent.class, event -> {
            if (event.getTabKey().equals(CreativeModeTabs.TOOLS_AND_UTILITIES)) event.accept(INK_AND_QUILL.get());
        });
        CraftResults.register(item -> item == Items.FILLED_MAP, new CartographerMaps());
    }
}
