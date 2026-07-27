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
    private static final int BASE_HEIGHT = 130;
    private static final int INHABITANT_LINE_HEIGHT = 12;

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

        boolean showing = DominionRangeTracker.isShowingRange(bannerPos);
        addRenderableWidget(Button.builder(
                Component.literal(showing ? "Hide Range" : "Show Range"),
                button -> {
                    DominionRangeTracker.toggleRange(bannerPos);
                    rebuildWidgets();
                }
        ).pos(left + 10, top + 88).size(PANEL_WIDTH - 20, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - panelHeight()) / 2;

        graphics.drawCenteredString(font, dominionName, width / 2, top + 8, 0xFFFFFFFF);
        graphics.drawCenteredString(font, tier.getDisplayName(), width / 2, top + 22, 0xFFFFD700);

        if (!inhabitantNames.isEmpty()) {
            int secY = top + 114;
            graphics.drawString(font, "Inhabitants (" + inhabitantNames.size() + "):", left + 10, secY, 0xFFAAAAAA);
            secY += 14;

            for (var name : inhabitantNames) {
                graphics.drawString(font, "  " + name, left + 10, secY, 0xFFDDDDDD);
                secY += INHABITANT_LINE_HEIGHT;
            }
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);

        int ph = panelHeight();
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - ph) / 2;

        graphics.fill(left, top, left + PANEL_WIDTH, top + ph, 0xCC000000);
        graphics.fill(left, top, left + PANEL_WIDTH, top + 1, 0xFF555555);
        graphics.fill(left, top + ph - 1, left + PANEL_WIDTH, top + ph, 0xFF555555);
        graphics.fill(left, top, left + 1, top + ph, 0xFF555555);
        graphics.fill(left + PANEL_WIDTH - 1, top, left + PANEL_WIDTH, top + ph, 0xFF555555);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
