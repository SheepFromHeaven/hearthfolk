package com.sheepfromheaven.minenow.structure.client;

import com.sheepfromheaven.minenow.structure.block.StructureMarkerBlockEntity;
import com.sheepfromheaven.minenow.structure.MarkerKind;
import com.sheepfromheaven.minenow.structure.StructureType;
import com.sheepfromheaven.minenow.network.ModNetwork;
import com.sheepfromheaven.minenow.pathfinding.client.RangeTracker;
import com.sheepfromheaven.minenow.requirement.RequirementStatus;
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
import com.sheepfromheaven.minenow.client.BeveledBox;
import com.sheepfromheaven.minenow.inhabitant.client.InhabitantSelectionScreen;

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
    private List<String> assignedResidentIds;
    private List<String> assignedWorkerIds;
    private int level;
    private int levelCap;
    private List<RequirementStatus> currentRequirementStatuses;
    private List<RequirementStatus> levelUpStatuses;
    private int inhabitantSlots;
    private int functioningInhabitantSlots;

    private EditBox nameField;
    private int lastMouseX, lastMouseY;
    private Component hoveredReqTooltip;
    private int refreshCooldown = REFRESH_INTERVAL_TICKS;

    public record InhabitantEntry(String id, UUID entityUuid, String name, String jobLabel, boolean homed) {}

    public StructureMarkerScreen(String structureName, StructureType structureType, BlockPos markerPos,
                                 String inhabitantData, String assignedResidentData, String assignedWorkerData,
                                 int level, int levelCap, String requirementData, String levelUpRequirementData,
                                 int inhabitantSlots, int functioningInhabitantSlots) {
        super(Component.literal("Structure"), loadSpec(structureType));
        this.structureName = structureName;
        this.structureType = structureType;
        this.markerPos = markerPos;
        this.inhabitants = parseInhabitants(inhabitantData);
        this.assignedResidentIds = parseIdList(assignedResidentData);
        this.assignedWorkerIds = parseIdList(assignedWorkerData);
        this.level = level;
        this.levelCap = levelCap;
        this.currentRequirementStatuses = parseStatusList(requirementData);
        this.levelUpStatuses = parseStatusList(levelUpRequirementData);
        this.inhabitantSlots = inhabitantSlots;
        this.functioningInhabitantSlots = functioningInhabitantSlots;

        on("minenow.level_up", (id, s, v) -> ModNetwork.sendLevelUpStructure(markerPos));

        on("minenow.rename", (id, s, v) -> {
            if (nameField == null) return;
            String newName = nameField.getValue().trim();
            if (!newName.isEmpty() && !newName.equals(this.structureName)) {
                ModNetwork.sendRenameStructure(markerPos, newName);
                this.structureName = newName;
            }
        });

        on("minenow.assign_resident", (id, s, v) -> openResidentPicker());
        on("minenow.assign_worker",   (id, s, v) -> openWorkerPicker());

        on("minenow.recalculate", (id, s, v) -> {
            ModNetwork.sendRecalculateStructure(markerPos);
            onClose();
        });

        on("minenow.toggle_range", (id, s, v) -> {
            RangeTracker.toggleRange(markerPos);
            rebuildWidgets();
        });

        on("minenow.toggle_territory", (id, s, v) -> {
            TerritoryTracker.toggle(markerPos);
            rebuildWidgets();
        });

        on("minenow.open_filter", (id, s, v) -> ModNetwork.sendOpenStructureFilter(markerPos));
    }

    private static ScreenSpec loadSpec(StructureType type) {
        return ScreenSpecLoader.fromResource(
                Minecraft.getInstance().getResourceManager(), "minenow", type.name().toLowerCase() + "_screen");
    }

    public boolean matchesMarker(BlockPos pos) { return markerPos.equals(pos); }

    public void update(String inhabitantData, String assignedResidentData, String assignedWorkerData,
                        int level, int levelCap, String requirementData, String levelUpRequirementData,
                        int inhabitantSlots, int functioningInhabitantSlots) {
        var newInhabitants = parseInhabitants(inhabitantData);
        var newResidentIds = parseIdList(assignedResidentData);
        var newWorkerIds = parseIdList(assignedWorkerData);
        boolean changed = !this.assignedResidentIds.equals(newResidentIds)
                || !this.assignedWorkerIds.equals(newWorkerIds)
                || !this.inhabitants.equals(newInhabitants)
                || this.level != level;
        this.inhabitants.clear();
        this.inhabitants.addAll(newInhabitants);
        this.assignedResidentIds = newResidentIds;
        this.assignedWorkerIds = newWorkerIds;
        this.level = level;
        this.levelCap = levelCap;
        this.currentRequirementStatuses = parseStatusList(requirementData);
        this.levelUpStatuses = parseStatusList(levelUpRequirementData);
        this.inhabitantSlots = inhabitantSlots;
        this.functioningInhabitantSlots = functioningInhabitantSlots;
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

        AbstractWidget residentBtn = getWidget("btn_resident");
        if (residentBtn != null) {
            residentBtn.visible = isHouse() && !inhabitants.isEmpty();
            String residentNames = assignedResidentIds.isEmpty() ? "None" : assignedResidentIds.stream()
                    .map(this::labelFor)
                    .collect(java.util.stream.Collectors.joining(", "));
            residentBtn.setMessage(Component.literal("Residents (" + functioningInhabitantSlots + "/" + inhabitantSlots + "): " + residentNames));
        }

        AbstractWidget workerBtn = getWidget("btn_worker");
        if (workerBtn != null) {
            workerBtn.visible = hasJob() && !inhabitants.isEmpty();
            String workerNames = assignedWorkerIds.isEmpty() ? "None" : assignedWorkerIds.stream()
                    .map(this::labelFor)
                    .collect(java.util.stream.Collectors.joining(", "));
            workerBtn.setMessage(Component.literal("Workers (" + functioningInhabitantSlots + "/" + inhabitantSlots + "): " + workerNames));
        }

        // --- requirements tab label (alert mark if unmet) ---
        AbstractWidget reqTab = getWidget("tab_requirements");
        if (reqTab != null) reqTab.setMessage(BeveledBox.withAlertMark("Requirements", hasUnmetRequirement()));

        // The editable work area was removed - hide its (now defunct) tab if the layout still declares one.
        AbstractWidget workAreaTab = getWidget("tab_work_area");
        if (workAreaTab != null) workAreaTab.visible = false;

        AbstractWidget filterTab = getWidget("tab_filter");
        if (filterTab != null) filterTab.visible = isStorehouse();

        AbstractWidget levelUpBtn = getWidget("btn_level_up");
        if (levelUpBtn != null) {
            boolean maxLevel = level >= StructureType.MAX_LEVEL;
            boolean atTierCap = level >= levelCap;
            boolean requirementsMet = !levelUpStatuses.isEmpty() && levelUpStatuses.stream().allMatch(RequirementStatus::satisfied);
            boolean ready = !maxLevel && !atTierCap && requirementsMet;
            levelUpBtn.visible = !maxLevel;
            levelUpBtn.active = ready;
            levelUpBtn.setMessage(Component.literal(
                    ready ? "Level Up (" + level + " → " + (level + 1) + ")"
                            : atTierCap ? "Grow Dominion to Level Up"
                            : "Level " + level + " (requirements not met)"));
        }

        // --- debug tab ---
        AbstractWidget toggleRange = getWidget("btn_toggle_range");
        if (toggleRange != null) {
            toggleRange.visible = hasRangeRequirement();
            toggleRange.setMessage(
                    Component.literal(RangeTracker.isShowingRange(markerPos) ? "Hide Range" : "Show Range"));
        }

        AbstractWidget toggleTerritory = getWidget("btn_toggle_territory");
        if (toggleTerritory != null) {
            toggleTerritory.visible = MarkerKind.acceptedBy(structureType);
            toggleTerritory.setMessage(
                    Component.literal(TerritoryTracker.isShowing(markerPos) ? "Hide Territory" : "Show Territory"));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        hoveredReqTooltip = null;

        bindText("label_name", structureName);
        bindText("label_type", structureType.getDisplayName());
        bindText("btn_type", "Type: " + structureType.getDisplayName());

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

        graphics.drawCenteredString(font, "Level " + level + " / " + StructureType.MAX_LEVEL, width / 2, y, 0xFF404040);
        y += 14;

        if (!currentRequirementStatuses.isEmpty()) {
            graphics.drawString(font, "To operate at level " + level + ":", ox + 10, y, 0xFF404040, false);
            y += 12;
            y = drawStatusList(graphics, currentRequirementStatuses, ox, y);
            y += 6;
        }

        if (level < StructureType.MAX_LEVEL && !levelUpStatuses.isEmpty()) {
            graphics.drawString(font, "Next level:", ox + 10, y, 0xFF404040, false);
            y += 12;
            y = drawStatusList(graphics, levelUpStatuses, ox, y);
            y += 6;
        }

        if (hasRangeRequirement() && !structureType.getRangeRequirements().isEmpty()) {
            var rangeReqs = structureType.getRangeRequirements();
            graphics.drawString(font, "Nearby (r=" + structureType.getRangeRadius() + "):", ox + 10, y, 0xFF404040, false);
            y += 14;
            var tooltip = drawSlots(graphics, rangeReqs, getRangeRequirementStatuses(), y);
            if (tooltip != null) hoveredReqTooltip = tooltip;
        }
    }

    private int drawStatusList(GuiGraphics graphics, List<RequirementStatus> statuses, int ox, int y) {
        for (var status : statuses) {
            int color = status.satisfied() ? SATISFIED : UNSATISFIED;
            graphics.drawString(font, status.name() + ": " + status.found() + "/" + status.required(), ox + 14, y, color, false);
            y += 11;
        }
        return y;
    }

    private Component drawSlots(GuiGraphics graphics,
                                 List<StructureType.Requirement> reqs,
                                 List<RequirementStatus> statuses,
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
    private boolean hasRangeRequirement()  { return structureType.getRangeRadius() > 0; }

    private boolean hasUnmetRequirement() {
        for (var s : currentRequirementStatuses) if (!s.satisfied()) return true;
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

    private List<RequirementStatus> getRangeRequirementStatuses() {
        var mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getBlockEntity(markerPos) instanceof StructureMarkerBlockEntity be) {
            var s = be.getRangeRequirementStatuses();
            if (!s.isEmpty()) return s;
        }
        return structureType.getRangeRequirements().stream()
                .map(r -> new RequirementStatus(r.displayName(), r.count(), 0))
                .toList();
    }

    /**
     * Opens a picker where clicking a name toggles their assignment (add to the next open slot, or
     * remove if already here) rather than replacing a single selection - a house can have several
     * residents now. The player reopens this to assign more than one in a sitting.
     */
    private void openResidentPicker() {
        var candidates = inhabitants.stream()
                .map(i -> new InhabitantSelectionScreen.Candidate(i.id(), i.entityUuid(), i.name(),
                        i.homed() ? "Housed" : "", i.homed() && !assignedResidentIds.contains(i.id())))
                .toList();
        Minecraft.getInstance().setScreen(InhabitantSelectionScreen.multiSelect(this,
                Component.literal("Assign Residents"), candidates, assignedResidentIds, id -> {
            assignedResidentIds = toggle(assignedResidentIds, id);
            ModNetwork.sendAssignHouse(markerPos, id);
        }));
    }

    /**
     * Opens a picker where clicking a name toggles their assignment (add to the next open slot, or
     * remove if already here) rather than replacing a single selection - a structure can have several
     * workers now. The player reopens this to assign more than one in a sitting.
     */
    private void openWorkerPicker() {
        var candidates = inhabitants.stream()
                .map(i -> new InhabitantSelectionScreen.Candidate(i.id(), i.entityUuid(), i.name(),
                        i.jobLabel(), !i.jobLabel().isEmpty() && !assignedWorkerIds.contains(i.id())))
                .toList();
        Minecraft.getInstance().setScreen(InhabitantSelectionScreen.multiSelect(this,
                Component.literal("Assign Workers"), candidates, assignedWorkerIds, id -> {
            assignedWorkerIds = toggle(assignedWorkerIds, id);
            ModNetwork.sendAssignWorker(markerPos, id);
        }));
    }

    /** Adds {@code id} to a copy of {@code ids} if absent, removes it if present. */
    private static List<String> toggle(List<String> ids, String id) {
        if (ids.contains(id)) {
            return ids.stream().filter(existing -> !existing.equals(id)).toList();
        }
        var updated = new ArrayList<>(ids);
        updated.add(id);
        return updated;
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

    private static List<String> parseIdList(String data) {
        if (data == null || data.isEmpty()) return List.of();
        return List.of(data.split(","));
    }

    private static List<RequirementStatus> parseStatusList(String data) {
        var list = new ArrayList<RequirementStatus>();
        if (data == null || data.isEmpty()) return list;
        for (var entry : data.split(",")) {
            var fields = entry.split(ModNetwork.INHABITANT_FIELD_SEP, -1);
            if (fields.length < 3) continue;
            list.add(new RequirementStatus(fields[0], Integer.parseInt(fields[1]), Integer.parseInt(fields[2])));
        }
        return list;
    }
}
