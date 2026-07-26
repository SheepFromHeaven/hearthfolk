package com.sheepfromheaven.minenow.client;

import com.sheepfromheaven.minenow.dominion.DominionTier;
import com.sheepfromheaven.minenow.network.ModNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public class DominionScreen extends Screen {
    private static final int PANEL_WIDTH = 200;
    private static final int PANEL_HEIGHT = 130;

    private final String dominionName;
    private final DominionTier tier;
    private final BlockPos bannerPos;

    private EditBox nameField;

    public DominionScreen(String dominionName, DominionTier tier, BlockPos bannerPos) {
        super(Component.literal("Dominion"));
        this.dominionName = dominionName;
        this.tier = tier;
        this.bannerPos = bannerPos;
    }

    @Override
    protected void init() {
        super.init();
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;

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
        int top = (height - PANEL_HEIGHT) / 2;

        graphics.drawCenteredString(font, dominionName, width / 2, top + 8, 0xFFFFFFFF);
        graphics.drawCenteredString(font, tier.getDisplayName(), width / 2, top + 22, 0xFFFFD700);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);

        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;

        graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xCC000000);
        graphics.fill(left, top, left + PANEL_WIDTH, top + 1, 0xFF555555);
        graphics.fill(left, top + PANEL_HEIGHT - 1, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xFF555555);
        graphics.fill(left, top, left + 1, top + PANEL_HEIGHT, 0xFF555555);
        graphics.fill(left + PANEL_WIDTH - 1, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xFF555555);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
