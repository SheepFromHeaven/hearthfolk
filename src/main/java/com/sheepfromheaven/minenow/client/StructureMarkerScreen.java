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

import java.util.ArrayList;
import java.util.List;

public class StructureMarkerScreen extends Screen {
    private static final int PANEL_WIDTH = 220;
    private static final int BASE_HEIGHT = 120;
    private static final int TAB_Y = 40;
    private static final int TAB_HEIGHT = 20;
    private static final int CONTENT_START = 70;
    private static final int TEXT_ROW_HEIGHT = 16;
    private static final int BUTTON_ROW_HEIGHT = 26;
    private static final int SLOT_SIZE = 20;
    private static final int SLOT_GAP = 4;
    private static final int REQ_ROW_HEIGHT = 14 + SLOT_SIZE + 4;

    private static final int PANEL_FILL = 0xFFC6C6C6;
    private static final int TEXT_DARK = 0xFF404040;
    private static final int TEXT_LABEL = 0xFF404040;
    private static final int SATISFIED = 0xFF3CB043;
    private static final int UNSATISFIED = 0xFFCC3333;

    private enum Tab { GENERAL, REQUIREMENTS }

    private String structureName;
    private StructureType structureType;
    private final BlockPos markerPos;
    private final List<InhabitantEntry> inhabitants;
    private int selectedInhabitantIndex;

    private Tab currentTab = Tab.GENERAL;
    private EditBox nameField;

    public record InhabitantEntry(String id, String name) {}

    private record GeneralLayout(int residentY, int rangeToggleY, int totalExtra) {}
    private record RequirementsLayout(int spaceY, int reqY, int rangeReqY, int totalExtra) {}

    public StructureMarkerScreen(String structureName, StructureType structureType, BlockPos markerPos,
                                 String inhabitantData, String assignedInhabitantId) {
        super(Component.literal("Structure"));
        this.structureName = structureName;
        this.structureType = structureType;
        this.markerPos = markerPos;
        this.inhabitants = parseInhabitants(inhabitantData);
        this.selectedInhabitantIndex = 0;
        for (int i = 0; i < inhabitants.size(); i++) {
            if (inhabitants.get(i).id().equals(assignedInhabitantId)) {
                selectedInhabitantIndex = i + 1;
                break;
            }
        }
    }

    private static List<InhabitantEntry> parseInhabitants(String data) {
        var list = new ArrayList<InhabitantEntry>();
        if (data == null || data.isEmpty()) return list;
        for (var entry : data.split(",")) {
            int colon = entry.indexOf(':');
            if (colon > 0) {
                list.add(new InhabitantEntry(entry.substring(0, colon), entry.substring(colon + 1)));
            }
        }
        return list;
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

    private List<DominionStructure.RequirementStatus> getRangeRequirementStatuses() {
        var mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getBlockEntity(markerPos) instanceof StructureMarkerBlockEntity be) {
            var statuses = be.getRangeRequirementStatuses();
            if (!statuses.isEmpty()) return statuses;
        }
        return structureType.getRangeRequirements().stream()
                .map(r -> new DominionStructure.RequirementStatus(r.displayName(), r.count(), 0))
                .toList();
    }

