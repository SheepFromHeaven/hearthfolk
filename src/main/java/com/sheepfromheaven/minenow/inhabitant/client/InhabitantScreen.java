package com.sheepfromheaven.minenow.inhabitant.client;

import com.sheepfromheaven.minenow.client.BeveledBox;
import com.sheepfromheaven.minenow.inhabitant.inventory.InhabitantInventoryMenu;
import com.sheepfromheaven.minenow.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import sheepfromheaven.screenspec.runtime.CustomWidgetRegistry;
import sheepfromheaven.screenspec.runtime.ScreenSpecLoader;
import sheepfromheaven.screenspec.runtime.SpecContainerScreen;
import sheepfromheaven.screenspec.runtime.WidgetSpec;

import java.util.List;

public class InhabitantScreen extends SpecContainerScreen<InhabitantInventoryMenu> {

    private static final int REFRESH_INTERVAL_TICKS = 20;
    private static final int DEBUG_REFRESH_INTERVAL_TICKS = 10;
    private static final int DEBUG_LINE_HEIGHT = 10;

    private final int entityId;
    private String inhabitantName = "";
    private String dominionName = "";
    private float shelter;
    private float food;
    private float rest;
    private String jobStatus = "";
    private boolean debugEnabled;
    private List<String> issues = List.of();
    private List<String> debugLines = List.of();

    private int refreshCooldown = REFRESH_INTERVAL_TICKS;
    private int debugRefreshCooldown = DEBUG_REFRESH_INTERVAL_TICKS;

    public InhabitantScreen(InhabitantInventoryMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, ScreenSpecLoader.fromResource(
                Minecraft.getInstance().getResourceManager(), "minenow", "inhabitant_screen"));
        this.entityId = menu.entityId();
        this.inhabitantName = title.getString();

        CustomWidgetRegistry.register("minenow:debug_area", (graphics, widget, x, y) -> renderDebugArea(graphics, x, y));
    }

    public boolean matchesEntity(int entityId) {
        return this.entityId == entityId;
    }

    public void update(String inhabitantName, String dominionName, boolean debugEnabled,
                        float shelter, float food, float rest, String jobStatus, List<String> issues) {
        this.inhabitantName = inhabitantName;
        this.dominionName = dominionName;
        this.shelter = shelter;
        this.food = food;
        this.rest = rest;
        this.jobStatus = jobStatus;
        this.issues = issues;
        if (this.debugEnabled != debugEnabled) {
            this.debugEnabled = debugEnabled;
            clearWidgets();
            init();
        }
    }

    public void setDebugLines(List<String> debugLines) {
        this.debugLines = debugLines;
    }

    private void requestDebugRefresh() {
        ModNetwork.sendRequestInhabitantDebug(entityId);
        debugRefreshCooldown = DEBUG_REFRESH_INTERVAL_TICKS;
    }

    @Override
    protected void init() {
        super.init();
        if (!debugEnabled) {
            AbstractWidget debugTab = getWidget("tab_debug");
            if (debugTab != null) debugTab.visible = false;
        }
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (--refreshCooldown <= 0) {
            ModNetwork.sendRefreshInhabitantScreen(entityId);
            refreshCooldown = REFRESH_INTERVAL_TICKS;
        }
        if (debugEnabled && "tab_debug".equals(activeTab("tabs_main")) && --debugRefreshCooldown <= 0) {
            requestDebugRefresh();
        }
    }

    @Override
    protected void onAction(String actionId, WidgetSpec spec, Object value) {
        switch (actionId) {
            case "minenow.go_home" -> ModNetwork.sendGoHome(entityId);
            case "minenow.issues" -> Minecraft.getInstance().setScreen(
                    IssuesListScreen.create(this,
                            Component.literal(inhabitantName + "'s Issues"),
                            issues.stream().map(IssuesListScreen.IssueEntry::new).toList()));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        bindText("label_name", inhabitantName);
        bindText("label_dominion", dominionName);
        bindText("label_job_status", "Job: " + jobStatus);

        AbstractWidget issuesBtn = getWidget("btn_issues");
        if (issuesBtn != null) issuesBtn.setMessage(BeveledBox.withAlertMark("Issues", !issues.isEmpty()));

        bindValue("progress_shelter", shelter);
        bindValue("progress_food", food);
        bindValue("progress_rest", rest);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderDebugArea(GuiGraphics graphics, int x, int y) {
        for (var line : debugLines) {
            graphics.drawString(getFont(), line, x, y, 0xFF404040, false);
            y += DEBUG_LINE_HEIGHT;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
