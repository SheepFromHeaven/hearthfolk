package com.sheepfromheaven.minenow.client;

import com.sheepfromheaven.minenow.block.StructureMarkerBlockEntity;
import com.sheepfromheaven.minenow.dominion.DominionStructure;
import com.sheepfromheaven.minenow.dominion.StructureType;
import com.sheepfromheaven.minenow.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.List;

public class StructureMarkerScreen extends Screen {
    private static final int PANEL_WIDTH = 220;
    private static final int BASE_HEIGHT = 150;
    private static final int SLOT_SIZE = 20;
    private static final int SLOT_GAP = 4;

    private String structureName;
    private StructureType structureType;
    private final BlockPos markerPos;

    private EditBox nameField;

    public StructureMarkerScreen(String structureName, StructureType structureType, BlockPos markerPos,
                                 List<DominionStructure.RequirementStatus> requirements) {
        super(Component.literal("Structure"));
        this.structureName = structureName;
        this.structureType = structureType;
        this.markerPos = markerPos;
    }

    private List<DominionStructure.RequirementStatus> getRequirementStatuses() {
        var mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getBlockEntity(markerPos) instanceof StructureMarkerBlockEntity be) {
            var statuses = be.getRequirementStatuses();
            if (!statuses.isEmpty()) return statuses;
        }
        return structureType.getRequirements().stream()
                .map(r -> new DominionStructure.RequirementStatus(r.displayName(), r.count(), 0))
                .toList();
    }

    private int panelHeight() {
        int reqCount = structureType.getRequirements().size();
        return BASE_HEIGHT + (reqCount > 0 ? 14 + SLOT_SIZE + 4 : 0);
    }

    @Override
    protected void init() {
        super.init();
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - panelHeight()) / 2;

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

        addRenderableWidget(Button.builder(Component.literal("Recalculate Area"), button -> {
            ModNetwork.sendRecalculateStructure(markerPos);
            onClose();
        }).pos(left + 10, top + 114).size(PANEL_WIDTH - 20, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - panelHeight()) / 2;

        graphics.drawCenteredString(font, structureName, width / 2, top + 8, 0xFFFFFFFF);
        graphics.drawCenteredString(font, structureType.getDisplayName(), width / 2, top + 22, 0xFF55FF55);

        var typeReqs = structureType.getRequirements();
        if (!typeReqs.isEmpty()) {
            var statuses = getRequirementStatuses();
            int reqY = top + 140;
            graphics.drawString(font, "Requirements:", left + 10, reqY, 0xFFAAAAAA);
            reqY += 14;

            int totalWidth = typeReqs.size() * SLOT_SIZE + (typeReqs.size() - 1) * SLOT_GAP;
            int startX = (width - totalWidth) / 2;

            Component hoveredTooltip = null;

            for (int i = 0; i < typeReqs.size(); i++) {
                var req = typeReqs.get(i);
                boolean satisfied = i < statuses.size() && statuses.get(i).satisfied();
                int borderColor = satisfied ? 0xFF22CC22 : 0xFFCC2222;
                int bgColor = satisfied ? 0xFF1A3A1A : 0xFF3A1A1A;

                int slotX = startX + i * (SLOT_SIZE + SLOT_GAP);

                graphics.fill(slotX, reqY, slotX + SLOT_SIZE, reqY + SLOT_SIZE, borderColor);
                graphics.fill(slotX + 1, reqY + 1, slotX + SLOT_SIZE - 1, reqY + SLOT_SIZE - 1, bgColor);

                graphics.renderItem(req.icon().get(), slotX + 2, reqY + 2);

                if (mouseX >= slotX && mouseX < slotX + SLOT_SIZE && mouseY >= reqY && mouseY < reqY + SLOT_SIZE) {
                    int found = i < statuses.size() ? statuses.get(i).found() : 0;
                    String status = satisfied ? "OK" : found + "/" + req.count();
                    hoveredTooltip = Component.literal(req.displayName() + " (" + status + ")");
                }
            }

            if (hoveredTooltip != null) {
                graphics.renderComponentTooltip(font, List.of(hoveredTooltip), mouseX, mouseY, net.minecraft.world.item.ItemStack.EMPTY);
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
