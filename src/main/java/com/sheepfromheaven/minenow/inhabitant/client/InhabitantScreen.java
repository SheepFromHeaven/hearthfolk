package com.sheepfromheaven.minenow.inhabitant.client;

import com.sheepfromheaven.minenow.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import sheepfromheaven.screenspec.runtime.ScreenSpec;
import sheepfromheaven.screenspec.runtime.SpecScreen;
import sheepfromheaven.screenspec.runtime.ScreenSpecLoader;
import sheepfromheaven.screenspec.runtime.SpecListWidget;

import java.util.List;
import java.util.Map;
import com.sheepfromheaven.minenow.client.BeveledBox;

public class InhabitantScreen extends SpecScreen {

    private static final int REFRESH_INTERVAL_TICKS = 20;
    private static final int DEBUG_REFRESH_INTERVAL_TICKS = 10;

    private final int entityId;
    private final String inhabitantName;
    private final String dominionName;
    private float shelter;
    private float food;
    private float rest;
    private String jobStatus;
    private final boolean debugEnabled;
    private List<String> issues;
    private List<String> debugLines = List.of();

    private int refreshCooldown = REFRESH_INTERVAL_TICKS;
    private int debugRefreshCooldown;

    public InhabitantScreen(int entityId, String inhabitantName, String dominionName,
                            float shelter, float food, float rest,
                            String jobStatus, boolean debugEnabled, List<String> issues) {
        super(Component.literal("Inhabitant"), loadSpec());
        this.entityId = entityId;
        this.inhabitantName = inhabitantName;
        this.dominionName = dominionName;
        this.shelter = shelter;
        this.food = food;
        this.rest = rest;
        this.jobStatus = jobStatus;
        this.debugEnabled = debugEnabled;
        this.issues = issues;

        on("minenow.go_home",         (id, s, v) -> ModNetwork.sendGoHome(entityId));
        on("minenow.view_inventory",   (id, s, v) -> ModNetwork.sendViewInventory(entityId));
        on("minenow.issues",           (id, s, v) -> Minecraft.getInstance().setScreen(
                IssuesListScreen.create(this,
                        Component.literal(inhabitantName + "'s Issues"),
                        this.issues.stream().map(IssuesListScreen.IssueEntry::new).toList())));
    }

    private static ScreenSpec loadSpec() {
        return ScreenSpecLoader.fromResource(
                Minecraft.getInstance().getResourceManager(), "minenow", "inhabitant_screen");
    }

    public boolean matchesEntity(int entityId) {
        return this.entityId == entityId;
    }

    public void update(float shelter, float food, float rest, String jobStatus, List<String> issues) {
        this.shelter = shelter;
        this.food = food;
        this.rest = rest;
        this.jobStatus = jobStatus;
        boolean issuesChanged = !this.issues.equals(issues);
        this.issues = issues;
        if (issuesChanged) rebuildWidgets();
    }

    public void setDebugLines(List<String> debugLines) {
        this.debugLines = debugLines;
        SpecListWidget list = (SpecListWidget) getWidget("list_debug");
        if (list != null) {
            list.setItems(debugLines.stream().map(l -> Map.of("text", l)).toList());
        }
    }

    @Override
    protected void init() {
        super.init();

        if (!debugEnabled) {
            AbstractWidget debugTab = getWidget("tab_debug");
            if (debugTab != null) debugTab.visible = false;
        }

        AbstractWidget issuesBtn = getWidget("btn_issues");
        if (issuesBtn != null) {
            issuesBtn.setMessage(issues.isEmpty()
                    ? Component.literal("Issues")
                    : Component.literal("Issues").append(
                            Component.literal(" !").withStyle(s -> s.withColor(BeveledBox.ALERT_RED))));
        }

        SpecListWidget debugList = (SpecListWidget) getWidget("list_debug");
        if (debugList != null && !debugLines.isEmpty()) {
            debugList.setItems(debugLines.stream().map(l -> Map.of("text", l)).toList());
        }
    }

    @Override
    protected void onTabSwitch(String tabsWidgetId, String newTabId) {
        if ("tab_debug".equals(newTabId)) {
            requestDebugRefresh();
        }
    }

    private void requestDebugRefresh() {
        ModNetwork.sendRequestInhabitantDebug(entityId);
        debugRefreshCooldown = DEBUG_REFRESH_INTERVAL_TICKS;
    }

    @Override
    public void tick() {
        super.tick();
        if (--refreshCooldown <= 0) {
            ModNetwork.sendRefreshInhabitantScreen(entityId);
            refreshCooldown = REFRESH_INTERVAL_TICKS;
        }
        if ("tab_debug".equals(activeTab("tabs_main")) && --debugRefreshCooldown <= 0) {
            requestDebugRefresh();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        bindText("label_name",     inhabitantName);
        bindText("label_dominion", dominionName);
        bindText("label_job_status", "Job: " + jobStatus);

        bindValue("progress_shelter", shelter);
        bindValue("progress_food",    food);
        bindValue("progress_rest",    rest);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
