package com.sheepfromheaven.minenow.structure.field.client;

import com.sheepfromheaven.minenow.structure.field.inventory.FieldMarkerMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import sheepfromheaven.screenspec.runtime.ScreenSpecLoader;
import sheepfromheaven.screenspec.runtime.SpecContainerScreen;

/** Real drag-and-drop screen for a field marker's seed-template slots - see {@link FieldMarkerMenu}. */
public class FieldMarkerScreen extends SpecContainerScreen<FieldMarkerMenu> {
    public FieldMarkerScreen(FieldMarkerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, ScreenSpecLoader.fromResource(
                Minecraft.getInstance().getResourceManager(), "minenow", "field_marker_screen"));
    }
}
