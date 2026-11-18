package com.sheepfromheaven.minenow.villager;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

public final class VillagerInspectorTests {
    private static final BlockPos COMPOSTER = new BlockPos(6, 1, 6);

    private VillagerInspectorTests() {}

    @SuppressWarnings("removal")
    private static ServerPlayer playerWithInspector(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VillagerInspector.ITEM.get()));
        return player;
    }

    public static void rightClickingAVillagerWithTheInspectorOpensItsWholeInventory(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        helper.setBlock(COMPOSTER, Blocks.COMPOSTER);
        Villager farmer = GameTestHelpers.spawnEmployed(helper, COMPOSTER.east(), COMPOSTER, VillagerProfession.FARMER, PoiTypes.FARMER);
        farmer.getInventory().setItem(0, new ItemStack(Items.WHEAT_SEEDS, 3));
        farmer.getInventory().setItem(5, new ItemStack(Items.EMERALD, 7));
        farmer.getInventory().setItem(7, new ItemStack(Items.BREAD, 2));
        ServerPlayer player = playerWithInspector(helper);

        InteractionResult result = player.interactOn(farmer, InteractionHand.MAIN_HAND, farmer.position());

        helper.assertTrue(result.consumesAction(), "The click is used up");
        helper.assertTrue(!farmer.isTrading(), "The inspector replaces the trade screen");
        helper.assertTrue(player.containerMenu instanceof ChestMenu, "The villager's inventory opens");
        ChestMenu menu = (ChestMenu) player.containerMenu;
        for (int slot = 0; slot < farmer.getInventory().getContainerSize(); slot++) {
            helper.assertTrue(ItemStack.matches(menu.getSlot(slot).getItem(), farmer.getInventory().getItem(slot)), "Slot " + slot + " shows what the villager carries");
        }
        menu.getSlot(5).set(ItemStack.EMPTY);
        helper.assertTrue(farmer.getInventory().getItem(5).isEmpty(), "The menu is the villager's inventory itself, not a copy");
        helper.succeed();
    }

    public static void theInspectorReportsTheSameAsTheVillagerCommand(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        helper.setBlock(COMPOSTER, Blocks.COMPOSTER);
        Villager farmer = GameTestHelpers.spawnEmployed(helper, COMPOSTER.east(), COMPOSTER, VillagerProfession.FARMER, PoiTypes.FARMER);

        var report = VillagerInspector.report(helper.getLevel(), farmer).stream().map(net.minecraft.network.chat.Component::getString).toList();
        var command = IdleReport.describe(helper.getLevel(), farmer).stream().map(net.minecraft.network.chat.Component::getString).toList();

        helper.assertTrue(report.equals(command), "The report is the command's report, got " + report);
        helper.assertTrue(report.stream().anyMatch(line -> line.contains("farmer")), "It names the profession");
        helper.succeed();
    }

    public static void theInspectorWorksOnAnUnemployedVillagerAndAChild(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        Villager unemployed = helper.spawn(EntityTypes.VILLAGER, new BlockPos(4, 1, 4));
        Villager child = helper.spawn(EntityTypes.VILLAGER, new BlockPos(8, 1, 8));
        child.setAge(-24000);
        ServerPlayer player = playerWithInspector(helper);

        helper.assertTrue(player.interactOn(unemployed, InteractionHand.MAIN_HAND, net.minecraft.world.phys.Vec3.ZERO).consumesAction(), "Clicking an unemployed villager works");
        helper.assertTrue(player.containerMenu instanceof ChestMenu, "Its inventory opens");
        player.closeContainer();
        helper.assertTrue(player.interactOn(child, InteractionHand.MAIN_HAND, net.minecraft.world.phys.Vec3.ZERO).consumesAction(), "Clicking a child works");
        helper.assertTrue(player.containerMenu instanceof ChestMenu, "A child's inventory opens");
        helper.succeed();
    }
}
