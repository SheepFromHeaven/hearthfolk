package com.sheepfromheaven.minenow.client;

import net.minecraft.client.gui.GuiGraphics;

public final class BeveledBox {
    public static final int BORDER_DARK = 0xFF000000;
    public static final int HIGHLIGHT = 0xFFFFFFFF;
    public static final int SHADOW = 0xFF555555;

    private BeveledBox() {}

    /**
     * Draws a vanilla-style 3D panel: a black outer border with a 2px diagonal
     * chamfer at each corner, a highlight/shadow bevel, and a fill color.
     * {@code pressed} swaps the bevel direction for a recessed ("pushed in") look.
     */
    public static void draw(GuiGraphics graphics, int x, int y, int w, int h, int fillColor, boolean pressed) {
        graphics.fill(x + 2, y, x + w - 2, y + 1, BORDER_DARK);
        graphics.fill(x + 1, y + 1, x + w - 1, y + 2, BORDER_DARK);
        graphics.fill(x, y + 2, x + w, y + h - 2, BORDER_DARK);
        graphics.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, BORDER_DARK);
        graphics.fill(x + 2, y + h - 1, x + w - 2, y + h, BORDER_DARK);

        int topLeft = pressed ? SHADOW : HIGHLIGHT;
        int bottomRight = pressed ? HIGHLIGHT : SHADOW;

        graphics.fill(x + 2, y + 1, x + w - 2, y + 2, topLeft);
        graphics.fill(x + 1, y + 2, x + 2, y + h - 2, topLeft);
        graphics.fill(x + 2, y + h - 2, x + w - 2, y + h - 1, bottomRight);
        graphics.fill(x + w - 2, y + 2, x + w - 1, y + h - 2, bottomRight);

        graphics.fill(x + 2, y + 2, x + w - 2, y + h - 2, fillColor);
    }
}
