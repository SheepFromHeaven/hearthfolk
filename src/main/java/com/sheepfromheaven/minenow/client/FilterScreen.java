package com.sheepfromheaven.minenow.client;

import com.sheepfromheaven.minenow.inventory.FilterMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import sheepfromheaven.screenspec.runtime.ScreenSpecLoader;
import sheepfromheaven.screenspec.runtime.SpecContainerScreen;
import sheepfromheaven.screenspec.runtime.WidgetSpec;

/**
 * The Filter item's own configuration screen: a real container menu with a 3x3 grid of template
 * slots the player can drag items into/out of like any other inventory, plus their own inventory
 * for the drag source, and two toggle buttons. {@link FilterMenu#removed} is what actually persists
 * the slot contents onto the held Filter item once this screen closes.
 */
public class FilterScreen extends SpecContainerScreen<FilterMenu> {

    public FilterScreen(FilterMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, ScreenSpecLoader.fromResource(
                Minecraft.getInstance().getResourceManager(), "minenow", "filter_screen"));
    }

    @Override
    protected void onAction(String widgetId, WidgetSpec widgetSpec, Object value) {
        switch (widgetId) {
            case "btn_toggle_whitelist" -> sendButton(FilterMenu.BUTTON_TOGGLE_WHITELIST);
            case "btn_toggle_match_metadata" -> sendButton(FilterMenu.BUTTON_TOGGLE_MATCH_METADATA);
        }
    }

    private void sendButton(int id) {
        this.menu.clickMenuButton(this.minecraft.player, id);
        this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        bindText("label_mode_status", menu.isWhitelist() ? "White" : "Black");
        bindText("label_metadata_status", menu.isMatchMetadata() ? "On" : "Off");
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
