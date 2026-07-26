package com.sheepfromheaven.minenow.client;

import com.sheepfromheaven.minenow.dominion.StructureType;
import com.sheepfromheaven.minenow.network.ModNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public class StructureMarkerScreen extends Screen {
    private static final int PANEL_WIDTH = 200;
    private static final int PANEL_HEIGHT = 150;

    private String structureName;
    private StructureType structureType;
    private final BlockPos markerPos;

    private EditBox nameField;

    public StructureMarkerScreen(String structureName, StructureType structureType, BlockPos markerPos) {
        super(Component.literal("Structure"));
        this.structureName = structureName;
        this.structureType = structureType;
        this.markerPos = markerPos;
    }

    @Override
    protected void init() {
        super.init();
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;

        nameField = new EditBox(font, left + 10, top + 35, PANEL_WIDTH - 20, 20, Component.literal("Name"));
        nameField.setMaxLength(32);
        nameField.setValue(structureName);
        nameField.setHint(Component.literal("Structure name..."));
        addRenderableWidget(nameField);

        addRenderableWidget(Button.builder(Component.literal("Rename"), button -> {
            String newName = nameField.getValue().trim();
            if (!newName.isEmpty() && !newName.equals(structureName)) {
                ModNetwork.sendRenameStructure(markerPos, newName);
                structureName = newName;
            }
        }).pos(left + 10, top + 62).size(PANEL_WIDTH - 20, 20).build());

        addRenderableWidget(Button.builder(
                Component.literal("Type: " + structureType.getDisplayName()),
                button -> {
                    var types = StructureType.values();
                    int next = (structureType.ordinal() + 1) % types.length;
                    structureType = types[next];
                    ModNetwork.sendSetStructureType(markerPos, structureType.name());
                    rebuildWidgets();
                }
        ).pos(left + 10, top + 88).size(PANEL_WIDTH - 20, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;

        graphics.drawCenteredString(font, structureName, width / 2, top + 8, 0xFFFFFF);
        graphics.drawCenteredString(font, structureType.getDisplayName(), width / 2, top + 22, 0x55FF55);
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
