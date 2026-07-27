package com.sheepfromheaven.minenow.client;

import com.sheepfromheaven.minenow.inventory.VanillaRowMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Generic screen for any {@link VanillaRowMenu} - register it once per menu type via {@code MenuScreens.register}. */
public class VanillaRowScreen extends AbstractContainerScreen<VanillaRowMenu> {
    public VanillaRowScreen(VanillaRowMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = VanillaRowBackground.IMAGE_WIDTH;
        this.imageHeight = VanillaRowBackground.imageHeight(menu);
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        VanillaRowBackground.draw(graphics, leftPos, topPos, menu);
    }
}
