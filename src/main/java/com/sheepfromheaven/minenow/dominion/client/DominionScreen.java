package com.sheepfromheaven.minenow.dominion.client;

import com.sheepfromheaven.minenow.dominion.DominionTier;
import com.sheepfromheaven.minenow.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.client.gui.components.AbstractWidget;
import sheepfromheaven.screenspec.runtime.ScreenSpec;
import sheepfromheaven.screenspec.runtime.SpecScreen;
import sheepfromheaven.screenspec.runtime.ScreenSpecLoader;

import javax.annotation.Nullable;
import java.util.List;
import com.sheepfromheaven.minenow.client.BeveledBox;
import com.sheepfromheaven.minenow.pathfinding.client.RangeTracker;

public class DominionScreen extends SpecScreen {

    private static final int INHABITANT_LINE_H = 12;
    private static final int SATISFIED   = 0xFF3CB043;
    private static final int UNSATISFIED = 0xFFCC3333;

    public record MilestoneProgress(int population, int structures, int storedResources,
                                     int minPopulation, int minStructures, int minStoredResources) {
        boolean satisfied() {
            return population >= minPopulation && structures >= minStructures && storedResources >= minStoredResources;
        }
    }

    private final String dominionName;
    private final DominionTier tier;
    private final BlockPos bannerPos;
    private final List<String> inhabitantNames;
    @Nullable
    private final MilestoneProgress milestoneProgress;
    private String inputValue;

    private DominionScreen(ScreenSpec spec, String dominionName, DominionTier tier,
                           BlockPos bannerPos, List<String> inhabitantNames, String milestoneProgressData) {
        super(Component.literal("Dominion"), spec);
        this.dominionName = dominionName;
        this.tier = tier;
        this.bannerPos = bannerPos;
        this.inhabitantNames = inhabitantNames;
        this.milestoneProgress = parseMilestoneProgress(milestoneProgressData);
        this.inputValue = dominionName;

        on("minenow.on_input", (id, s, value) -> inputValue = (String) value);
        on("minenow.rename", (id, s, value) -> {
            String trimmed = inputValue.trim();
            if (!trimmed.isEmpty() && !trimmed.equals(dominionName)) {
                ModNetwork.sendRenameDominion(bannerPos, trimmed);
            }
        });
        on("minenow.toggle_range", (id, s, value) -> {
            RangeTracker.toggleRange(bannerPos);
            rebuildWidgets();
        });
        on("minenow.requests", (id, s, value) -> ModNetwork.sendRequestRequestsScreen(bannerPos));
    }

    public static DominionScreen create(String dominionName, DominionTier tier, BlockPos bannerPos,
                                        List<String> inhabitantNames, String milestoneProgressData) {
        ScreenSpec spec = ScreenSpecLoader.fromResource(
                Minecraft.getInstance().getResourceManager(), "minenow", "dominion_screen");
        return new DominionScreen(spec, dominionName, tier, bannerPos, inhabitantNames, milestoneProgressData);
    }

    @Nullable
    private static MilestoneProgress parseMilestoneProgress(String data) {
        if (data == null || data.isEmpty()) return null;
        var parts = data.split(",");
        if (parts.length != 6) return null;
        var values = new int[6];
        for (int i = 0; i < 6; i++) values[i] = Integer.parseInt(parts[i]);
        return new MilestoneProgress(values[0], values[1], values[2], values[3], values[4], values[5]);
    }

    @Override
    protected void init() {
        super.init();
        if ("tab_general".equals(activeTab("tabs_main"))) {
            EditBox nameInput = getWidget("input_name");
            if (nameInput != null) nameInput.setValue(inputValue);
        }

        AbstractWidget reqTab = getWidget("tab_requirements");
        if (reqTab != null) {
            boolean unmet = milestoneProgress != null && !milestoneProgress.satisfied();
            reqTab.setMessage(BeveledBox.withAlertMark("Requirements", unmet));
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        bindText("label_name", dominionName);
        bindText("label_tier", tier.getDisplayName());

        String header = inhabitantNames.isEmpty() ? "" : "Inhabitants (" + inhabitantNames.size() + "):";
        bindText("label_inhabitants_header", header);

        // Update range button label each frame
        AbstractWidget rangeBtn = getWidget("btn_range");
        if (rangeBtn != null) rangeBtn.setMessage(Component.literal(
                RangeTracker.isShowingRange(bannerPos) ? "Hide Range" : "Show Range"));

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        String activeTab = activeTab("tabs_main");
        if (milestoneProgress != null && "tab_requirements".equals(activeTab)) {
            renderMilestoneProgress(guiGraphics);
        }

        // Draw individual inhabitant names below the header label
        if (!inhabitantNames.isEmpty() && "tab_inhabitants".equals(activeTab)) {
            var headerWidget = getWidget("label_inhabitants_header");
            if (headerWidget != null) {
                int x = headerWidget.getX();
                int y = headerWidget.getY() + 14;
                for (var name : inhabitantNames) {
                    guiGraphics.drawString(getFont(), "  " + name, x, y, 0xFF303030, false);
                    y += INHABITANT_LINE_H;
                }
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void renderMilestoneProgress(GuiGraphics graphics) {
        int ox = (width  - spec().width)  / 2;
        int oy = (height - spec().height) / 2;
        int x = ox + 10;
        int y = oy + 64; // tabs.y=40 + tab strip height (20) + small padding

        graphics.drawString(getFont(), "Next tier requires:", x, y, 0xFF404040, false);
        y += 12;
        y = drawMilestoneLine(graphics, x, y, "Population", milestoneProgress.population(), milestoneProgress.minPopulation());
        y = drawMilestoneLine(graphics, x, y, "Structures", milestoneProgress.structures(), milestoneProgress.minStructures());
        drawMilestoneLine(graphics, x, y, "Stored resources", milestoneProgress.storedResources(), milestoneProgress.minStoredResources());
    }

    private int drawMilestoneLine(GuiGraphics graphics, int x, int y, String label, int found, int required) {
        int color = found >= required ? SATISFIED : UNSATISFIED;
        graphics.drawString(getFont(), label + ": " + found + "/" + required, x, y, color, false);
        return y + 10;
    }
}