    private DominionStructure.RequirementStatus getSpaceStatus() {
        var mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getBlockEntity(markerPos) instanceof StructureMarkerBlockEntity be
                && be.getSpaceStatus() != null) {
            return be.getSpaceStatus();
        }
        return new DominionStructure.RequirementStatus("Space", structureType.getMinSize(), 0);
    }

    private boolean isHouse() {
        return structureType == StructureType.HOUSE;
    }

    private boolean hasSpaceRequirement() {
        return structureType.getMinSize() > 0;
    }

    private boolean hasRangeRequirement() {
        return structureType.getRangeRadius() > 0;
    }

    private boolean hasUnmetRequirement() {
        if (hasSpaceRequirement() && !getSpaceStatus().satisfied()) return true;
        for (var status : getRequirementStatuses()) {
            if (!status.satisfied()) return true;
        }
        if (hasRangeRequirement()) {
            for (var status : getRangeRequirementStatuses()) {
                if (!status.satisfied()) return true;
            }
        }
        return false;
    }

    private GeneralLayout computeGeneralLayout() {
        int y = CONTENT_START + BUTTON_ROW_HEIGHT * 4;
        int residentY = -1, rangeToggleY = -1;

        if (isHouse() && !inhabitants.isEmpty()) {
            residentY = y;
            y += BUTTON_ROW_HEIGHT;
        }
        if (hasRangeRequirement()) {
            rangeToggleY = y;
            y += BUTTON_ROW_HEIGHT;
        }
        return new GeneralLayout(residentY, rangeToggleY, y - CONTENT_START);
    }

    private RequirementsLayout computeRequirementsLayout() {
        int y = CONTENT_START;
        int spaceY = -1, reqY = -1, rangeReqY = -1;

        if (hasSpaceRequirement()) {
            spaceY = y;
            y += TEXT_ROW_HEIGHT;
        }
        if (!structureType.getRequirements().isEmpty()) {
            reqY = y;
            y += REQ_ROW_HEIGHT;
        }
        if (hasRangeRequirement() && !structureType.getRangeRequirements().isEmpty()) {
            rangeReqY = y;
            y += REQ_ROW_HEIGHT;
        }
        return new RequirementsLayout(spaceY, reqY, rangeReqY, y - CONTENT_START);
    }

    private int panelHeight() {
        int extra = Math.max(computeGeneralLayout().totalExtra(), computeRequirementsLayout().totalExtra());
        return BASE_HEIGHT + extra;
    }

    private String getSelectedLabel() {
        if (selectedInhabitantIndex == 0) return "None";
        return inhabitants.get(selectedInhabitantIndex - 1).name();
    }

    private Component requirementsTabLabel() {
        var label = Component.literal("Requirements");
        if (hasUnmetRequirement()) {
            label.append(Component.literal(" !").withStyle(style -> style.withColor(0xFFFF5555)));
        }
        return label;
    }

    @Override
    protected void init() {
        super.init();
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - panelHeight()) / 2;
        int tabWidth = (PANEL_WIDTH - 20) / 2;

        addRenderableWidget(new NiceTabButton(left + 10, top + TAB_Y, tabWidth, TAB_HEIGHT,
                Component.literal("General"), () -> currentTab == Tab.GENERAL, () -> {
            currentTab = Tab.GENERAL;
            rebuildWidgets();
        }));

        addRenderableWidget(new NiceTabButton(left + 10 + tabWidth, top + TAB_Y, tabWidth, TAB_HEIGHT,
                requirementsTabLabel(), () -> currentTab == Tab.REQUIREMENTS, () -> {
            currentTab = Tab.REQUIREMENTS;
            rebuildWidgets();
        }));

        if (currentTab == Tab.GENERAL) {
            initGeneralTab(left, top);
        }
    }

    private void initGeneralTab(int left, int top) {
        var layout = computeGeneralLayout();

        nameField = new EditBox(font, left + 10, top + CONTENT_START, PANEL_WIDTH - 20, 20, Component.literal("Name"));
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
        }).pos(left + 10, top + CONTENT_START + BUTTON_ROW_HEIGHT).size(PANEL_WIDTH - 20, 20).build());

        addRenderableWidget(Button.builder(
                Component.literal("Type: " + structureType.getDisplayName()),
                button -> {
                    var types = StructureType.values();
                    int next = (structureType.ordinal() + 1) % types.length;
                    structureType = types[next];
                    ModNetwork.sendSetStructureType(markerPos, structureType.name());
                    rebuildWidgets();
                }
        ).pos(left + 10, top + CONTENT_START + BUTTON_ROW_HEIGHT * 2).size(PANEL_WIDTH - 20, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Recalculate Area"), button -> {
            ModNetwork.sendRecalculateStructure(markerPos);
            onClose();
        }).pos(left + 10, top + CONTENT_START + BUTTON_ROW_HEIGHT * 3).size(PANEL_WIDTH - 20, 20).build());

        if (layout.residentY() >= 0) {
            addRenderableWidget(Button.builder(
                    Component.literal("Resident: " + getSelectedLabel()),
                    button -> {
                        selectedInhabitantIndex = (selectedInhabitantIndex + 1) % (inhabitants.size() + 1);
                        button.setMessage(Component.literal("Resident: " + getSelectedLabel()));
                        String id = selectedInhabitantIndex == 0 ? "" : inhabitants.get(selectedInhabitantIndex - 1).id();
                        ModNetwork.sendAssignHouse(markerPos, id);
                    }
            ).pos(left + 10, top + layout.residentY()).size(PANEL_WIDTH - 20, 20).build());
        }

        if (layout.rangeToggleY() >= 0) {
            boolean showing = RangeTracker.isShowingRange(markerPos);
            addRenderableWidget(Button.builder(
                    Component.literal(showing ? "Hide Range" : "Show Range"),
                    button -> {
                        RangeTracker.toggleRange(markerPos);
                        rebuildWidgets();
                    }
            ).pos(left + 10, top + layout.rangeToggleY()).size(PANEL_WIDTH - 20, 20).build());
        }
    }

    private void drawCentered(GuiGraphics graphics, String text, int y, int color) {
        graphics.drawString(font, text, width / 2 - font.width(text) / 2, y, color, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - panelHeight()) / 2;

        drawCentered(graphics, structureName, top + 8, TEXT_DARK);
        drawCentered(graphics, structureType.getDisplayName(), top + 22, 0xFF8A6D00);

        if (currentTab == Tab.REQUIREMENTS) {
            renderRequirementsTab(graphics, mouseX, mouseY, left, top);
        }
    }

    private void renderRequirementsTab(GuiGraphics graphics, int mouseX, int mouseY, int left, int top) {
        var layout = computeRequirementsLayout();
        Component hoveredTooltip = null;

        if (layout.spaceY() >= 0) {
            var spaceStatus = getSpaceStatus();
            int color = spaceStatus.satisfied() ? SATISFIED : UNSATISFIED;
            drawCentered(graphics, "Space: " + spaceStatus.found() + "/" + spaceStatus.required(),
                    top + layout.spaceY(), color);
        }

        if (layout.reqY() >= 0) {
            hoveredTooltip = drawRequirementRow(graphics, mouseX, mouseY, left, top + layout.reqY(),
                    "Requirements:", structureType.getRequirements(), getRequirementStatuses());
        }

        if (layout.rangeReqY() >= 0) {
            var tooltip = drawRequirementRow(graphics, mouseX, mouseY, left, top + layout.rangeReqY(),
                    "Nearby (r=" + structureType.getRangeRadius() + "):", structureType.getRangeRequirements(), getRangeRequirementStatuses());
            if (tooltip != null) hoveredTooltip = tooltip;
        }

        if (hoveredTooltip != null) {
            graphics.renderComponentTooltip(font, List.of(hoveredTooltip), mouseX, mouseY, net.minecraft.world.item.ItemStack.EMPTY);
        }
    }

    private Component drawRequirementRow(GuiGraphics graphics, int mouseX, int mouseY, int left, int rowTop,
                                          String label, List<StructureType.Requirement> reqs,
                                          List<DominionStructure.RequirementStatus> statuses) {
        graphics.drawString(font, label, left + 10, rowTop, TEXT_LABEL, false);
        int iconY = rowTop + 14;

        int totalWidth = reqs.size() * SLOT_SIZE + (reqs.size() - 1) * SLOT_GAP;
        int startX = (width - totalWidth) / 2;

        Component hoveredTooltip = null;

        for (int i = 0; i < reqs.size(); i++) {
            var req = reqs.get(i);
            boolean satisfied = i < statuses.size() && statuses.get(i).satisfied();
            int borderColor = satisfied ? 0xFF22CC22 : 0xFFCC2222;
            int bgColor = satisfied ? 0xFF1A3A1A : 0xFF3A1A1A;

            int slotX = startX + i * (SLOT_SIZE + SLOT_GAP);

            graphics.fill(slotX, iconY, slotX + SLOT_SIZE, iconY + SLOT_SIZE, borderColor);
            graphics.fill(slotX + 1, iconY + 1, slotX + SLOT_SIZE - 1, iconY + SLOT_SIZE - 1, bgColor);

            graphics.renderItem(req.icon().get(), slotX + 2, iconY + 2);

            if (mouseX >= slotX && mouseX < slotX + SLOT_SIZE && mouseY >= iconY && mouseY < iconY + SLOT_SIZE) {
                int found = i < statuses.size() ? statuses.get(i).found() : 0;
                String status = satisfied ? "OK" : found + "/" + req.count();
                hoveredTooltip = Component.literal(req.displayName() + " (" + status + ")");
            }
        }

        return hoveredTooltip;
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
