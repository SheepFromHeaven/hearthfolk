package com.sheepfromheaven.minenow.client;

import com.sheepfromheaven.minenow.Config;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.gui.widget.ForgeSlider;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

public class ConfigScreen extends Screen {
    private final Screen parent;
    private ForgeSlider radiusSlider;
    private ForgeSlider roomSizeSlider;

    // Kept out of MineNow's constructor so its class file never references Screen directly;
    // referencing a client-only class there crashes mod construction on a dedicated server.
    public static void registerExtensionPoint(FMLJavaModLoadingContext context) {
        context.registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(ConfigScreen::new));
    }

    public ConfigScreen(Screen parent) {
        super(Component.literal("MineNow Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int centerX = width / 2;
        int y = height / 4;

        radiusSlider = new ForgeSlider(
                centerX - 150, y, 300, 20,
                Component.literal("Dominion Radius: "), Component.empty(),
                16, 256, Config.defaultDominionRadius, 1, 0, true);
        addRenderableWidget(radiusSlider);

        roomSizeSlider = new ForgeSlider(
                centerX - 150, y + 30, 300, 20,
                Component.literal("Max Room Size: "), Component.empty(),
                50, 2000, Config.maxRoomSize, 10, 0, true);
        addRenderableWidget(roomSizeSlider);

        addRenderableWidget(Button.builder(Component.literal("Save"), button -> {
            Config.DEFAULT_DOMINION_RADIUS.set(radiusSlider.getValueInt());
            Config.MAX_ROOM_SIZE.set(roomSizeSlider.getValueInt());
            Config.defaultDominionRadius = radiusSlider.getValueInt();
            Config.maxRoomSize = roomSizeSlider.getValueInt();
            minecraft.setScreen(parent);
        }).pos(centerX - 75, y + 70).size(150, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> {
            minecraft.setScreen(parent);
        }).pos(centerX - 75, y + 96).size(150, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, height / 4 - 20, 0xFFFFFF);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
    }
}
