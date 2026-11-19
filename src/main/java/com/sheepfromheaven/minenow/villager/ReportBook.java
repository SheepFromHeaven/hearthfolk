package com.sheepfromheaven.minenow.villager;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundOpenBookPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows a report on vanilla's book screen instead of in the chat, which is hard to read. The client opens a book from the
 * stack in the player's hand, so the player is sent the book in that slot, told to open it, and sent their real stack back:
 * the server never holds the book, and the screen keeps its own copy of the pages.
 */
public final class ReportBook {
    /** A page is about 14 rows of 19 or so characters. */
    static final int ROWS_PER_PAGE = 13;
    static final int CHARS_PER_ROW = 19;

    private ReportBook() {}

    static int rows(String text) {
        int rows = 0;
        for (String line : text.split("\n", -1)) rows += Math.max(1, (line.length() + CHARS_PER_ROW - 1) / CHARS_PER_ROW);
        return rows;
    }

    /** A section title: bold and underlined, and every title starts a new page. */
    public static Component heading(String title) {
        return Component.literal(title).withStyle(ChatFormatting.BOLD, ChatFormatting.UNDERLINE);
    }

    static boolean isHeading(Component line) {
        return line.getStyle().isBold() && line.getStyle().isUnderlined();
    }

    /**
     * The lines in order, a page at a time: each heading starts a page, and a page that runs over continues on the next
     * (a line is never split over two pages unless it is longer than a page).
     */
    static List<Component> pages(List<Component> lines) {
        List<Component> pages = new ArrayList<>();
        net.minecraft.network.chat.MutableComponent page = Component.empty();
        int used = 0;
        for (Component line : lines) {
            String text = line.getString();
            int rows = rows(text);
            if (used > 0 && (isHeading(line) || used + rows > ROWS_PER_PAGE)) {
                pages.add(page);
                page = Component.empty();
                used = 0;
            }
            if (used > 0) page.append("\n");
            page.append(isHeading(line) ? line.copy() : Component.literal(text));
            used += rows;
        }
        if (used > 0 || pages.isEmpty()) pages.add(page);
        return pages;
    }

    static ItemStack book(String title, List<Component> lines) {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        List<Filterable<Component>> pages = pages(lines).stream().map(Filterable::passThrough).toList();
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough(title), "Hearthfolk", 0, pages, true));
        return book;
    }

    /** Opens the report on the player's screen, from the slot of the hand that holds the inspector. */
    public static void show(ServerPlayer player, InteractionHand hand, String title, List<Component> lines) {
        int slot = hand == InteractionHand.MAIN_HAND ? 36 + player.getInventory().getSelectedSlot() : 45;
        ItemStack real = player.getItemInHand(hand);
        var menu = player.inventoryMenu;
        player.connection.send(new ClientboundContainerSetSlotPacket(menu.containerId, menu.incrementStateId(), slot, book(title, lines)));
        player.connection.send(new ClientboundOpenBookPacket(hand));
        player.connection.send(new ClientboundContainerSetSlotPacket(menu.containerId, menu.incrementStateId(), slot, real));
    }
}
