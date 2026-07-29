package com.sheepfromheaven.minenow.client;

import com.sheepfromheaven.minenow.dominion.NeedType;
import com.sheepfromheaven.minenow.network.ModNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class InhabitantScreen extends Screen {
    private static final int PANEL_WIDTH = 200;
    private static final int PANEL_HEIGHT = 190;
    private static final int TAB_HEIGHT = 20;
    private static final int BAR_WIDTH = 160;
    private static final int BAR_HEIGHT = 10;
    private static final int NEED_BLOCK_HEIGHT = 30;
    private static final int DEBUG_REFRESH_INTERVAL_TICKS = 10;

    private static final int PANEL_FILL = 0xFFC6C6C6;
    private static final int TEXT_DARK = 0xFF404040;

    private enum Tab { GENERAL, NEEDS, DEBUG }

    private final int entityId;
    private final String inhabitantName;
    private final String dominionName;
    private float shelter;
    private float food;
    private float rest;
    private String jobStatus;
    private final boolean debugEnabled;
    private List<String> issues;

    private static final int REFRESH_INTERVAL_TICKS = 20;
    private int refreshCooldown = REFRESH_INTERVAL_TICKS;

    private Tab currentTab = Tab.GENERAL;
    private List<String> debugLines = List.of();
    private int debugRefreshCooldown;

    public InhabitantScreen(int entityId, String inhabitantName, String dominionName, float shelter, float food, float rest,
                             String jobStatus, boolean debugEnabled, List<String> issues) {
        super(Component.literal("Inhabitant"));
        this.entityId = entityId;
        this.inhabitantName = inhabitantName;
        this.dominionName = dominionName;
        this.shelter = shelter;
        this.food = food;
        this.rest = rest;
        this.jobStatus = jobStatus;
        this.debugEnabled = debugEnabled;
        this.issues = issues;
    }

    public boolean matchesEntity(int entityId) {
        return this.entityId == entityId;
    }

    public void setDebugLines(List<String> debugLines) {
        this.debugLines = debugLines;
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

    @Override
    protected void init() {
        super.init();
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;
        var tabs = new ArrayList<Tab>();
        tabs.add(Tab.GENERAL);
        tabs.add(Tab.NEEDS);
        if (debugEnabled) tabs.add(Tab.DEBUG);
        int tabWidth = (PANEL_WIDTH - 20) / tabs.size();

        for (int i = 0; i < tabs.size(); i++) {
            var tab = tabs.get(i);
            addRenderableWidget(new NiceTabButton(left + 10 + tabWidth * i, top + 40, tabWidth, TAB_HEIGHT,
                    Component.literal(tabLabel(tab)), () -> currentTab == tab, () -> {
                currentTab = tab;
                if (tab == Tab.DEBUG) requestDebugRefresh();
                rebuildWidgets();
            }));
        }

        if (currentTab == Tab.GENERAL) {
            addRenderableWidget(Button.builder(Component.literal("Go Home"), button ->
                    ModNetwork.sendGoHome(entityId)
            ).pos(left + 10, top + 70).size(PANEL_WIDTH - 20, 20).build());

            addRenderableWidget(Button.builder(Component.literal("View Inventory"), button ->
                    ModNetwork.sendViewInventory(entityId)
            ).pos(left + 10, top + 96).size(PANEL_WIDTH - 20, 20).build());

            addRenderableWidget(Button.builder(BeveledBox.withAlertMark("Issues", !issues.isEmpty()), button ->
                    minecraft.setScreen(new IssuesListScreen(this, Component.literal(inhabitantName + "'s Issues"),
                            issues.stream().map(IssuesListScreen.IssueEntry::new).toList()))
            ).pos(left + 10, top + 122).size(PANEL_WIDTH - 20, 20).build());
        }
    }

    private static String tabLabel(Tab tab) {
        return switch (tab) {
            case GENERAL -> "General";
            case NEEDS -> "Needs";
            case DEBUG -> "Debug";
        };
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
        if (currentTab == Tab.DEBUG && --debugRefreshCooldown <= 0) {
            requestDebugRefresh();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;

        drawCentered(graphics, inhabitantName, top + 8, TEXT_DARK);
        drawCentered(graphics, dominionName, top + 22, 0xFF8A6D00);

        if (currentTab == Tab.GENERAL) {
            drawCentered(graphics, "Job: " + jobStatus, top + 152, TEXT_DARK);
        } else if (currentTab == Tab.NEEDS) {
            renderNeedsTab(graphics, left, top);
        } else if (currentTab == Tab.DEBUG) {
            renderDebugTab(graphics, left, top);
        }
    }

    private void renderDebugTab(GuiGraphics graphics, int left, int top) {
        int y = top + 66;
        if (debugLines.isEmpty()) {
            graphics.drawString(font, "Loading...", left + 10, y, TEXT_DARK, false);
            return;
        }
        for (var line : debugLines) {
            graphics.drawString(font, line, left + 10, y, TEXT_DARK, false);
            y += 10;
        }
    }

    private void drawCentered(GuiGraphics graphics, String text, int y, int color) {
        graphics.drawString(font, text, width / 2 - font.width(text) / 2, y, color, false);
    }

    private void renderNeedsTab(GuiGraphics graphics, int left, int top) {
        int barLeft = left + (PANEL_WIDTH - BAR_WIDTH) / 2;
        int contentTop = top + 70;

        graphics.drawString(font, "Needs", left + 10, contentTop, TEXT_DARK, false);

        var types = NeedType.values();
        var values = new float[] { shelter, food, rest };
        for (int i = 0; i < types.length; i++) {
            drawNeedBar(graphics, barLeft, contentTop + 18 + i * NEED_BLOCK_HEIGHT, types[i], values[i]);
        }
    }

    private void drawNeedBar(GuiGraphics graphics, int x, int y, NeedType type, float value) {
        graphics.drawString(font, type.getDisplayName(), x, y, TEXT_DARK, false);

        int barY = y + 12;
        int fillColor = value >= 0.66f ? 0xFF3CB043 : value >= 0.33f ? 0xFFD9A400 : 0xFFCC3333;
        int filled = Math.round(BAR_WIDTH * Math.max(0.0f, Math.min(1.0f, value)));

        graphics.fill(x, barY, x + BAR_WIDTH, barY + BAR_HEIGHT, 0xFF2B2B2B);
        if (filled > 0) {
            graphics.fill(x, barY, x + filled, barY + BAR_HEIGHT, fillColor);
        }
        graphics.fill(x, barY, x + BAR_WIDTH, barY + 1, BeveledBox.SHADOW);
        graphics.fill(x, barY + BAR_HEIGHT - 1, x + BAR_WIDTH, barY + BAR_HEIGHT, BeveledBox.SHADOW);
        graphics.fill(x, barY, x + 1, barY + BAR_HEIGHT, BeveledBox.SHADOW);
        graphics.fill(x + BAR_WIDTH - 1, barY, x + BAR_WIDTH, barY + BAR_HEIGHT, BeveledBox.SHADOW);

        String percent = Math.round(value * 100) + "%";
        graphics.drawCenteredString(font, percent, x + BAR_WIDTH / 2, barY + 1, 0xFFFFFFFF);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);

        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;

        BeveledBox.draw(graphics, left, top, PANEL_WIDTH, PANEL_HEIGHT, PANEL_FILL, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
