package com.sheepfromheaven.minenow.client;

import com.sheepfromheaven.minenow.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import sheepfromheaven.screenspec.runtime.CustomWidgetRegistry;
import sheepfromheaven.screenspec.runtime.ScreenSpecLoader;
import sheepfromheaven.screenspec.runtime.SpecScreen;
import sheepfromheaven.screenspec.runtime.WidgetSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows all pending item requests across a dominion, grouped by structure.
 * Urgent requests (work is blocked) are shown in red; soft requests (proactive restocking) in grey.
 */
public class RequestsScreen extends SpecScreen {

    private static final int ROW_HEIGHT = 12;
    private static final int TEXT_SOFT = 0xFF666666;
    private static final int TEXT_URGENT = 0xFFAA2222;
    private static final int TEXT_HEADER = 0xFF303030;

    private static final int REFRESH_INTERVAL_TICKS = 20;

    public record RequestEntry(String structureName, String label, int amount, boolean urgent) {}

    private final Screen parent;
    private final String dominionName;
    private final List<RequestEntry> entries;
    private final BlockPos bannerPos;
    private int refreshCooldown = REFRESH_INTERVAL_TICKS;

    public RequestsScreen(Screen parent, String dominionName, List<RequestEntry> entries, BlockPos bannerPos) {
        super(Component.translatable("screen.minenow.requests_screen"),
                ScreenSpecLoader.fromResource(Minecraft.getInstance().getResourceManager(), "minenow", "requests_screen"));
        this.parent = parent;
        this.dominionName = dominionName;
        this.entries = new ArrayList<>(entries);
        this.bannerPos = bannerPos;

        CustomWidgetRegistry.register("requests_list", (graphics, widget, x, y) -> renderRequestsList(graphics, x, y));
    }

    public void update(List<RequestEntry> fresh) {
        boolean changed = !this.entries.equals(fresh);
        if (changed) {
            this.entries.clear();
            this.entries.addAll(fresh);
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
    protected void onAction(String actionId, WidgetSpec spec, Object value) {
        if ("minenow.close".equals(actionId)) {
            onClose();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        bindText("label_title", "Requests — " + dominionName);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void renderRequestsList(GuiGraphics graphics, int x, int y) {
        if (entries.isEmpty()) {
            graphics.drawString(getFont(), "No pending requests", x, y, TEXT_SOFT, false);
            return;
        }
        String lastStructure = null;
        for (var entry : entries) {
            if (!entry.structureName().equals(lastStructure)) {
                graphics.drawString(getFont(), entry.structureName(), x, y, TEXT_HEADER, false);
                lastStructure = entry.structureName();
                y += ROW_HEIGHT;
            }
            int color = entry.urgent() ? TEXT_URGENT : TEXT_SOFT;
            String prefix = entry.urgent() ? "! " : "  ";
            graphics.drawString(getFont(), prefix + entry.label() + "  x" + entry.amount(), x + 8, y, color, false);
            y += ROW_HEIGHT;
        }
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
}
