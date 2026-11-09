package com.sheepfromheaven.minenow.villager;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.util.List;

public final class IdleCueTests {
    private static final BlockPos COMPOSTER = new BlockPos(11, 1, 12);
    private static final BlockPos SMOKER = new BlockPos(11, 1, 14);
    private static final BlockPos BED = new BlockPos(6, 1, 18);
    private static final BlockPos HOME_CHEST = new BlockPos(5, 1, 20);

    private IdleCueTests() {}

    private static Villager farmer(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(COMPOSTER, Blocks.COMPOSTER);
        return GameTestHelpers.spawnEmployed(helper, COMPOSTER.east(), COMPOSTER, VillagerProfession.FARMER, PoiTypes.FARMER);
    }

    public static void aWorkerWithoutItsToolIsDiagnosed(GameTestHelper helper) {
        Villager farmer = farmer(helper);

        helper.assertTrue(IdleReason.of(helper.getLevel(), farmer).contains(IdleReason.NO_TOOL), "A farmer without a hoe should be missing its tool");
        helper.assertTrue(IdleCues.show(helper.getLevel(), farmer) == null, "A missing tool shows through SeekTool, not a particle");
        farmer.getInventory().addItem(new ItemStack(Items.IRON_HOE));
        helper.assertTrue(!IdleReason.of(helper.getLevel(), farmer).contains(IdleReason.NO_TOOL), "A farmer holding a hoe is not missing its tool");
        helper.succeed();
    }

