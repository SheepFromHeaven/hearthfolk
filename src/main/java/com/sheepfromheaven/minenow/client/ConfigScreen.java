package com.sheepfromheaven.minenow.client;

import com.sheepfromheaven.minenow.core.Config;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.widget.ExtendedSlider;

public class ConfigScreen extends Screen {
    private final Screen parent;
    private ExtendedSlider radiusSlider;
    private ExtendedSlider roomSizeSlider;
    private ExtendedSlider maxTrunkSizeSlider;
    private Checkbox debugViewCheckbox;

    // Kept out of MineNow's constructor so its class file never references Screen directly;
    // referencing a client-only class there crashes mod construction on a dedicated server.
    public static void registerExtensionPoint(ModContainer modContainer) {
        modContainer.registerExtensionPoint(
                IConfigScreenFactory.class,
                (mc, parent) -> new ConfigScreen(parent));
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

        radiusSlider = new ExtendedSlider(
                centerX - 150, y, 300, 20,
                Component.literal("Dominion Radius: "), Component.empty(),
                16, 256, Config.defaultDominionRadius, 1, 0, true);
        addRenderableWidget(radiusSlider);

        roomSizeSlider = new ExtendedSlider(
                centerX - 150, y + 30, 300, 20,
                Component.literal("Max Room Size: "), Component.empty(),
                50, 2000, Config.maxRoomSize, 10, 0, true);
        addRenderableWidget(roomSizeSlider);

        maxTrunkSizeSlider = new ExtendedSlider(
                centerX - 150, y + 60, 300, 20,
                Component.literal("Max Trunk Size: "), Component.empty(),
                32, 2000, Config.maxTrunkSize, 10, 0, true);
        addRenderableWidget(maxTrunkSizeSlider);

        debugViewCheckbox = Checkbox.builder(Component.literal("Inhabitant Debug View"), font)
                .pos(centerX - 150, y + 90)
                .selected(Config.debugInhabitantView)
                .build();
        addRenderableWidget(debugViewCheckbox);

        addRenderableWidget(Button.builder(Component.literal("Save"), button -> {
            Config.DEFAULT_DOMINION_RADIUS.set(radiusSlider.getValueInt());
            Config.MAX_ROOM_SIZE.set(roomSizeSlider.getValueInt());
            Config.MAX_TRUNK_SIZE.set(maxTrunkSizeSlider.getValueInt());
            Config.DEBUG_INHABITANT_VIEW.set(debugViewCheckbox.selected());
            Config.defaultDominionRadius = radiusSlider.getValueInt();
            Config.maxRoomSize = roomSizeSlider.getValueInt();
            Config.maxTrunkSize = maxTrunkSizeSlider.getValueInt();
            Config.debugInhabitantView = debugViewCheckbox.selected();
            minecraft.setScreen(parent);
        }).pos(centerX - 75, y + 130).size(150, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> {
            minecraft.setScreen(parent);
        }).pos(centerX - 75, y + 156).size(150, 20).build());
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
