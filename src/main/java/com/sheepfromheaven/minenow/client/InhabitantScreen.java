package com.sheepfromheaven.minenow.client;

import com.sheepfromheaven.minenow.dominion.NeedType;
import com.sheepfromheaven.minenow.network.ModNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class InhabitantScreen extends Screen {
    private static final int PANEL_WIDTH = 200;
    private static final int PANEL_HEIGHT = 180;
    private static final int TAB_HEIGHT = 20;
    private static final int BAR_WIDTH = 160;
    private static final int BAR_HEIGHT = 10;
    private static final int NEED_BLOCK_HEIGHT = 30;

    private static final int PANEL_FILL = 0xFFC6C6C6;
    private static final int TEXT_DARK = 0xFF404040;

    private enum Tab { GENERAL, NEEDS }

    private final int entityId;
    private final String inhabitantName;
    private final String dominionName;
    private final float shelter;
    private final float food;
    private final float rest;
    private final String jobStatus;

    private Tab currentTab = Tab.GENERAL;

    public InhabitantScreen(int entityId, String inhabitantName, String dominionName, float shelter, float food, float rest,
                             String jobStatus) {
        super(Component.literal("Inhabitant"));
        this.entityId = entityId;
        this.inhabitantName = inhabitantName;
        this.dominionName = dominionName;
        this.shelter = shelter;
        this.food = food;
        this.rest = rest;
        this.jobStatus = jobStatus;
    }

    @Override
    protected void init() {
        super.init();
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;
        int tabWidth = (PANEL_WIDTH - 20) / 2;

        addRenderableWidget(new NiceTabButton(left + 10, top + 40, tabWidth, TAB_HEIGHT,
                Component.literal("General"), () -> currentTab == Tab.GENERAL, () -> {
            currentTab = Tab.GENERAL;
            rebuildWidgets();
        }));

        addRenderableWidget(new NiceTabButton(left + 10 + tabWidth, top + 40, tabWidth, TAB_HEIGHT,
                Component.literal("Needs"), () -> currentTab == Tab.NEEDS, () -> {
            currentTab = Tab.NEEDS;
            rebuildWidgets();
        }));

        if (currentTab == Tab.GENERAL) {
            addRenderableWidget(Button.builder(Component.literal("Go Home"), button ->
                    ModNetwork.sendGoHome(entityId)
            ).pos(left + 10, top + 70).size(PANEL_WIDTH - 20, 20).build());

            addRenderableWidget(Button.builder(Component.literal("View Inventory"), button ->
                    ModNetwork.sendViewInventory(entityId)
            ).pos(left + 10, top + 96).size(PANEL_WIDTH - 20, 20).build());
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;

        drawCentered(graphics, inhabitantName, top + 8, TEXT_DARK);
        drawCentered(graphics, dominionName, top + 22, 0xFF8A6D00);

        if (currentTab == Tab.GENERAL) {
            drawCentered(graphics, "Job: " + jobStatus, top + 126, TEXT_DARK);
        } else if (currentTab == Tab.NEEDS) {
            renderNeedsTab(graphics, left, top);
        }
    }

    private void drawCentered(GuiGraphics graphics, String text, int y, int color) {
        graphics.drawString(font, text, width / 2 - font.width(text) / 2, y, color, false);
    }

    private void renderNeedsTab(GuiGraphics graphics, int left, int top) {
        int barLeft = left + (PANEL_WIDTH - BAR_WIDTH) / 2;
        int contentTop = top + 70;

        graphics.drawString(font, "Needs", left + 10, contentTop, TEXT_DARK, false);

        var types = NeedType.values();
        var values = new float[] { shelter, food, rest };
        for (int i = 0; i < types.length; i++) {
            drawNeedBar(graphics, barLeft, contentTop + 18 + i * NEED_BLOCK_HEIGHT, types[i], values[i]);
        }
    }

    private void drawNeedBar(GuiGraphics graphics, int x, int y, NeedType type, float value) {
        graphics.drawString(font, type.getDisplayName(), x, y, TEXT_DARK, false);

        int barY = y + 12;
        int fillColor = value >= 0.66f ? 0xFF3CB043 : value >= 0.33f ? 0xFFD9A400 : 0xFFCC3333;
        int filled = Math.round(BAR_WIDTH * Math.max(0.0f, Math.min(1.0f, value)));

        graphics.fill(x, barY, x + BAR_WIDTH, barY + BAR_HEIGHT, 0xFF2B2B2B);
        if (filled > 0) {
            graphics.fill(x, barY, x + filled, barY + BAR_HEIGHT, fillColor);
        }
        graphics.fill(x, barY, x + BAR_WIDTH, barY + 1, BeveledBox.SHADOW);
        graphics.fill(x, barY + BAR_HEIGHT - 1, x + BAR_WIDTH, barY + BAR_HEIGHT, BeveledBox.SHADOW);
        graphics.fill(x, barY, x + 1, barY + BAR_HEIGHT, BeveledBox.SHADOW);
        graphics.fill(x + BAR_WIDTH - 1, barY, x + BAR_WIDTH, barY + BAR_HEIGHT, BeveledBox.SHADOW);

        String percent = Math.round(value * 100) + "%";
        graphics.drawCenteredString(font, percent, x + BAR_WIDTH / 2, barY + 1, 0xFFFFFFFF);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);

        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;

        BeveledBox.draw(graphics, left, top, PANEL_WIDTH, PANEL_HEIGHT, PANEL_FILL, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
