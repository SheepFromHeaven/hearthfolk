package com.sheepfromheaven.minenow.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import sheepfromheaven.screenspec.runtime.ScreenSpec;
import sheepfromheaven.screenspec.runtime.SpecScreen;
import sheepfromheaven.screenspec.runtime.ScreenSpecLoader;
import sheepfromheaven.screenspec.runtime.SpecListWidget;

import java.util.List;
import java.util.Map;

/**
 * Scrollable list of inhabitant issues (missing tools, blocked jobs, unmet house requirements, ...).
 * Shared by the single-inhabitant "Issues" button and a dominion-wide message board.
 */
public class IssuesListScreen extends SpecScreen {

    /** One issue line, optionally attributed to a subject (e.g. an inhabitant name for a multi-inhabitant board). */
    public record IssueEntry(String subject, String message) {
        public IssueEntry(String message) {
            this("", message);
        }
    }

    private final Screen parent;
    private final List<IssueEntry> issues;

    private IssuesListScreen(ScreenSpec spec, Screen parent, Component title, List<IssueEntry> issues) {
        super(title, spec);
        this.parent = parent;
        this.issues = issues;
    }

    public static IssuesListScreen create(Screen parent, Component title, List<IssueEntry> issues) {
        ScreenSpec spec = ScreenSpecLoader.fromResource(
                Minecraft.getInstance().getResourceManager(), "minenow", "issues_screen");
        return new IssuesListScreen(spec, parent, title, issues);
    }

    @Override
    protected void init() {
        super.init();
        on("minenow.close", (id, s, v) -> onClose());
        SpecListWidget list = (SpecListWidget) getWidget("list_issues");
        if (list != null) {
            list.setItems(issues.stream()
                    .map(e -> Map.of("text", "• " + (e.subject().isEmpty() ? e.message() : e.subject() + ": " + e.message())))
                    .toList());
        }
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        bindText("label_title", getTitle().getString());

        if (issues.isEmpty()) {
            super.render(graphics, mouseX, mouseY, partialTick);
            graphics.drawCenteredString(getFont(), "No issues",
                    (int) (spec().width / 2f + (width - spec().width) / 2f),
                    (height - spec().height) / 2 + 36, 0xFF404040);
            return;
        }

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
}
