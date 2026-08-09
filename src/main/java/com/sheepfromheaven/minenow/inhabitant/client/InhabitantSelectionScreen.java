package com.sheepfromheaven.minenow.inhabitant.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import sheepfromheaven.screenspec.runtime.ScreenSpec;
import sheepfromheaven.screenspec.runtime.ScreenSpecLoader;
import sheepfromheaven.screenspec.runtime.SpecScreen;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

public class InhabitantSelectionScreen extends SpecScreen {

    private static final int ROW_HEIGHT = 24;
    private static final int HEAD_SIZE  = 20;
    private static final int TEXT_DARK  = 0xFF404040;
    private static final int TEXT_GREY  = 0xFF8A8A8A;

    public record Candidate(String id, UUID entityUuid, String name, String statusLabel, boolean busy) {}

    private final Screen parent;
    private final List<Candidate> candidates;
    private final String currentlyAssignedId;
    private final List<String> currentlyAssignedIds;
    private final Consumer<String> onSelect;

    public InhabitantSelectionScreen(Screen parent, Component title, List<Candidate> candidates,
                                     String currentlyAssignedId, Consumer<String> onSelect) {
        super(title, loadSpec());
        this.parent = parent;
        this.candidates = candidates;
        this.currentlyAssignedId = currentlyAssignedId;
        this.currentlyAssignedIds = null;
        this.onSelect = onSelect;
    }

    private InhabitantSelectionScreen(Screen parent, Component title, List<Candidate> candidates,
                                      List<String> currentlyAssignedIds, Consumer<String> onSelect) {
        super(title, loadSpec());
        this.parent = parent;
        this.candidates = candidates;
        this.currentlyAssignedId = null;
        this.currentlyAssignedIds = currentlyAssignedIds;
        this.onSelect = onSelect;
    }

    /**
     * A picker with no single "current" selection and no "None" unassign row - every click toggles that
     * candidate's own assignment via {@code onSelect} (add or remove), for structures that can hold
     * several workers at once rather than exactly one.
     */
    public static InhabitantSelectionScreen multiSelect(Screen parent, Component title, List<Candidate> candidates,
                                                         List<String> currentlyAssignedIds, Consumer<String> onSelect) {
        return new InhabitantSelectionScreen(parent, title, candidates, currentlyAssignedIds, onSelect);
    }

    private boolean isMultiSelect() {
        return currentlyAssignedIds != null;
    }

    private static ScreenSpec loadSpec() {
        return ScreenSpecLoader.fromResource(
                Minecraft.getInstance().getResourceManager(), "minenow", "inhabitant_selection_screen");
    }

    @Override
    protected void init() {
        super.init();

        int ox = (width  - spec().width)  / 2;
        int oy = (height - spec().height) / 2;

        var list = new InhabitantList(ox + 2, oy + 24, spec().width - 4, spec().height - 26);

        if (!isMultiSelect()) {
            list.add(new Row(new Candidate("", null, "None", "Unassign", false),
                    currentlyAssignedId == null || currentlyAssignedId.isEmpty()));
        }

        candidates.stream()
                .sorted(Comparator.comparing(Candidate::busy).thenComparing(c -> c.name().toLowerCase(Locale.ROOT)))
                .forEach(c -> list.add(new Row(c, isMultiSelect() ? currentlyAssignedIds.contains(c.id()) : c.id().equals(currentlyAssignedId))));

        addRenderableWidget(list);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        bindText("label_title", getTitle().getString());
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() { Minecraft.getInstance().setScreen(parent); }

    private void pick(Candidate candidate) {
        onSelect.accept(candidate.id());
        Minecraft.getInstance().setScreen(parent);
    }

    private class InhabitantList extends ObjectSelectionList<Row> {
        InhabitantList(int x, int y, int width, int height) {
            super(Minecraft.getInstance(), width, height, y, ROW_HEIGHT);
            setX(x);
        }

        void add(Row row) { addEntry(row); }
    }

    private class Row extends ObjectSelectionList.Entry<Row> {
        private final Candidate candidate;
        private final boolean current;
        private LivingEntity cachedEntity;

        Row(Candidate candidate, boolean current) {
            this.candidate = candidate;
            this.current = current;
        }

        @Override
        public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
            int x = getContentX();
            int y = getContentY();

            if (current) {
                graphics.fill(x, y, getContentRight(), getContentBottom(), 0x553CB043);
            } else if (hovered) {
                graphics.fill(x, y, getContentRight(), getContentBottom(), 0x33FFFFFF);
            }

            if (cachedEntity == null && candidate.entityUuid() != null) {
                cachedEntity = findLiveEntity(candidate.entityUuid());
            }
            if (cachedEntity != null) {
                int headTop = y + (getContentHeight() - HEAD_SIZE) / 2;
                int x1 = x + 2 + HEAD_SIZE;
                int y1 = headTop + HEAD_SIZE;
                InventoryScreen.renderEntityInInventoryFollowsMouse(graphics,
                        x + 2, headTop, x1, y1, HEAD_SIZE / 2, 0.0F,
                        (x + 2 + x1) / 2f, (headTop + y1) / 2f, cachedEntity);
            } else {
                graphics.fill(x + 2, y + (getContentHeight() - HEAD_SIZE) / 2,
                        x + 2 + HEAD_SIZE, y + (getContentHeight() - HEAD_SIZE) / 2 + HEAD_SIZE, 0xFF555555);
            }

            int textColor = candidate.busy() ? TEXT_GREY : TEXT_DARK;
            String label = candidate.statusLabel().isEmpty()
                    ? candidate.name()
                    : candidate.name() + " (" + candidate.statusLabel() + ")";
            graphics.drawString(font, label, x + 2 + HEAD_SIZE + 6,
                    y + (getContentHeight() - 8) / 2, textColor, false);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
            pick(candidate);
            return true;
        }

        @Override
        public Component getNarration() { return Component.literal(candidate.name()); }
    }

    private static LivingEntity findLiveEntity(UUID entityUuid) {
        var level = Minecraft.getInstance().level;
        if (level == null) return null;
        for (var entity : level.entitiesForRendering()) {
            if (entity.getUUID().equals(entityUuid) && entity instanceof LivingEntity living) {
                return living;
            }
        }
        return null;
    }
}
