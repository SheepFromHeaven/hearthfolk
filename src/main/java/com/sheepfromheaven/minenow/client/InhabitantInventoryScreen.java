package com.sheepfromheaven.minenow.client;

import com.sheepfromheaven.minenow.inventory.InhabitantInventoryMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Reuses vanilla's own single-row chest texture instead of hand-drawn panels/slots, so it looks
 * pixel-identical to a real chest. The gap cell (offhand-style spacing before the general slots)
 * and any grid cells beyond the inhabitant's current capacity aren't turned into real slots by the
 * menu, so they're painted over here with the texture's own flat background tone rather than being
 * left showing as dead/locked slots.
 */
public class InhabitantInventoryScreen extends AbstractContainerScreen<InhabitantInventoryMenu> {
    private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final int PANEL_FILL = 0xFFC6C6C6;
    private static final int GRID_CELLS = 9;
    private static final int TOP_HEIGHT = 35; // 18 (slot row) + 17 (border), matching vanilla's one-row layout
    private static final int CELL_TOP_Y = 17;
    private static final int BORDER_PATCH_WIDTH = 2;
    // Vanilla's own panel bevel is asymmetric (a thinner highlight on the left, a thicker shadow on
    // the right), so sampling the real left-frame edge for the start patch looked too thin - reuse
    // the right-frame edge's look for both instead.
    private static final float END_BORDER_SAMPLE_U = 8 + GRID_CELLS * 18 - BORDER_PATCH_WIDTH;

    public InhabitantInventoryScreen(InhabitantInventoryMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 114 + 18;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos, topPos, 0.0F, 0.0F, imageWidth, TOP_HEIGHT, 256, 256);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos, topPos + TOP_HEIGHT, 0.0F, 126.0F, imageWidth, 96, 256, 256);

        int capacity = menu.slots.size() - 1 - 36;
        int firstHidden = InhabitantInventoryMenu.GENERAL_GRID_START + capacity;
        hideCellSweepLeft(graphics, InhabitantInventoryMenu.GAP_GRID_INDEX);
        for (int i = firstHidden; i < GRID_CELLS; i++) {
            if (i == firstHidden) {
                hideCellSweepLeft(graphics, i);
            } else {
                hideCell(graphics, i);
            }
        }

        // Erasing a hidden cell removes the grid's shared border column with whatever real slot
        // sits just before it, leaving a stray line dangling off that slot's edge instead of a
        // proper border. Drop that line and patch a real border back onto the real slot's side,
        // copied pixel-for-pixel from an untouched boundary elsewhere in the same texture.
        if (capacity > 0) {
            int x = leftPos + InhabitantInventoryMenu.GRID_X + InhabitantInventoryMenu.GENERAL_GRID_START * 18;
            patchBorder(graphics, x, END_BORDER_SAMPLE_U);
        }
        if (firstHidden < GRID_CELLS) {
            int x = leftPos + InhabitantInventoryMenu.GRID_X + firstHidden * 18 - BORDER_PATCH_WIDTH;
            patchBorder(graphics, x, END_BORDER_SAMPLE_U);
        }
    }

    private void patchBorder(GuiGraphics graphics, int destX, float sampleU) {
        int y = topPos + CELL_TOP_Y;
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, destX, y, sampleU, (float) CELL_TOP_Y,
                BORDER_PATCH_WIDTH, 18, 256, 256);
    }

    private void hideCell(GuiGraphics graphics, int gridIndex) {
        int x = leftPos + InhabitantInventoryMenu.GRID_X + gridIndex * 18;
        int y = topPos + CELL_TOP_Y;
        graphics.fill(x, y, x + 18, y + 18, PANEL_FILL);
    }

    /** Also sweeps into the preceding real slot's side to fully catch the grid's shared border column. */
    private void hideCellSweepLeft(GuiGraphics graphics, int gridIndex) {
        int x = leftPos + InhabitantInventoryMenu.GRID_X + gridIndex * 18;
        int y = topPos + CELL_TOP_Y;
        graphics.fill(x - BORDER_PATCH_WIDTH, y, x + 18, y + 18, PANEL_FILL);
    }
}
