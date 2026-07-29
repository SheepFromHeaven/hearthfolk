package com.sheepfromheaven.minenow.client;

import com.sheepfromheaven.minenow.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows all pending item requests across a dominion, grouped by structure.
 * Urgent requests (work is blocked) are shown in red; soft requests (proactive restocking) in grey.
 */
public class RequestsScreen extends Screen {
    private static final int PANEL_WIDTH = 280;
    private static final int PANEL_HEIGHT = 220;
    private static final int ROW_HEIGHT = 16;

    private static final int PANEL_FILL = 0xFFC6C6C6;
    private static final int TEXT_DARK = 0xFF404040;
    private static final int TEXT_URGENT = 0xFFAA2222;
    private static final int TEXT_SOFT = 0xFF666666;
    private static final int TEXT_HEADER = 0xFF303030;

    public record RequestEntry(String structureName, String label, int amount, boolean urgent) {}

    private static final int REFRESH_INTERVAL_TICKS = 20;
    private int refreshCooldown = REFRESH_INTERVAL_TICKS;

    private final Screen parent;
    private final String dominionName;
    private final List<RequestEntry> entries;
    private net.minecraft.core.BlockPos bannerPos;

    public RequestsScreen(Screen parent, String dominionName, List<RequestEntry> entries, net.minecraft.core.BlockPos bannerPos) {
        super(Component.literal("Requests"));
        this.parent = parent;
        this.dominionName = dominionName;
        this.entries = new ArrayList<>(entries);
        this.bannerPos = bannerPos;
    }

    public void update(List<RequestEntry> fresh) {
        boolean changed = !this.entries.equals(fresh);
        if (changed) {
            this.entries.clear();
            this.entries.addAll(fresh);
            rebuildWidgets();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (--refreshCooldown <= 0) {
            ModNetwork.sendRequestRequestsScreen(bannerPos);
            refreshCooldown = REFRESH_INTERVAL_TICKS;
        }
    }

    @Override
    protected void init() {
        super.init();
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;

        if (!entries.isEmpty()) {
            var list = new RequestList(left + 2, top + 26, PANEL_WIDTH - 4, PANEL_HEIGHT - 56);
            buildRows(list);
            addRenderableWidget(list);
        }

        addRenderableWidget(Button.builder(Component.literal("Close"), b ->
                minecraft.setScreen(parent)
        ).pos(left + (PANEL_WIDTH - 100) / 2, top + PANEL_HEIGHT - 26).size(100, 20).build());
    }

    private void buildRows(RequestList list) {
        String lastStructure = null;
        for (var entry : entries) {
            if (!entry.structureName().equals(lastStructure)) {
                list.add(new HeaderRow(entry.structureName()));
                lastStructure = entry.structureName();
            }
            list.add(new EntryRow(entry));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int top = (height - PANEL_HEIGHT) / 2;
        String title = "Requests — " + dominionName;
        graphics.drawString(font, title, width / 2 - font.width(title) / 2, top + 8, TEXT_DARK, false);

        if (entries.isEmpty()) {
            String msg = "No pending requests";
            graphics.drawString(font, msg, width / 2 - font.width(msg) / 2, top + 26, TEXT_SOFT, false);
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

    /** Parses the flat wire encoding produced by {@link ModNetwork#encodeRequests}. */
    public static List<RequestEntry> decode(String data) {
        var result = new ArrayList<RequestEntry>();
        if (data.isEmpty()) return result;
        String sep = ModNetwork.INHABITANT_FIELD_SEP;
        for (var row : data.split(",", -1)) {
            var parts = row.split(java.util.regex.Pattern.quote(sep), -1);
            if (parts.length < 4) continue;
            try {
                result.add(new RequestEntry(parts[0], parts[1], Integer.parseInt(parts[2]), "1".equals(parts[3])));
            } catch (NumberFormatException ignored) {}
        }
        return result;
    }

    @SuppressWarnings("rawtypes")
    private class RequestList extends ObjectSelectionList {
        RequestList(int x, int y, int width, int height) {
            super(Minecraft.getInstance(), width, height, y, ROW_HEIGHT);
            setX(x);
        }

        @SuppressWarnings("unchecked")
        void add(ObjectSelectionList.Entry<?> row) {
            addEntry(row);
        }
    }

    private class HeaderRow extends ObjectSelectionList.Entry<HeaderRow> {
        private final String name;

        HeaderRow(String name) { this.name = name; }

        @Override
        public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
            graphics.drawString(font, name, getContentX() + 2, getContentY() + (getContentHeight() - 8) / 2, TEXT_HEADER, false);
        }

        @Override
        public Component getNarration() { return Component.literal(name); }
    }

    private class EntryRow extends ObjectSelectionList.Entry<EntryRow> {
        private final RequestEntry entry;

        EntryRow(RequestEntry entry) { this.entry = entry; }

        @Override
        public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
            int color = entry.urgent() ? TEXT_URGENT : TEXT_SOFT;
            String prefix = entry.urgent() ? "! " : "  ";
            String text = prefix + entry.label() + "  x" + entry.amount();
            graphics.drawString(font, text, getContentX() + 12, getContentY() + (getContentHeight() - 8) / 2, color, false);
        }

        @Override
        public Component getNarration() {
            return Component.literal(entry.label() + " x" + entry.amount());
        }
    }
}
