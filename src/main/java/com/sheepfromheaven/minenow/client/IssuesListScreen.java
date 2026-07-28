package com.sheepfromheaven.minenow.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Scrollable list of inhabitant issues (missing tools, blocked jobs, unmet house requirements, ...).
 * Shared by the single-inhabitant "Issues" button and (eventually) a dominion-wide message board that
 * lists the same kind of entries across every inhabitant.
 */
public class IssuesListScreen extends Screen {
    private static final int PANEL_WIDTH = 240;
    private static final int PANEL_HEIGHT = 200;
    private static final int ROW_HEIGHT = 16;

    private static final int PANEL_FILL = 0xFFC6C6C6;
    private static final int TEXT_DARK = 0xFF404040;

    /** One issue line, optionally attributed to a subject (e.g. an inhabitant name for a multi-inhabitant board). */
    public record IssueEntry(String subject, String message) {
        public IssueEntry(String message) {
            this("", message);
        }
    }

    private final Screen parent;
    private final List<IssueEntry> issues;

    public IssuesListScreen(Screen parent, Component title, List<IssueEntry> issues) {
        super(title);
        this.parent = parent;
        this.issues = issues;
    }

    @Override
    protected void init() {
        super.init();
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;

        if (!issues.isEmpty()) {
            var list = new IssueList(left + 2, top + 26, PANEL_WIDTH - 4, PANEL_HEIGHT - 56);
            issues.forEach(issue -> list.add(new Row(issue)));
            addRenderableWidget(list);
        }

        addRenderableWidget(Button.builder(Component.literal("Close"), button ->
                minecraft.setScreen(parent)
        ).pos(left + (PANEL_WIDTH - 100) / 2, top + PANEL_HEIGHT - 26).size(100, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int top = (height - PANEL_HEIGHT) / 2;
        String titleText = getTitle().getString();
        graphics.drawString(font, titleText, width / 2 - font.width(titleText) / 2, top + 8, TEXT_DARK, false);

        if (issues.isEmpty()) {
            String noIssues = "No issues";
            graphics.drawString(font, noIssues, width / 2 - font.width(noIssues) / 2, top + 26, TEXT_DARK, false);
        }
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

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    private class IssueList extends ObjectSelectionList<Row> {
        IssueList(int x, int y, int width, int height) {
            super(Minecraft.getInstance(), width, height, y, ROW_HEIGHT);
            setX(x);
        }

        void add(Row row) {
            addEntry(row);
        }
    }

    private class Row extends ObjectSelectionList.Entry<Row> {
        private final IssueEntry entry;

        Row(IssueEntry entry) {
            this.entry = entry;
        }

        @Override
        public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
            String text = entry.subject().isEmpty() ? entry.message() : entry.subject() + ": " + entry.message();
            graphics.drawString(font, "• " + text, getContentX() + 4, getContentY() + (getContentHeight() - 8) / 2, TEXT_DARK, false);
        }

        @Override
        public Component getNarration() {
            return Component.literal(entry.message());
        }
    }
}
