package com.sheepfromheaven.minenow.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

public class InhabitantSelectionScreen extends Screen {
    private static final int PANEL_WIDTH = 220;
    private static final int PANEL_HEIGHT = 200;
    private static final int ROW_HEIGHT = 24;
    private static final int HEAD_SIZE = 20;

    private static final int PANEL_FILL = 0xFFC6C6C6;
    private static final int TEXT_DARK = 0xFF404040;
    private static final int TEXT_GREY = 0xFF8A8A8A;

    public record Candidate(String id, UUID entityUuid, String name, String statusLabel, boolean busy) {}

    private final Screen parent;
    private final List<Candidate> candidates;
    private final String currentlyAssignedId;
    private final Consumer<String> onSelect;

    private InhabitantList list;

    public InhabitantSelectionScreen(Screen parent, Component title, List<Candidate> candidates,
                                      String currentlyAssignedId, Consumer<String> onSelect) {
        super(title);
        this.parent = parent;
        this.candidates = candidates;
        this.currentlyAssignedId = currentlyAssignedId;
        this.onSelect = onSelect;
    }

    @Override
    protected void init() {
        super.init();
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;

        list = new InhabitantList(left + 2, top + 24, PANEL_WIDTH - 4, PANEL_HEIGHT - 26);

        list.add(new Row(new Candidate("", null, "None", "Unassign", false),
                currentlyAssignedId == null || currentlyAssignedId.isEmpty()));

        candidates.stream()
                .sorted(Comparator.comparing(Candidate::busy).thenComparing(c -> c.name().toLowerCase(Locale.ROOT)))
                .forEach(c -> list.add(new Row(c, c.id().equals(currentlyAssignedId))));

        addRenderableWidget(list);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;
        String title = getTitle().getString();
        graphics.drawString(font, title, width / 2 - font.width(title) / 2, top + 6, TEXT_DARK, false);
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

    private void pick(Candidate candidate) {
        onSelect.accept(candidate.id());
        Minecraft.getInstance().setScreen(parent);
    }

    private class InhabitantList extends ObjectSelectionList<Row> {
        InhabitantList(int x, int y, int width, int height) {
            super(Minecraft.getInstance(), width, height, y, ROW_HEIGHT);
            setX(x);
        }

        void add(Row row) {
            addEntry(row);
        }
    }

    private class Row extends ObjectSelectionList.Entry<Row> {
        private final Candidate candidate;
        private final boolean current;

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
            } else if (hovered && !candidate.busy()) {
                graphics.fill(x, y, getContentRight(), getContentBottom(), 0x33FFFFFF);
            }

            LivingEntity live = candidate.entityUuid() != null ? findLiveEntity(candidate.entityUuid()) : null;
            if (live != null) {
                int headTop = y + (getContentHeight() - HEAD_SIZE) / 2;
                int x0 = x + 2;
                int y0 = headTop;
                int x1 = x0 + HEAD_SIZE;
                int y1 = y0 + HEAD_SIZE;
                InventoryScreen.renderEntityInInventoryFollowsMouse(graphics,
                        x0, y0, x1, y1, HEAD_SIZE / 2, (x0 + x1) / 2f, (y0 + y1) / 2f, partialTick, live);
            } else {
                graphics.fill(x + 2, y + (getContentHeight() - HEAD_SIZE) / 2,
                        x + 2 + HEAD_SIZE, y + (getContentHeight() - HEAD_SIZE) / 2 + HEAD_SIZE, 0xFF555555);
            }

            int textColor = candidate.busy() ? TEXT_GREY : TEXT_DARK;
            int textX = x + 2 + HEAD_SIZE + 6;
            String label = candidate.statusLabel().isEmpty()
                    ? candidate.name()
                    : candidate.name() + " (" + candidate.statusLabel() + ")";
            graphics.drawString(font, label, textX, y + (getContentHeight() - 8) / 2, textColor, false);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
            if (candidate.busy()) return true;
            pick(candidate);
            return true;
        }

        @Override
        public Component getNarration() {
            return Component.literal(candidate.name());
        }
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
