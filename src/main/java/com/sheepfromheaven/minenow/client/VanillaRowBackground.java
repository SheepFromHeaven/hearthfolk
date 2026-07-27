package com.sheepfromheaven.minenow.client;

import com.sheepfromheaven.minenow.inventory.VanillaRowMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Draws vanilla's own chest texture as the background for a {@link VanillaRowMenu}, hiding the gap
 * column and any general grid cells beyond the inventory's current capacity (which may leave a
 * partially- or fully-empty trailing row), then patching a clean border back onto the real slots'
 * edges.
 *
 * <p>The patch positions/samples here were tuned by hand against the actual texture rather than
 * derived analytically - vanilla's border art isn't symmetric (a hidden region's leading edge and
 * its trailing edge behave differently), so don't "simplify" this without checking the result
 * in-game.
 */
public final class VanillaRowBackground {
    private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final int PANEL_FILL = 0xFFC6C6C6;
    private static final int CELL_TOP_Y = 17;
    private static final int BORDER_PATCH_WIDTH = 2;
    private static final float START_BORDER_SAMPLE_U = VanillaRowMenu.GRID_X - BORDER_PATCH_WIDTH;
    private static final float END_BORDER_SAMPLE_U =
            VanillaRowMenu.GRID_X + VanillaRowMenu.GRID_CELLS * 18 - BORDER_PATCH_WIDTH;

    public static final int IMAGE_WIDTH = 176;

    private VanillaRowBackground() {}

    public static int imageHeight(VanillaRowMenu menu) {
        return 114 + menu.getRows() * 18;
    }

    public static void draw(GuiGraphics graphics, int left, int top, VanillaRowMenu menu) {
        int rows = menu.getRows();
        int topHeight = rows * 18 + 17;
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, left, top, 0.0F, 0.0F, IMAGE_WIDTH, topHeight, 256, 256);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, left, top + topHeight, 0.0F, 126.0F, IMAGE_WIDTH, 96, 256, 256);

        int toolSlotCount = menu.getToolSlotCount();
        int generalStart = menu.getGeneralGridStart();
        int columns = menu.getColumns();
        int generalCapacity = menu.getGeneralCapacity();

        for (int row = 0; row < rows; row++) {
            int rowTop = top + row * 18;
            int usedInRow = Math.max(0, Math.min(columns, generalCapacity - row * columns));

            // Leading hidden region: the tool slots' row also reserves the gap after them; every
            // other row just reserves the same leading width for column alignment. Only the tool
            // row's sweep needs to protect a real slot to its left (the last tool slot) - and that
            // boundary is deliberately left open (no border), matching the player's offhand look.
            int leadStart = row == 0 ? toolSlotCount : 0;
            for (int i = leadStart; i < generalStart; i++) {
                if (row == 0 && toolSlotCount > 0 && i == leadStart) {
                    hideCellSweepLeft(graphics, left, rowTop, i);
                } else {
                    hideCell(graphics, left, rowTop, i);
                }
            }
            if (usedInRow > 0) {
                int x = left + VanillaRowMenu.gridX(generalStart) - BORDER_PATCH_WIDTH;
                patchBorder(graphics, x, rowTop, START_BORDER_SAMPLE_U);
            }

            // Trailing hidden region: anything past this row's used general columns.
            int firstHidden = generalStart + usedInRow;
            for (int i = firstHidden; i < VanillaRowMenu.GRID_CELLS; i++) {
                if (usedInRow > 0 && i == firstHidden) {
                    hideCellSweepLeft(graphics, left, rowTop, i);
                } else {
                    hideCell(graphics, left, rowTop, i);
                }
            }
            if (usedInRow > 0 && firstHidden < VanillaRowMenu.GRID_CELLS) {
                int x = left + VanillaRowMenu.gridX(firstHidden) - BORDER_PATCH_WIDTH;
                patchBorder(graphics, x, rowTop, END_BORDER_SAMPLE_U);
            }
        }
    }

    private static void hideCell(GuiGraphics graphics, int left, int rowTop, int gridIndex) {
        int x = left + VanillaRowMenu.gridX(gridIndex);
        int y = rowTop + CELL_TOP_Y;
        graphics.fill(x, y, x + 18, y + 18, PANEL_FILL);
    }

    /** Also sweeps into the preceding slot's side to fully catch the grid's shared border column. */
    private static void hideCellSweepLeft(GuiGraphics graphics, int left, int rowTop, int gridIndex) {
        int x = left + VanillaRowMenu.gridX(gridIndex);
        int y = rowTop + CELL_TOP_Y;
        graphics.fill(x - BORDER_PATCH_WIDTH, y, x + 18, y + 18, PANEL_FILL);
    }

    private static void patchBorder(GuiGraphics graphics, int destX, int rowTop, float sampleU) {
        int y = rowTop + CELL_TOP_Y;
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, destX, y, sampleU, (float) CELL_TOP_Y,
                BORDER_PATCH_WIDTH, 18, 256, 256);
    }
}
