package com.sheepfromheaven.minenow.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;

public class NiceTabButton extends AbstractWidget {
    private static final int SELECTED_FILL = 0xFFC6C6C6;
    private static final int UNSELECTED_FILL = 0xFF8B8B8B;

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
        BeveledBox.draw(graphics, getX(), getY(), getWidth(), getHeight(),
                isSelected ? SELECTED_FILL : UNSELECTED_FILL, !isSelected);

        int textColor = isSelected ? 0xFF000000 : 0xFF404040;
        var font = Minecraft.getInstance().font;
        graphics.drawCenteredString(font, getMessage(), getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, textColor);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
