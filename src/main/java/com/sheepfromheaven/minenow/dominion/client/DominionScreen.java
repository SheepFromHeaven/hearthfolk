package com.sheepfromheaven.minenow.dominion.client;

import com.sheepfromheaven.minenow.dominion.DominionTier;
import com.sheepfromheaven.minenow.dominion.inventory.DominionMenu;
import com.sheepfromheaven.minenow.network.ModNetwork;
import com.sheepfromheaven.minenow.pathfinding.client.RangeTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import sheepfromheaven.screenspec.runtime.CustomWidgetRegistry;
import sheepfromheaven.screenspec.runtime.ScreenSpecLoader;
import sheepfromheaven.screenspec.runtime.SpecContainerScreen;
import sheepfromheaven.screenspec.runtime.WidgetSpec;

import javax.annotation.Nullable;
import java.util.List;

/** The dominion screen: general info, inhabitants, requirements progress, and a Banner tab with a real slot for applying a template banner's design - see {@link DominionMenu}. */
public class DominionScreen extends SpecContainerScreen<DominionMenu> {

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

    public DominionScreen(DominionMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, ScreenSpecLoader.fromResource(
                Minecraft.getInstance().getResourceManager(), "minenow", "dominion_screen"));
        this.dominionName = menu.getDominionName();
        this.tier = DominionTier.valueOf(menu.getTier());
        this.bannerPos = menu.getBannerPos();
        this.inhabitantNames = menu.getInhabitantNames().isEmpty()
                ? List.of() : List.of(menu.getInhabitantNames().split(","));
        this.milestoneProgress = parseMilestoneProgress(menu.getMilestoneProgressData());
        this.inputValue = dominionName;

        CustomWidgetRegistry.register("minenow:inhabitant_area", (graphics, widget, x, y) -> renderInhabitantArea(graphics, x, y));
        CustomWidgetRegistry.register("minenow:requirements_area", (graphics, widget, x, y) -> renderRequirementsArea(graphics, x, y));
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
        EditBox nameInput = getWidget("input_name");
        if (nameInput != null) nameInput.setValue(inputValue);
    }

    @Override
    protected void onAction(String actionId, WidgetSpec spec, Object value) {
        switch (actionId) {
            case "minenow.on_input" -> inputValue = (String) value;
            case "minenow.rename" -> {
                String trimmed = inputValue.trim();
                if (!trimmed.isEmpty() && !trimmed.equals(dominionName)) {
                    ModNetwork.sendRenameDominion(bannerPos, trimmed);
                }
            }
            case "minenow.toggle_range" -> {
                RangeTracker.toggleRange(bannerPos);
                rebuildWidgets();
            }
            case "minenow.requests" -> ModNetwork.sendRequestRequestsScreen(bannerPos);
            case "minenow.apply_banner" -> ModNetwork.sendApplyBanner(bannerPos);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        bindText("label_tier", tier.getDisplayName());
        bindText("label_inhabitants_header", inhabitantNames.isEmpty() ? "" : inhabitantNames.size() + " inhabitants");
        bindText("btn_range", RangeTracker.isShowingRange(bannerPos) ? "Hide Range" : "Show Range");
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void renderInhabitantArea(GuiGraphics graphics, int x, int y) {
        for (var name : inhabitantNames) {
            graphics.drawString(getFont(), name, x, y, 0xFF303030, false);
            y += INHABITANT_LINE_H;
        }
    }

    private void renderRequirementsArea(GuiGraphics graphics, int x, int y) {
        if (milestoneProgress == null) {
            graphics.drawString(getFont(), "Max tier reached.", x, y, 0xFF404040, false);
            return;
        }
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
