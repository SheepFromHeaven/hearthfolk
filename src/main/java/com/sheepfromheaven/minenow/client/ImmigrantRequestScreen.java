package com.sheepfromheaven.minenow.client;

import com.sheepfromheaven.minenow.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import sheepfromheaven.screenspec.runtime.ScreenSpec;
import sheepfromheaven.screenspec.runtime.SpecScreen;
import sheepfromheaven.screenspec.runtime.ScreenSpecLoader;

/**
 * Shows an immigration candidate's Approve/Reject request to the dominion owner, mirroring
 * {@link DominionScreen}'s ScreenSpec + ModNetwork action pattern. Opened from
 * {@code InhabitantEntity#mobInteract} when the owner right-clicks a candidate awaiting a decision;
 * the actual approve/reject logic runs server-side in {@code InhabitantEntity#applyImmigrationDecision}.
 */
public class ImmigrantRequestScreen extends SpecScreen {

    private final int entityId;
    private final String candidateName;

    private ImmigrantRequestScreen(ScreenSpec spec, int entityId, String candidateName) {
        super(Component.literal("Immigration Request"), spec);
        this.entityId = entityId;
        this.candidateName = candidateName;

        on("minenow.approve", (id, s, value) -> {
            ModNetwork.sendImmigrationDecision(entityId, true);
            Minecraft.getInstance().setScreen(null);
        });
        on("minenow.reject", (id, s, value) -> {
            ModNetwork.sendImmigrationDecision(entityId, false);
            Minecraft.getInstance().setScreen(null);
        });
    }

    public static ImmigrantRequestScreen create(int entityId, String candidateName) {
        ScreenSpec spec = ScreenSpecLoader.fromResource(
                Minecraft.getInstance().getResourceManager(), "minenow", "immigrant_request_screen");
        return new ImmigrantRequestScreen(spec, entityId, candidateName);
    }

    public boolean matchesEntity(int entityId) {
        return this.entityId == entityId;
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        bindText("label_name", candidateName);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
