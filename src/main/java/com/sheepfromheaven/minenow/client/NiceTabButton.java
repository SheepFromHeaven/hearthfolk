package com.sheepfromheaven.minenow.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.function.BooleanSupplier;

/**
 * A folder-style tab button using the same sprites as vanilla's TabButton
 * (the widget backing e.g. the Controls/Recipe Book tabs), sized to fit
 * inside a small floating panel rather than a full-screen TabNavigationBar.
 */
public class NiceTabButton extends AbstractWidget {
    private static final WidgetSprites SPRITES = new WidgetSprites(
            Identifier.withDefaultNamespace("widget/tab_selected"),
            Identifier.withDefaultNamespace("widget/tab"),
            Identifier.withDefaultNamespace("widget/tab_selected_highlighted"),
            Identifier.withDefaultNamespace("widget/tab_highlighted")
    );
    private static final int UNSELECTED_TEXT_OFFSET = 3;

    private final BooleanSupplier selected;
    private final Runnable onPress;

    public NiceTabButton(int x, int y, int width, int height, Component label, BooleanSupplier selected, Runnable onPress) {
        super(x, y, width, height, label);
        this.selected = selected;
        this.onPress = onPress;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        onPress.run();
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean isSelected = selected.getAsBoolean();
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SPRITES.get(isSelected, isHoveredOrFocused()),
                getX(), getY(), getWidth(), getHeight());

        var font = Minecraft.getInstance().font;
        int textY = getY() + (isSelected ? 0 : UNSELECTED_TEXT_OFFSET) + (getHeight() - 8) / 2;
        graphics.drawCenteredString(font, getMessage(), getX() + getWidth() / 2, textY, 0xFFFFFFFF);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
