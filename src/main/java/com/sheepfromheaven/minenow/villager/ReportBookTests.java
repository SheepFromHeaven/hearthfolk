package com.sheepfromheaven.minenow.villager;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** Reports are shown on the pages of a written book, which the client opens as vanilla's book screen. */
public final class ReportBookTests {
    private ReportBookTests() {}

    private static List<Component> lines(int count, int width) {
        List<Component> lines = new ArrayList<>();
        for (int i = 0; i < count; i++) lines.add(Component.literal("line " + i + " " + "x".repeat(width)));
        return lines;
    }

    public static void aLongReportIsSplitOverPagesInOrderWithNothingLost(GameTestHelper helper) {
        List<Component> report = lines(40, 10);

        List<Component> pages = ReportBook.pages(report);

        helper.assertTrue(pages.size() > 1, "Forty lines don't fit on one page, got " + pages.size());
        String all = String.join("\n", pages.stream().map(Component::getString).toList());
        int last = -1;
        for (int i = 0; i < 40; i++) {
            int at = all.indexOf("line " + i + " ");
            helper.assertTrue(at > last, "Line " + i + " is there, after the one before it");
            last = at;
        }
        for (Component page : pages) helper.assertTrue(ReportBook.rows(page.getString()) <= ReportBook.ROWS_PER_PAGE, "No page holds more rows than fit: " + ReportBook.rows(page.getString()));
        helper.succeed();
    }

    public static void aLongLineThatWrapsTakesMoreThanOneRow(GameTestHelper helper) {
        List<Component> pages = ReportBook.pages(lines(6, 90));

        helper.assertTrue(pages.size() >= 3, "Six lines that each wrap over several rows spread over several pages, got " + pages.size());
        helper.succeed();
    }

    public static void theBookCarriesTheReportAsItsPages(GameTestHelper helper) {
        ItemStack book = ReportBook.book("Villager", lines(3, 5));

        helper.assertTrue(book.is(Items.WRITTEN_BOOK), "It is a written book");
        var content = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
        helper.assertTrue(content != null && content.pages().size() == 1, "Three short lines are one page");
        helper.assertTrue(content.pages().get(0).raw().getString().contains("line 2"), "The page holds the report");
        helper.succeed();
    }

    public static void eachHeadingStartsANewPage(GameTestHelper helper) {
        List<Component> report = List.of(ReportBook.heading("General"), Component.literal("a"), Component.literal("b"),
                ReportBook.heading("Job"), Component.literal("c"), ReportBook.heading("Breeding"), Component.literal("d"));

        List<Component> pages = ReportBook.pages(report);

        helper.assertTrue(pages.size() == 3, "Three headings are three pages, got " + pages.size());
        helper.assertTrue(pages.get(1).getString().startsWith("Job") && pages.get(1).getString().contains("c") && !pages.get(1).getString().contains("d"), "The second page is the job: " + pages.get(1).getString());
        helper.succeed();
    }

    public static void theVillagerReportHasOnePagePerTopic(GameTestHelper helper) {
        com.sheepfromheaven.minenow.gametest.GameTestHelpers.fence(helper);
        var villager = helper.spawn(net.minecraft.world.entity.EntityTypes.VILLAGER, new net.minecraft.core.BlockPos(6, 1, 6));

        helper.runAfterDelay(5, () -> {
            List<Component> pages = ReportBook.pages(IdleReport.describe(helper.getLevel(), villager));
            List<String> titles = pages.stream().map(page -> page.getString().lines().findFirst().orElse("")).toList();
            helper.assertTrue(titles.size() >= 4 && titles.subList(0, 4).equals(List.of("General", "Job", "Storage", "Breeding")), "One page per topic (a long one runs on to the next), got " + titles);
            helper.succeed();
        });
    }
}
