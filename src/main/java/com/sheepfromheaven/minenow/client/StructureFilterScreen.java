package com.sheepfromheaven.minenow.client;

import com.sheepfromheaven.minenow.inventory.StructureFilterMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import sheepfromheaven.screenspec.runtime.ScreenSpecLoader;
import sheepfromheaven.screenspec.runtime.SpecContainerScreen;

/** Real drag-and-drop screen for a storehouse's filter slot - see {@link StructureFilterMenu}. */
public class StructureFilterScreen extends SpecContainerScreen<StructureFilterMenu> {
    public StructureFilterScreen(StructureFilterMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, ScreenSpecLoader.fromResource(
                Minecraft.getInstance().getResourceManager(), "minenow", "structure_filter_screen"));
    }
}
