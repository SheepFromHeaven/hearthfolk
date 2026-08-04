package com.sheepfromheaven.minenow.client;

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

import java.util.List;

public class DominionScreen extends SpecScreen {

    private static final int INHABITANT_LINE_H = 12;

    private final String dominionName;
    private final DominionTier tier;
    private final BlockPos bannerPos;
    private final List<String> inhabitantNames;
    private String inputValue;

    private DominionScreen(ScreenSpec spec, String dominionName, DominionTier tier,
                           BlockPos bannerPos, List<String> inhabitantNames) {
        super(Component.literal("Dominion"), spec);
        this.dominionName = dominionName;
        this.tier = tier;
        this.bannerPos = bannerPos;
        this.inhabitantNames = inhabitantNames;
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

    public static DominionScreen create(String dominionName, DominionTier tier,
                                        BlockPos bannerPos, List<String> inhabitantNames) {
        ScreenSpec spec = ScreenSpecLoader.fromResource(
                Minecraft.getInstance().getResourceManager(), "minenow", "dominion_screen");
        return new DominionScreen(spec, dominionName, tier, bannerPos, inhabitantNames);
    }

    @Override
    protected void init() {
        super.init();
        EditBox nameInput = getWidget("input_name");
        if (nameInput != null) nameInput.setValue(inputValue);
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

        // Draw individual inhabitant names below the header label
        if (!inhabitantNames.isEmpty()) {
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
}
