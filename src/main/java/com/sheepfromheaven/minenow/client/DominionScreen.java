package com.sheepfromheaven.minenow.client;

import com.sheepfromheaven.minenow.dominion.DominionTier;
import com.sheepfromheaven.minenow.network.ModNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.List;

public class DominionScreen extends Screen {
    private static final int PANEL_WIDTH = 220;
    private static final int BASE_HEIGHT = 156;
    private static final int INHABITANT_LINE_HEIGHT = 12;

    private static final int PANEL_FILL = 0xFFC6C6C6;
    private static final int TEXT_DARK = 0xFF404040;
    private static final int TEXT_LABEL = 0xFF404040;
    private static final int TEXT_BODY = 0xFF303030;

    private final String dominionName;
    private final DominionTier tier;
    private final BlockPos bannerPos;
    private final List<String> inhabitantNames;

    private EditBox nameField;

    public DominionScreen(String dominionName, DominionTier tier, BlockPos bannerPos, List<String> inhabitantNames) {
        super(Component.literal("Dominion"));
        this.dominionName = dominionName;
        this.tier = tier;
        this.bannerPos = bannerPos;
        this.inhabitantNames = inhabitantNames;
    }

    private int panelHeight() {
        int inhabitantSection = inhabitantNames.isEmpty() ? 0 : 14 + inhabitantNames.size() * INHABITANT_LINE_HEIGHT + 4;
        return BASE_HEIGHT + inhabitantSection;
    }

    @Override
    protected void init() {
        super.init();
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - panelHeight()) / 2;

        nameField = new EditBox(font, left + 10, top + 35, PANEL_WIDTH - 20, 20, Component.literal("Name"));
        nameField.setMaxLength(32);
        nameField.setValue(dominionName);
        nameField.setHint(Component.literal("Dominion name..."));
        addRenderableWidget(nameField);

        addRenderableWidget(Button.builder(Component.literal("Rename"), button -> {
            String newName = nameField.getValue().trim();
            if (!newName.isEmpty() && !newName.equals(dominionName)) {
                ModNetwork.sendRenameDominion(bannerPos, newName);
            }
        }).pos(left + 10, top + 62).size(PANEL_WIDTH - 20, 20).build());

        boolean showing = RangeTracker.isShowingRange(bannerPos);
        addRenderableWidget(Button.builder(
                Component.literal(showing ? "Hide Range" : "Show Range"),
                button -> {
                    RangeTracker.toggleRange(bannerPos);
                    rebuildWidgets();
                }
        ).pos(left + 10, top + 88).size(PANEL_WIDTH - 20, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Requests"),
                button -> com.sheepfromheaven.minenow.network.ModNetwork.sendRequestRequestsScreen(bannerPos)
        ).pos(left + 10, top + 114).size(PANEL_WIDTH - 20, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - panelHeight()) / 2;

        drawCentered(graphics, dominionName, top + 8, TEXT_DARK);
        drawCentered(graphics, tier.getDisplayName(), top + 22, 0xFF8A6D00);

        if (!inhabitantNames.isEmpty()) {
            int secY = top + 140;
            graphics.drawString(font, "Inhabitants (" + inhabitantNames.size() + "):", left + 10, secY, TEXT_LABEL, false);
            secY += 14;

            for (var name : inhabitantNames) {
                graphics.drawString(font, "  " + name, left + 10, secY, TEXT_BODY, false);
                secY += INHABITANT_LINE_HEIGHT;
            }
        }
    }

    private void drawCentered(GuiGraphics graphics, String text, int y, int color) {
        graphics.drawString(font, text, width / 2 - font.width(text) / 2, y, color, false);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);

        int ph = panelHeight();
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - ph) / 2;

        BeveledBox.draw(graphics, left, top, PANEL_WIDTH, ph, PANEL_FILL, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
