package com.sheepfromheaven.minenow.client;

import com.sheepfromheaven.minenow.block.StructureMarkerBlockEntity;
import com.sheepfromheaven.minenow.dominion.DominionStructure;
import com.sheepfromheaven.minenow.dominion.JobType;
import com.sheepfromheaven.minenow.dominion.StructureType;
import com.sheepfromheaven.minenow.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import sheepfromheaven.screenspec.runtime.ScreenSpec;
import sheepfromheaven.screenspec.runtime.ScreenSpecLoader;
import sheepfromheaven.screenspec.runtime.SpecScreen;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class StructureMarkerScreen extends SpecScreen {

    private static final int SLOT_SIZE = 20;
    private static final int SLOT_GAP = 4;
    private static final int SATISFIED   = 0xFF3CB043;
    private static final int UNSATISFIED = 0xFFCC3333;

    private static final int REFRESH_INTERVAL_TICKS = 20;

    private String structureName;
    private StructureType structureType;
    private final BlockPos markerPos;
    private final List<InhabitantEntry> inhabitants;
    private String assignedInhabitantId;
    private String assignedWorkerId;
    private BlockPos workAreaCenter;
    private int workAreaRadius;
    private String itemFilterData;

    private EditBox nameField;
    private EditBox filterField;
    private int lastMouseX, lastMouseY;
    private Component hoveredReqTooltip;
    private int refreshCooldown = REFRESH_INTERVAL_TICKS;

    public record InhabitantEntry(String id, UUID entityUuid, String name, String jobLabel, boolean homed) {}

    public StructureMarkerScreen(String structureName, StructureType structureType, BlockPos markerPos,
                                 String inhabitantData, String assignedInhabitantId, String assignedWorkerId,
                                 BlockPos workAreaCenter, int workAreaRadius, String itemFilterData) {
        super(Component.literal("Structure"), loadSpec());
        this.structureName = structureName;
        this.structureType = structureType;
        this.markerPos = markerPos;
        this.inhabitants = parseInhabitants(inhabitantData);
        this.assignedInhabitantId = assignedInhabitantId;
        this.assignedWorkerId = assignedWorkerId;
        this.workAreaCenter = workAreaCenter;
        this.workAreaRadius = workAreaRadius;
        this.itemFilterData = itemFilterData;

        on("minenow.rename", (id, s, v) -> {
            if (nameField == null) return;
            String newName = nameField.getValue().trim();
            if (!newName.isEmpty() && !newName.equals(this.structureName)) {
                ModNetwork.sendRenameStructure(markerPos, newName);
                this.structureName = newName;
            }
        });

        on("minenow.cycle_type", (id, s, v) -> {
            var types = StructureType.values();
            this.structureType = types[(this.structureType.ordinal() + 1) % types.length];
            ModNetwork.sendSetStructureType(markerPos, this.structureType.name());
            rebuildWidgets();
        });

        on("minenow.assign_resident", (id, s, v) -> openResidentPicker());
        on("minenow.assign_worker",   (id, s, v) -> openWorkerPicker());

        on("minenow.set_work_center", (id, s, v) -> {
            WorldPickMode.activate(markerPos, this.structureName, this.structureType.getRangeRadius());
            onClose();
        });

        on("minenow.toggle_workarea", (id, s, v) -> {
            WorkAreaTracker.toggleWorkArea(markerPos);
            rebuildWidgets();
        });

        on("minenow.recalculate", (id, s, v) -> {
            ModNetwork.sendRecalculateStructure(markerPos);
            onClose();
        });

        on("minenow.toggle_range", (id, s, v) -> {
            RangeTracker.toggleRange(markerPos);
            rebuildWidgets();
        });

        on("minenow.save_item_filter", (id, s, v) -> {
            if (filterField == null) return;
            this.itemFilterData = filterField.getValue().trim();
            ModNetwork.sendSetItemFilter(markerPos, this.itemFilterData);
        });
    }

    private static ScreenSpec loadSpec() {
        return ScreenSpecLoader.fromResource(
                Minecraft.getInstance().getResourceManager(), "minenow", "structure_marker_screen");
    }

    public boolean matchesMarker(BlockPos pos) { return markerPos.equals(pos); }

    public void update(String inhabitantData, String assignedInhabitantId, String assignedWorkerId, String itemFilterData) {
        var newInhabitants = parseInhabitants(inhabitantData);
        boolean changed = !this.assignedInhabitantId.equals(assignedInhabitantId)
                || !this.assignedWorkerId.equals(assignedWorkerId)
                || !this.inhabitants.equals(newInhabitants);
        this.inhabitants.clear();
        this.inhabitants.addAll(newInhabitants);
        this.assignedInhabitantId = assignedInhabitantId;
        this.assignedWorkerId = assignedWorkerId;
        this.itemFilterData = itemFilterData;
        if (changed) rebuildWidgets();
    }

    @Override
    public void tick() {
        super.tick();
        if (--refreshCooldown <= 0) {
            ModNetwork.sendRefreshStructureScreen(markerPos);
            refreshCooldown = REFRESH_INTERVAL_TICKS;
        }
    }

    @Override
    protected void init() {
        super.init();

        int ox = (width  - spec().width)  / 2;
        int oy = (height - spec().height) / 2;
        int contentY = oy + 60; // tabs.y=40 + tab_height=20

        // --- general tab ---
        if ("tab_general".equals(activeTab("tabs_main"))) {
            nameField = new EditBox(font, ox + 10, contentY, spec().width - 20, 20, Component.literal("Name"));
            nameField.setMaxLength(32);
            nameField.setValue(structureName);
            nameField.setHint(Component.literal("Structure name..."));
            addRenderableWidget(nameField);
        }

        AbstractWidget typeBtn = getWidget("btn_type");
        if (typeBtn != null) typeBtn.setMessage(Component.literal("Type: " + structureType.getDisplayName()));

        AbstractWidget residentBtn = getWidget("btn_resident");
        if (residentBtn != null) {
            residentBtn.visible = isHouse() && !inhabitants.isEmpty();
            residentBtn.setMessage(Component.literal("Resident: " + labelFor(assignedInhabitantId)));
        }

        AbstractWidget workerBtn = getWidget("btn_worker");
        if (workerBtn != null) {
            workerBtn.visible = hasJob() && !inhabitants.isEmpty();
            workerBtn.setMessage(Component.literal("Worker: " + labelFor(assignedWorkerId)));
        }

        // --- requirements tab label (alert mark if unmet) ---
        AbstractWidget reqTab = getWidget("tab_requirements");
        if (reqTab != null) reqTab.setMessage(BeveledBox.withAlertMark("Requirements", hasUnmetRequirement()));

        // --- work area tab ---
        AbstractWidget workAreaTab = getWidget("tab_work_area");
        if (workAreaTab != null) workAreaTab.visible = hasWorkArea();

        if ("tab_work_area".equals(activeTab("tabs_main"))) {
            addRenderableWidget(new WorkAreaRadiusSlider(ox + 10, contentY + 18, spec().width - 20, 20,
                    workAreaRadius, radius -> {
                workAreaRadius = radius;
                ModNetwork.sendSetWorkAreaRadius(markerPos, radius);
            }));
        }

        AbstractWidget filterTab = getWidget("tab_filter");
        if (filterTab != null) filterTab.visible = isStorehouse();

        if ("tab_filter".equals(activeTab("tabs_main"))) {
            filterField = new EditBox(font, ox + 10, contentY + 18, spec().width - 20, 20, Component.literal("Item filter"));
            filterField.setMaxLength(512);
            filterField.setValue(itemFilterData);
            filterField.setHint(Component.literal("e.g. minecraft:coal, minecraft:iron_ingot"));
            addRenderableWidget(filterField);
        }

        AbstractWidget toggleWorkarea = getWidget("btn_toggle_workarea");
        if (toggleWorkarea != null) toggleWorkarea.setMessage(
                Component.literal(WorkAreaTracker.isShowingWorkArea(markerPos) ? "Hide Workarea" : "Show Workarea"));

        // --- debug tab ---
        AbstractWidget toggleRange = getWidget("btn_toggle_range");
        if (toggleRange != null) {
            toggleRange.visible = hasRangeRequirement();
            toggleRange.setMessage(
                    Component.literal(RangeTracker.isShowingRange(markerPos) ? "Hide Range" : "Show Range"));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        hoveredReqTooltip = null;

        bindText("label_name", structureName);
        bindText("label_type", structureType.getDisplayName());
        bindText("label_center", "Center: " + workAreaCenter.getX() + ", "
                + workAreaCenter.getY() + ", " + workAreaCenter.getZ());

        super.render(graphics, mouseX, mouseY, partialTick);

        if ("tab_requirements".equals(activeTab("tabs_main"))) {
            renderRequirementsTab(graphics);
        }

        if (hoveredReqTooltip != null) {
            graphics.setComponentTooltipForNextFrame(font, List.of(hoveredReqTooltip), mouseX, mouseY);
        }
    }

    private void renderRequirementsTab(GuiGraphics graphics) {
        int ox = (width  - spec().width)  / 2;
        int oy = (height - spec().height) / 2;
        int y  = oy + 64; // tab content top + small padding

        if (hasSpaceRequirement()) {
            var status = getSpaceStatus();
            int color = status.satisfied() ? SATISFIED : UNSATISFIED;
            graphics.drawCenteredString(font, "Space: " + status.found() + "/" + status.required(),
                    width / 2, y, color);
            y += 16;
        }

        var reqs = structureType.getRequirements();
        if (!reqs.isEmpty()) {
            graphics.drawString(font, "Requirements:", ox + 10, y, 0xFF404040, false);
            y += 14;
            hoveredReqTooltip = drawSlots(graphics, reqs, getRequirementStatuses(), y);
            y += SLOT_SIZE + 10;
        }

        if (hasRangeRequirement() && !structureType.getRangeRequirements().isEmpty()) {
            var rangeReqs = structureType.getRangeRequirements();
            graphics.drawString(font, "Nearby (r=" + structureType.getRangeRadius() + "):", ox + 10, y, 0xFF404040, false);
            y += 14;
            var tooltip = drawSlots(graphics, rangeReqs, getRangeRequirementStatuses(), y);
            if (tooltip != null) hoveredReqTooltip = tooltip;
        }
    }

    private Component drawSlots(GuiGraphics graphics,
                                 List<StructureType.Requirement> reqs,
                                 List<DominionStructure.RequirementStatus> statuses,
                                 int slotY) {
        int totalWidth = reqs.size() * SLOT_SIZE + (reqs.size() - 1) * SLOT_GAP;
        int startX = (width - totalWidth) / 2;
        Component tooltip = null;

        for (int i = 0; i < reqs.size(); i++) {
            var req = reqs.get(i);
            boolean satisfied = i < statuses.size() && statuses.get(i).satisfied();
            int slotX = startX + i * (SLOT_SIZE + SLOT_GAP);

            graphics.fill(slotX,     slotY,     slotX + SLOT_SIZE,     slotY + SLOT_SIZE,     satisfied ? 0xFF22CC22 : 0xFFCC2222);
            graphics.fill(slotX + 1, slotY + 1, slotX + SLOT_SIZE - 1, slotY + SLOT_SIZE - 1, satisfied ? 0xFF1A3A1A : 0xFF3A1A1A);
            graphics.renderItem(req.icon().get(), slotX + 2, slotY + 2);

            if (lastMouseX >= slotX && lastMouseX < slotX + SLOT_SIZE
                    && lastMouseY >= slotY && lastMouseY < slotY + SLOT_SIZE) {
                int found = i < statuses.size() ? statuses.get(i).found() : 0;
                String status = satisfied ? "OK" : found + "/" + req.count();
                tooltip = Component.literal(req.displayName() + " (" + status + ")");
            }
        }
        return tooltip;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // --- helpers ---

    private boolean isHouse()       { return structureType == StructureType.HOUSE; }
    private boolean isStorehouse()  { return structureType == StructureType.STOREHOUSE; }
    private boolean hasJob()        { return structureType.getJobType().isPresent(); }
    private boolean hasWorkArea()   { return structureType.getJobType().map(JobType::hasWorkArea).orElse(false); }
    private boolean hasSpaceRequirement()  { return structureType.getMinSize() > 0; }
    private boolean hasRangeRequirement()  { return structureType.getRangeRadius() > 0; }

    private boolean hasUnmetRequirement() {
        if (hasSpaceRequirement() && !getSpaceStatus().satisfied()) return true;
        for (var s : getRequirementStatuses()) if (!s.satisfied()) return true;
        if (hasRangeRequirement()) for (var s : getRangeRequirementStatuses()) if (!s.satisfied()) return true;
        return false;
    }

    private String labelFor(String assignedId) {
        if (assignedId == null || assignedId.isEmpty()) return "None";
        return inhabitants.stream()
                .filter(i -> i.id().equals(assignedId))
                .map(InhabitantEntry::name)
                .findFirst()
                .orElse("None");
    }

    private List<DominionStructure.RequirementStatus> getRequirementStatuses() {
        var mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getBlockEntity(markerPos) instanceof StructureMarkerBlockEntity be) {
            var s = be.getRequirementStatuses();
            if (!s.isEmpty()) return s;
        }
        return structureType.getRequirements().stream()
                .map(r -> new DominionStructure.RequirementStatus(r.displayName(), r.count(), 0))
                .toList();
    }

    private List<DominionStructure.RequirementStatus> getRangeRequirementStatuses() {
        var mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getBlockEntity(markerPos) instanceof StructureMarkerBlockEntity be) {
            var s = be.getRangeRequirementStatuses();
            if (!s.isEmpty()) return s;
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

    private void openResidentPicker() {
        var candidates = inhabitants.stream()
                .map(i -> new InhabitantSelectionScreen.Candidate(i.id(), i.entityUuid(), i.name(),
                        i.homed() ? "Housed" : "", i.homed() && !i.id().equals(assignedInhabitantId)))
                .toList();
        Minecraft.getInstance().setScreen(new InhabitantSelectionScreen(this,
                Component.literal("Assign Resident"), candidates, assignedInhabitantId, id -> {
            assignedInhabitantId = id;
            ModNetwork.sendAssignHouse(markerPos, id);
        }));
    }

    private void openWorkerPicker() {
        var candidates = inhabitants.stream()
                .map(i -> new InhabitantSelectionScreen.Candidate(i.id(), i.entityUuid(), i.name(),
                        i.jobLabel(), !i.jobLabel().isEmpty() && !i.id().equals(assignedWorkerId)))
                .toList();
        Minecraft.getInstance().setScreen(new InhabitantSelectionScreen(this,
                Component.literal("Assign Worker"), candidates, assignedWorkerId, id -> {
            assignedWorkerId = id;
            ModNetwork.sendAssignWorker(markerPos, id);
        }));
    }

    private static List<InhabitantEntry> parseInhabitants(String data) {
        var list = new ArrayList<InhabitantEntry>();
        if (data == null || data.isEmpty()) return list;
        for (var entry : data.split(",")) {
            var fields = entry.split(ModNetwork.INHABITANT_FIELD_SEP, -1);
            if (fields.length < 5) continue;
            list.add(new InhabitantEntry(fields[0], UUID.fromString(fields[1]), fields[2], fields[3],
                    "1".equals(fields[4])));
        }
        return list;
    }
}
