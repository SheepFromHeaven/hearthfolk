package com.sheepfromheaven.minenow.villager;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.util.ArrayList;
import java.util.List;

public final class NeedBubbleTests {
    private static final BlockPos COMPOSTER = new BlockPos(11, 1, 12);
    private static final BlockPos SMOKER = new BlockPos(11, 1, 14);
    private static final BlockPos BED = new BlockPos(6, 1, 18);
    private static final BlockPos HOME_CHEST = new BlockPos(5, 1, 20);

    private NeedBubbleTests() {}

    private static Villager farmer(GameTestHelper helper, boolean field) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(COMPOSTER, Blocks.COMPOSTER);
        Villager farmer = GameTestHelpers.spawnEmployed(helper, COMPOSTER.east(), COMPOSTER, VillagerProfession.FARMER, PoiTypes.FARMER);
        if (field) helper.setBlock(COMPOSTER.north().below(), Blocks.FARMLAND);
        return farmer;
    }

    private static Villager butcher(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(SMOKER, Blocks.SMOKER);
        Villager butcher = GameTestHelpers.spawnEmployed(helper, SMOKER.east(), SMOKER, VillagerProfession.BUTCHER, PoiTypes.BUTCHER);
        butcher.getInventory().addItem(new ItemStack(Items.IRON_AXE));
        butcher.getInventory().addItem(new ItemStack(Items.BEEF, 2));
        helper.spawn(net.minecraft.world.entity.EntityTypes.COW, new BlockPos(9, 1, 14));
        return butcher;
    }

    private static void shows(GameTestHelper helper, Villager villager, Item expected) {
        ItemStack icon = NeedBubbles.iconFor(helper.getLevel(), villager);
        helper.assertTrue(icon.is(expected), "Expected a " + expected + " bubble, got " + icon + " for " + IdleReason.of(helper.getLevel(), villager));
    }

    public static void aWorkerWithoutItsToolShowsItsCheapestTool(GameTestHelper helper) {
        Villager farmer = farmer(helper, true);
        helper.assertTrue(IdleReason.of(helper.getLevel(), farmer).getFirst() == IdleReason.NO_TOOL, "A farmer without a hoe lacks its tool");
        shows(helper, farmer, TradeTable.forVillager(farmer).orElseThrow().tools().getFirst().item());
        helper.assertTrue(NeedBubbles.TOOL.equals(NeedBubbles.bubbleFor(helper.getLevel(), farmer).image()), "A missing tool is shown as the crossed pickaxe and axe with an exclamation mark");
        helper.succeed();
    }

    public static void aWorkerOutOfFuelShowsTheFuel(GameTestHelper helper) {
        Villager butcher = butcher(helper);
        butcher.getInventory().addItem(new ItemStack(Items.EMERALD, 20));
        helper.assertTrue(IdleReason.of(helper.getLevel(), butcher).getFirst() == IdleReason.NO_MATERIALS, "A butcher without fuel is out of materials");
        shows(helper, butcher, Shortage.fuel(helper.getLevel(), butcher).orElseThrow().item());
        helper.assertTrue(NeedBubbles.FUEL.equals(NeedBubbles.bubbleFor(helper.getLevel(), butcher).image()), "Missing fuel is shown as coal with an exclamation mark");
        helper.succeed();
    }

    public static void aBrokeWorkerShowsAnEmerald(GameTestHelper helper) {
        Villager butcher = butcher(helper);
        helper.assertTrue(IdleReason.of(helper.getLevel(), butcher).contains(IdleReason.NO_MONEY), "A butcher that can't pay for fuel is broke");
        shows(helper, butcher, Items.EMERALD);
        helper.assertTrue(NeedBubbles.MONEY.equals(NeedBubbles.bubbleFor(helper.getLevel(), butcher).image()), "Missing money is shown as an emerald with an exclamation mark");
        helper.succeed();
    }

    public static void aWorkerWithNowhereToUnloadShowsAChest(GameTestHelper helper) {
        Villager farmer = farmer(helper, true);
        farmer.getInventory().addItem(new ItemStack(Items.IRON_HOE));
        GameTestHelpers.giveHome(helper, farmer, BED);
        helper.setBlock(HOME_CHEST, Blocks.CHEST);
        ChestBlockEntity chest = (ChestBlockEntity) helper.getBlockEntity(HOME_CHEST, ChestBlockEntity.class);
        for (int i = 0; i < chest.getContainerSize(); i++) chest.setItem(i, new ItemStack(Items.STONE, 64));
        farmer.setData(ModVillagers.STORAGE, net.minecraft.core.GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(HOME_CHEST)));
        for (int i = 0; i < farmer.getInventory().getContainerSize() - 1; i++) farmer.getInventory().setItem(i + 1, new ItemStack(Items.DIRT, 64));
        helper.assertTrue(IdleReason.of(helper.getLevel(), farmer).getFirst() == IdleReason.STORAGE_FULL, "A full villager with a full chest can't unload");
        shows(helper, farmer, Items.CHEST_MINECART);
        helper.assertTrue(NeedBubbles.STORAGE_FULL.equals(NeedBubbles.bubbleFor(helper.getLevel(), farmer).image()), "A full chest is shown as a chest seen from the front with an exclamation mark");
        helper.succeed();
    }

    public static void aVillagerWithoutAJobSiteShowsItsJobBlock(GameTestHelper helper) {
        Villager farmer = farmer(helper, true);
        farmer.getInventory().addItem(new ItemStack(Items.IRON_HOE));
        farmer.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);
        helper.assertTrue(IdleReason.of(helper.getLevel(), farmer).getFirst() == IdleReason.NO_JOB_SITE, "A farmer that lost its workstation has no job site");
        shows(helper, farmer, NeedBubbles.toolIcon(farmer));
        NeedBubbles.Shown block = NeedBubbles.bubbleFor(helper.getLevel(), farmer);
        helper.assertTrue(NeedBubbles.JOB_BLOCK.equals(block.image()) && block.item().is(Items.COMPOSTER), "A farmer without a job site shows the front of a composter, got " + block);
        helper.succeed();
    }

    public static void aWorkerWithNoWorkAreaShowsWhatItsWorkNeeds(GameTestHelper helper) {
        Villager farmer = farmer(helper, false);
        farmer.getInventory().addItem(new ItemStack(Items.IRON_HOE));
        helper.assertTrue(IdleReason.of(helper.getLevel(), farmer).getFirst() == IdleReason.NO_WORK_AREA, "A farmer with no field has no work area");
        shows(helper, farmer, Items.WHEAT_SEEDS);
        NeedBubbles.Shown area = NeedBubbles.bubbleFor(helper.getLevel(), farmer);
        helper.assertTrue(NeedBubbles.WORK_AREA.equals(area.image()) && area.item().is(Items.WHEAT_SEEDS), "A farmer with no field shows the picture of a field, got " + area);
        helper.succeed();
    }

    @SuppressWarnings("removal")
    public static void aShowcaseVillagerGetsNoNeedBubble(GameTestHelper helper) {
        Villager farmer = farmer(helper, true);
        net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.snapTo(farmer.getX() + 3, farmer.getY(), farmer.getZ());
        Meals.makeHungry(farmer);
        farmer.addTag(SpeechBubbles.SHOWCASE_TAG);
        helper.assertTrue(!NeedBubbles.bubbleFor(helper.getLevel(), farmer).isEmpty(), "Without the tag this farmer would show a bubble");
        List<SpeechBubbles.Payload> sent = new ArrayList<>();
        var original = SpeechBubbles.sender;
        SpeechBubbles.sender = (to, payload) -> sent.add(payload);
        try {
            helper.assertTrue(NeedBubbles.poll(helper.getLevel(), farmer).isEmpty(), "A showcase villager shows nothing from poll");
        } finally {
            SpeechBubbles.sender = original;
        }
        helper.assertTrue(sent.isEmpty(), "Nothing is sent over a showcase villager: " + sent);
        helper.succeed();
    }

    @SuppressWarnings("removal")
    public static void withoutSpeechBubblesNothingIsSent(GameTestHelper helper) {
        Villager farmer = farmer(helper, true);
        net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.snapTo(farmer.getX() + 3, farmer.getY(), farmer.getZ());
        helper.assertTrue(IdleReason.of(helper.getLevel(), farmer).contains(IdleReason.NO_TOOL), "The farmer needs a hoe");
        List<SpeechBubbles.Payload> sent = new ArrayList<>();
        var original = SpeechBubbles.sender;
        SpeechBubbles.sender = (to, payload) -> sent.add(payload);
        try {
            NeedBubbles.poll(helper.getLevel(), farmer);
        } finally {
            SpeechBubbles.sender = original;
        }
        helper.assertTrue(sent.isEmpty(), "With speech bubbles off nothing is sent: " + sent);
        helper.succeed();
    }
}