    public static void aWorkerWithoutItsToolHoldsUpAnEmerald(GameTestHelper helper) {
        Villager farmer = farmer(helper);
        farmer.getInventory().addItem(new ItemStack(Items.EMERALD, 6));
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(farmer.getMainHandItem().is(Items.EMERALD), "A farmer that can pay for a hoe should hold up an emerald, holds " + farmer.getMainHandItem());
            helper.assertTrue(farmer.getInventory().countItem(Items.EMERALD) == 6, "The emerald in hand is only a show, its purse stays whole");
            farmer.getInventory().addItem(new ItemStack(Items.IRON_HOE));
        });
        helper.runAfterDelay(120, () -> {
            helper.assertTrue(!farmer.getMainHandItem().is(Items.EMERALD), "Once it has a hoe it puts the emerald away");
            helper.succeed();
        });
    }

    public static void aBrokeWorkerWithoutItsToolHoldsNoEmerald(GameTestHelper helper) {
        Villager farmer = farmer(helper);
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(!farmer.getMainHandItem().is(Items.EMERALD), "A farmer with no money to pay for a hoe shouldn't promise one");
            helper.succeed();
        });
    }

    public static void aWorkerWithoutItsToolSeeksOutANearbyPlayer(GameTestHelper helper) {
        Villager farmer = farmer(helper);
        farmer.getInventory().addItem(new ItemStack(Items.EMERALD, 6));
        @SuppressWarnings("removal")
        net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos at = helper.absolutePos(new BlockPos(4, 1, 4));
        int[] cheers = {0};
        helper.onEachTick(() -> {
            player.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            if (SeekTool.cheeredAt(farmer) == helper.getLevel().getGameTime()) cheers[0]++;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(farmer.distanceTo(player) <= 3.0, "The farmer should walk up to the player, " + farmer.distanceTo(player) + " away");
            helper.assertTrue(cheers[0] > 0, "The farmer should make excited noises at the player");
        });
    }

    public static void aSeekingWorkerPicksUpAToolThrownToIt(GameTestHelper helper) {
        Villager farmer = farmer(helper);
        farmer.getInventory().addItem(new ItemStack(Items.EMERALD, 6));
        @SuppressWarnings("removal")
        net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos at = helper.absolutePos(new BlockPos(4, 1, 4));
        helper.onEachTick(() -> player.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5));
        helper.runAfterDelay(100, () -> {
            BlockPos drop = helper.absolutePos(new BlockPos(6, 1, 4));
            helper.getLevel().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(helper.getLevel(), drop.getX() + 0.5, drop.getY(), drop.getZ() + 0.5, new ItemStack(Items.IRON_HOE)));
        });
        helper.succeedWhen(() -> helper.assertTrue(farmer.getInventory().countItem(Items.IRON_HOE) == 1, "The farmer should pick up the hoe thrown to it"));
    }

    public static void aWorkerWithNowhereToUnloadShakesItsHead(GameTestHelper helper) {
        Villager farmer = farmer(helper);
        farmer.getInventory().addItem(new ItemStack(Items.IRON_HOE));
        GameTestHelpers.giveHome(helper, farmer, BED);
        helper.setBlock(HOME_CHEST, Blocks.CHEST);
        ChestBlockEntity chest = (ChestBlockEntity) helper.getBlockEntity(HOME_CHEST, ChestBlockEntity.class);
        for (int i = 0; i < chest.getContainerSize(); i++) chest.setItem(i, new ItemStack(Items.STONE, 64));
        farmer.setData(ModVillagers.STORAGE, net.minecraft.core.GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(HOME_CHEST)));
        for (int i = 0; i < farmer.getInventory().getContainerSize() - 1; i++) farmer.getInventory().setItem(i + 1, new ItemStack(Items.DIRT, 64));

        List<IdleReason> reasons = IdleReason.of(helper.getLevel(), farmer);
        helper.assertTrue(reasons.contains(IdleReason.STORAGE_FULL), "A full villager with a full chest can't unload: " + reasons);
        helper.assertTrue(IdleCues.show(helper.getLevel(), farmer) == IdleReason.STORAGE_FULL, "A full chest should show as the head shake");
        helper.assertTrue(farmer.getUnhappyCounter() > 0, "The villager should be shaking its head");
        helper.succeed();
    }

    public static void aVillagerWithRoomToUnloadShowsNoCue(GameTestHelper helper) {
        Villager farmer = farmer(helper);
        farmer.getInventory().addItem(new ItemStack(Items.IRON_HOE));
        helper.assertTrue(IdleReason.of(helper.getLevel(), farmer).isEmpty(), "Nothing is wrong with this farmer: " + IdleReason.of(helper.getLevel(), farmer));
        helper.assertTrue(IdleCues.show(helper.getLevel(), farmer) == null, "No cue when nothing is wrong");
        helper.assertTrue(farmer.getUnhappyCounter() == 0, "No head shake when nothing is wrong");
        helper.succeed();
    }

    public static void everyProfessionIsDiagnosedNotJustTheFarmer(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(SMOKER, Blocks.SMOKER);
        Villager butcher = GameTestHelpers.spawnEmployed(helper, SMOKER.east(), SMOKER, VillagerProfession.BUTCHER, PoiTypes.BUTCHER);

        helper.assertTrue(IdleReason.of(helper.getLevel(), butcher).contains(IdleReason.NO_TOOL), "A butcher without an axe should be missing its tool");
        String report = String.join("\n", IdleReport.describe(helper.getLevel(), butcher).stream().map(c -> c.getString()).toList());
        helper.assertTrue(report.contains("butcher"), "The report should name the profession:\n" + report);
        helper.assertTrue(report.contains("tool"), "The report should say the tool is missing:\n" + report);
        helper.succeed();
    }

    public static void aVillagerOutsideWorkHoursShowsNoCue(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.LATE_AFTERNOON);
        GameTestHelpers.fence(helper);
        helper.setBlock(COMPOSTER, Blocks.COMPOSTER);
        Villager farmer = GameTestHelpers.spawnEmployed(helper, COMPOSTER.east(), COMPOSTER, VillagerProfession.FARMER, PoiTypes.FARMER);
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(!IdleCues.isWorking(farmer), "A farmer in the late afternoon is not at work, so it shows no cue");
            helper.succeed();
        });
    }

    public static void aVillagerAtWorkIsDueACue(GameTestHelper helper) {
        Villager farmer = farmer(helper);
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(IdleCues.isWorking(farmer), "A farmer in work hours is at work");
            helper.succeed();
        });
    }
}
