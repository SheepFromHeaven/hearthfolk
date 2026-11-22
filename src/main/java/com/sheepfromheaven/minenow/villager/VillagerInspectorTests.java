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
        player.setShiftKeyDown(true);

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
        player.setShiftKeyDown(true);

        helper.assertTrue(player.interactOn(unemployed, InteractionHand.MAIN_HAND, net.minecraft.world.phys.Vec3.ZERO).consumesAction(), "Clicking an unemployed villager works");
        helper.assertTrue(player.containerMenu instanceof ChestMenu, "Its inventory opens");
        player.closeContainer();
        helper.assertTrue(player.interactOn(child, InteractionHand.MAIN_HAND, net.minecraft.world.phys.Vec3.ZERO).consumesAction(), "Clicking a child works");
        helper.assertTrue(player.containerMenu instanceof ChestMenu, "A child's inventory opens");
        helper.succeed();
    }

    public static void theInspectorOpensALockedHomeChest(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        BlockPos bed = new BlockPos(4, 1, 4);
        BlockPos chest = new BlockPos(6, 1, 4);
        helper.setBlock(chest, Blocks.CHEST);
        Villager owner = GameTestHelpers.spawnWithHome(helper, bed.east(2).south(), bed);
        ServerPlayer player = playerWithInspector(helper);
        boolean[] filled = {false};

        helper.succeedWhen(() -> {
            helper.assertTrue(HomeStorage.storageOf(owner).isPresent(), "Chest not claimed yet");
            if (!filled[0]) {
                filled[0] = true;
                GameTestHelpers.fill(helper, chest, new ItemStack(Items.EMERALD, 5));
            }
            BlockPos abs = helper.absolutePos(chest);
            var event = net.neoforged.neoforge.common.CommonHooks.onRightClickBlock(player, InteractionHand.MAIN_HAND, abs,
                    new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(abs), net.minecraft.core.Direction.UP, abs, false));
            helper.assertTrue(event.isCanceled(), "The inspector takes the click");
            helper.assertTrue(player.containerMenu instanceof ChestMenu, "The locked chest opens for the inspector");
            helper.assertTrue(player.containerMenu.getSlot(0).getItem().is(Items.EMERALD), "...showing what the villager keeps in it");
        });
    }

    public static void rightClickingAJobBlockWithTheInspectorTellsWhoClaimedIt(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        helper.setBlock(COMPOSTER, Blocks.COMPOSTER);
        Villager farmer = GameTestHelpers.spawnEmployed(helper, COMPOSTER.east(), COMPOSTER, VillagerProfession.FARMER, PoiTypes.FARMER);
        farmer.setCustomName(net.minecraft.network.chat.Component.literal("Jim"));
        farmer.getInventory().addItem(new ItemStack(Items.EMERALD, 4));

        helper.runAfterDelay(5, () -> {
            var lines = VillagerInspector.claimReport(helper.getLevel(), helper.absolutePos(COMPOSTER)).map(list -> list.stream().map(net.minecraft.network.chat.Component::getString).toList()).orElse(java.util.List.of());
            String all = String.join("\n", lines);
            helper.assertTrue(all.contains("Jim") && all.contains("farmer") && all.contains("level"), "It names the claimant, its trade and level, got " + all);
            helper.assertTrue(all.contains("Position:"), "It says where the villager is, got " + all);
            helper.assertTrue(all.contains("Emeralds: 4"), "It says what the villager holds, got " + all);

            ServerPlayer player = playerWithInspector(helper);
            BlockPos abs = helper.absolutePos(COMPOSTER);
            var event = net.neoforged.neoforge.common.CommonHooks.onRightClickBlock(player, InteractionHand.MAIN_HAND, abs,
                    new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(abs), net.minecraft.core.Direction.UP, abs, false));
            helper.assertTrue(event.isCanceled(), "The inspector takes the click on a claimed job block");
            helper.succeed();
        });
    }

    public static void anUnclaimedJobBlockSaysSo(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        helper.setBlock(COMPOSTER, Blocks.COMPOSTER);

        helper.runAfterDelay(5, () -> {
            var lines = VillagerInspector.claimReport(helper.getLevel(), helper.absolutePos(COMPOSTER)).map(list -> list.stream().map(net.minecraft.network.chat.Component::getString).toList()).orElse(java.util.List.of());
            helper.assertTrue(lines.size() == 1 && lines.get(0).contains("Nobody"), "An unclaimed job block says nobody claimed it, got " + lines);
            helper.assertTrue(VillagerInspector.claimReport(helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 2))).isEmpty(), "An ordinary block has nothing to report");
            helper.succeed();
        });
    }

    private static net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock click(ServerPlayer player, BlockPos abs, InteractionHand hand) {
        return net.neoforged.neoforge.common.CommonHooks.onRightClickBlock(player, hand, abs,
                new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(abs), net.minecraft.core.Direction.UP, abs, false));
    }

    public static void theInspectorNeverUsesABedWhicheverHalfItClicks(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        BlockPos head = new BlockPos(6, 1, 6);
        Villager owner = GameTestHelpers.spawnWithHome(helper, head.east(2), head);
        ServerPlayer player = playerWithInspector(helper);

        helper.runAfterDelay(5, () -> {
            for (BlockPos half : new BlockPos[] {head, head.south()}) {
                helper.assertTrue(click(player, helper.absolutePos(half), InteractionHand.MAIN_HAND).isCanceled(), "The inspector never lets a bed be used, clicking " + half);
            }
            var foot = VillagerInspector.claimReport(helper.getLevel(), helper.absolutePos(head.south())).map(list -> list.stream().map(net.minecraft.network.chat.Component::getString).toList()).orElse(java.util.List.of());
            helper.assertTrue(String.join(" ", foot).contains("Bed claimed by"), "The foot of the bed reports the villager who claimed the bed, got " + foot);
            helper.assertTrue(owner.getBrain().hasMemoryValue(net.minecraft.world.entity.ai.memory.MemoryModuleType.HOME), "The villager still has its bed");
            helper.succeed();
        });
    }

    public static void theInspectorInTheOffHandNeverUsesABedEither(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        BlockPos head = new BlockPos(6, 1, 6);
        GameTestHelpers.placeBed(helper, head);
        @SuppressWarnings("removal")
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(VillagerInspector.ITEM.get()));

        helper.runAfterDelay(5, () -> {
            helper.assertTrue(click(player, helper.absolutePos(head.south()), InteractionHand.MAIN_HAND).isCanceled(), "An inspector in the off hand keeps a bed from being used");
            helper.assertFalse(click(player, helper.absolutePos(new BlockPos(2, 1, 2)), InteractionHand.MAIN_HAND).isCanceled(), "...but leaves other clicks alone");
            helper.succeed();
        });
    }

    public static void aBellWithTheInspectorListsTheVillagersOfItsVillage(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        BlockPos bell = new BlockPos(12, 1, 12);
        Villager jim = GameTestHelpers.spawnWithHome(helper, new BlockPos(6, 1, 6), new BlockPos(4, 1, 4));
        Villager anna = GameTestHelpers.spawnWithHome(helper, new BlockPos(18, 1, 18), new BlockPos(20, 1, 19));
        jim.setCustomName(net.minecraft.network.chat.Component.literal("Jim"));
        anna.setCustomName(net.minecraft.network.chat.Component.literal("Anna"));
        VillageTests.claimBell(helper, jim, bell);
        VillageTests.claimBell(helper, anna, bell);
        ServerPlayer player = playerWithInspector(helper);

        helper.succeedWhen(() -> {
            var lines = VillagerInspector.villageReport(helper.getLevel(), helper.absolutePos(bell)).map(list -> list.stream().map(net.minecraft.network.chat.Component::getString).toList()).orElse(java.util.List.of());
            String all = String.join("\n", lines);
            helper.assertTrue(all.contains("2 villagers") && all.contains("Jim") && all.contains("Anna"), "It names the village's villagers, got " + all);
            helper.assertTrue(click(player, helper.absolutePos(bell), InteractionHand.MAIN_HAND).isCanceled(), "The inspector never rings the bell");
        });
    }

    public static void aBellReportsFreeBedsAndJobBlocksAndItsBorderShowsWhereTheyWouldWidenIt(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        BlockPos bell = new BlockPos(4, 1, 12);
        Villager jim = GameTestHelpers.spawnWithHome(helper, new BlockPos(6, 1, 6), new BlockPos(4, 1, 4));
        VillageTests.claimBell(helper, jim, bell);
        BlockPos farBed = new BlockPos(50, 1, 12);
        GameTestHelpers.placeBed(helper, farBed);
        BlockPos loom = new BlockPos(52, 1, 18);
        helper.setBlock(loom, Blocks.LOOM);
        BlockPos tooFar = new BlockPos(70, 1, 12);
        helper.setBlock(tooFar, Blocks.COMPOSTER);

        helper.runAfterDelay(5, () -> {
            var lines = VillagerInspector.villageReport(helper.getLevel(), helper.absolutePos(bell)).map(list -> list.stream().map(net.minecraft.network.chat.Component::getString).toList()).orElse(java.util.List.of());
            String all = String.join("\n", lines);
            helper.assertTrue(all.contains("Free beds: 1"), "It counts the free bed outside the border, got " + all);
            helper.assertTrue(all.contains("Loom") && all.contains("outside the border"), "It lists the free job block outside the border, got " + all);
            helper.assertFalse(all.contains("Composter"), "A job block more than 48 blocks past the border is not listed, got " + all);
            var claimable = VillageBorders.claimableAround(helper.getLevel(), helper.absolutePos(bell));
            helper.assertTrue(claimable.contains(helper.absolutePos(loom)) && !claimable.contains(helper.absolutePos(tooFar)), "The border would widen towards the loom only, got " + claimable);
            helper.succeed();
        });
    }

    public static void leftClickingABellWithTheInspectorTogglesTheVillageBorders(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        BlockPos bell = new BlockPos(12, 1, 12);
        Villager jim = GameTestHelpers.spawnWithHome(helper, new BlockPos(6, 1, 6), new BlockPos(4, 1, 4));
        VillageTests.claimBell(helper, jim, bell);
        ServerPlayer player = playerWithInspector(helper);
        BlockPos abs = helper.absolutePos(bell);

        helper.succeedWhen(() -> {
            helper.assertFalse(VillageBorders.isShowing(player), "No borders to begin with");
            var first = leftClick(player, abs);
            helper.assertTrue(first.isCanceled(), "The click never breaks or rings the bell");
            helper.assertTrue(VillageBorders.isShowing(player), "A left click turns the borders on");
            helper.assertTrue(VillageBorders.anchorsAround(helper.getLevel(), abs).contains(helper.absolutePos(new BlockPos(4, 1, 4))), "...of the village the bell belongs to, with its bed among the places");
            leftClick(player, abs);
            helper.assertFalse(VillageBorders.isShowing(player), "A second left click turns them off again");
        });
    }

    public static void leftClickingABellNobodyClaimedShowsNoBorders(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        BlockPos bell = new BlockPos(12, 1, 12);
        helper.setBlock(bell, Blocks.BELL);
        ServerPlayer player = playerWithInspector(helper);

        helper.runAfterDelay(5, () -> {
            var event = leftClick(player, helper.absolutePos(bell));
            helper.assertTrue(event.isCanceled(), "The inspector still never hits the bell");
            helper.assertFalse(VillageBorders.isShowing(player), "A bell that belongs to no village has no border to show");
            helper.succeed();
        });
    }

    public static void leftClickingABellWithoutTheInspectorChangesNothing(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        BlockPos bell = new BlockPos(12, 1, 12);
        helper.setBlock(bell, Blocks.BELL);
        @SuppressWarnings("removal")
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        helper.runAfterDelay(5, () -> {
            helper.assertFalse(leftClick(player, helper.absolutePos(bell)).isCanceled(), "Any other hand leaves the bell alone");
            helper.assertFalse(VillageBorders.isShowing(player), "and shows no borders");
            helper.succeed();
        });
    }

    private static net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.LeftClickBlock leftClick(ServerPlayer player, BlockPos abs) {
        return net.neoforged.neoforge.common.CommonHooks.onLeftClickBlock(player, abs, net.minecraft.core.Direction.UP,
                net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK);
    }

    public static void aBellNobodyClaimedBelongsToNoVillage(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        BlockPos bell = new BlockPos(12, 1, 12);
        helper.setBlock(bell, Blocks.BELL);

        helper.runAfterDelay(5, () -> {
            var lines = VillagerInspector.villageReport(helper.getLevel(), helper.absolutePos(bell)).map(list -> list.stream().map(net.minecraft.network.chat.Component::getString).toList()).orElse(java.util.List.of());
            helper.assertTrue(lines.size() == 1 && lines.get(0).contains("no village"), "A bell no villager claimed says so, got " + lines);
            helper.succeed();
        });
    }

    public static void aPlainClickShowsTheReportBookInsteadOfOpeningAMenu(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(6, 1, 6));
        ServerPlayer player = playerWithInspector(helper);

        InteractionResult result = player.interactOn(villager, InteractionHand.MAIN_HAND, villager.position());

        helper.assertTrue(result.consumesAction(), "The click is used up");
        helper.assertFalse(player.containerMenu instanceof ChestMenu, "A plain click opens no inventory, the report goes to the book screen");
        helper.assertTrue(player.getMainHandItem().is(VillagerInspector.ITEM.get()), "The server still holds the inspector, never the book");
        helper.succeed();
    }
}
