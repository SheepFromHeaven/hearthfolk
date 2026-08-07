package com.sheepfromheaven.minenow.client;

import com.sheepfromheaven.minenow.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import sheepfromheaven.screenspec.runtime.ScreenSpec;
import sheepfromheaven.screenspec.runtime.SpecScreen;
import sheepfromheaven.screenspec.runtime.ScreenSpecLoader;

/**
 * Shows an immigration candidate's Approve/Reject request to the dominion owner, mirroring
 * {@link DominionScreen}'s ScreenSpec + ModNetwork action pattern. Opened from
 * {@code InhabitantEntity#mobInteract} when the owner right-clicks a candidate awaiting a decision;
 * the actual approve/reject logic runs server-side in {@code InhabitantEntity#applyImmigrationDecision}.
 * Lets the owner rename the candidate before approving - the name field starts pre-filled with the
 * candidate's rolled name but can be edited.
 */
public class ImmigrantRequestScreen extends SpecScreen {

    private final int entityId;
    private final String candidateName;
    private String inputValue;
    private EditBox nameInput;

    private ImmigrantRequestScreen(ScreenSpec spec, int entityId, String candidateName) {
        super(Component.literal("Immigration Request"), spec);
        this.entityId = entityId;
        this.candidateName = candidateName;
        this.inputValue = candidateName;

        on("minenow.on_input", (id, s, value) -> inputValue = (String) value);
        on("minenow.approve", (id, s, value) -> {
            String name = inputValue.trim();
            ModNetwork.sendImmigrationDecision(entityId, true, name.isEmpty() ? candidateName : name);
            Minecraft.getInstance().setScreen(null);
        });
        on("minenow.reject", (id, s, value) -> {
            ModNetwork.sendImmigrationDecision(entityId, false, candidateName);
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
    protected void init() {
        super.init();
        nameInput = getWidget("input_name");
        if (nameInput != null) nameInput.setValue(inputValue);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
