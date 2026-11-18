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

    private static Villager farmerWithoutAField(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(COMPOSTER, Blocks.COMPOSTER);
        return GameTestHelpers.spawnEmployed(helper, COMPOSTER.east(), COMPOSTER, VillagerProfession.FARMER, PoiTypes.FARMER);
    }

    private static Villager farmer(GameTestHelper helper) {
        Villager farmer = farmerWithoutAField(helper);
        helper.setBlock(COMPOSTER.north().below(), Blocks.FARMLAND);
        return farmer;
    }

    public static void aWorkerWithoutItsToolIsDiagnosed(GameTestHelper helper) {
        Villager farmer = farmer(helper);

        helper.assertTrue(IdleReason.of(helper.getLevel(), farmer).contains(IdleReason.NO_TOOL), "A farmer without a hoe should be missing its tool");
        helper.assertTrue(IdleCues.show(helper.getLevel(), farmer) == null, "A missing tool shows through SeekGoods, not a particle");
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
        farmer.getInventory().addItem(new ItemStack(Items.WHEAT_SEEDS, 16));
        @SuppressWarnings("removal")
        net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos at = helper.absolutePos(new BlockPos(4, 1, 4));
        int[] cheers = {0};
        helper.onEachTick(() -> {
            player.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            if (SeekGoods.cheeredAt(farmer) == helper.getLevel().getGameTime()) cheers[0]++;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(farmer.distanceTo(player) <= 3.0, "The farmer should walk up to the player, " + farmer.distanceTo(player) + " away");
            helper.assertTrue(cheers[0] > 0, "The farmer should make excited noises at the player");
        });
    }

    public static void aSeekingWorkerPicksUpAToolThrownToIt(GameTestHelper helper) {
        Villager farmer = farmer(helper);
        farmer.getInventory().addItem(new ItemStack(Items.EMERALD, 6));
        farmer.getInventory().addItem(new ItemStack(Items.WHEAT_SEEDS, 16));
        farmer.getInventory().addItem(new ItemStack(Items.POTATO, net.minecraft.world.entity.npc.villager.Villager.BREEDING_FOOD_THRESHOLD));
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
        spawnSurplusPigs(helper);

        helper.assertTrue(IdleReason.of(helper.getLevel(), butcher).contains(IdleReason.NO_TOOL), "A butcher without an axe, with animals to cull, should be missing its tool");
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

    private static void spawnSurplusPigs(GameTestHelper helper) {
        for (int i = 0; i < 6; i++) helper.spawn(net.minecraft.world.entity.EntityTypes.PIG, new BlockPos(9 + i % 3, 1, 14 + i / 3));
    }

    private static Villager butcherWithoutLivestock(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(SMOKER, Blocks.SMOKER);
        Villager butcher = GameTestHelpers.spawnEmployed(helper, SMOKER.east(), SMOKER, VillagerProfession.BUTCHER, PoiTypes.BUTCHER);
        butcher.getInventory().addItem(new ItemStack(Items.IRON_AXE));
        return butcher;
    }

    private static Villager butcher(GameTestHelper helper) {
        Villager butcher = butcherWithoutLivestock(helper);
        butcher.getInventory().addItem(new ItemStack(Items.BEEF, 2));
        helper.spawn(net.minecraft.world.entity.EntityTypes.COW, new BlockPos(9, 1, 14));
        return butcher;
    }

    public static void aWorkerOutOfFuelNobodySellsItIsDiagnosed(GameTestHelper helper) {
        Villager butcher = butcher(helper);
        butcher.getInventory().addItem(new ItemStack(Items.EMERALD, 20));

        List<IdleReason> reasons = IdleReason.of(helper.getLevel(), butcher);
        helper.assertTrue(reasons.contains(IdleReason.NO_MATERIALS), "A butcher with no fuel and nobody selling it is out of materials: " + reasons);
        helper.assertTrue(!reasons.contains(IdleReason.NO_MONEY), "It could pay a player for fuel: " + reasons);
        helper.assertTrue(IdleCues.show(helper.getLevel(), butcher) == null, "Missing materials show through SeekGoods, not a head shake");
        butcher.getInventory().addItem(new ItemStack(Items.COAL, 8));
        helper.assertTrue(!IdleReason.of(helper.getLevel(), butcher).contains(IdleReason.NO_MATERIALS), "A butcher holding fuel is not out of materials");
        helper.succeed();
    }

    public static void aBrokeWorkerOutOfFuelShakesItsHead(GameTestHelper helper) {
        Villager butcher = butcher(helper);

        List<IdleReason> reasons = IdleReason.of(helper.getLevel(), butcher);
        helper.assertTrue(reasons.contains(IdleReason.NO_MONEY), "A butcher that can't pay a player for fuel is broke: " + reasons);
        helper.assertTrue(IdleCues.show(helper.getLevel(), butcher) == IdleReason.NO_MONEY, "A broke worker shows the head shake");
        helper.assertTrue(butcher.getUnhappyCounter() > 0, "The villager should be shaking its head");
        helper.succeed();
    }

    public static void aWorkerWithoutItsToolDoesNotAlsoShakeForMoney(GameTestHelper helper) {
        Villager butcher = butcher(helper);
        butcher.getInventory().removeAllItems();
        spawnSurplusPigs(helper);
        helper.assertTrue(IdleReason.of(helper.getLevel(), butcher).contains(IdleReason.NO_TOOL), "The butcher has no axe and animals to cull");
        helper.assertTrue(!IdleReason.of(helper.getLevel(), butcher).contains(IdleReason.NO_MONEY), "Its tool comes first, SeekGoods handles it");
        helper.assertTrue(IdleCues.show(helper.getLevel(), butcher) == null, "No head shake while it seeks a tool");
        helper.succeed();
    }

    public static void aWorkerOutOfFuelHoldsUpAnEmeraldAfterWaiting(GameTestHelper helper) {
        Villager butcher = butcher(helper);
        butcher.getInventory().addItem(new ItemStack(Items.EMERALD, 20));
        helper.runAfterDelay(100, () -> helper.assertTrue(!butcher.getMainHandItem().is(Items.EMERALD),
                "Villagers get time to buy the fuel from each other before it asks a player"));
        helper.runAfterDelay(SeekGoods.PATIENCE + 100, () -> {
            helper.assertTrue(butcher.getMainHandItem().is(Items.EMERALD), "A butcher that can pay for fuel should hold up an emerald, holds " + butcher.getMainHandItem());
            butcher.getInventory().addItem(new ItemStack(Items.COAL, 8));
        });
        helper.runAfterDelay(SeekGoods.PATIENCE + 200, () -> {
            helper.assertTrue(!butcher.getMainHandItem().is(Items.EMERALD), "Once it has fuel it puts the emerald away");
            helper.succeed();
        });
    }

    public static void aWorkerOutOfFuelSeeksOutANearbyPlayer(GameTestHelper helper) {
        Villager butcher = butcher(helper);
        butcher.getInventory().addItem(new ItemStack(Items.EMERALD, 20));
        @SuppressWarnings("removal")
        net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos at = helper.absolutePos(new BlockPos(4, 1, 4));
        helper.onEachTick(() -> player.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5));
        helper.succeedWhen(() -> helper.assertTrue(butcher.distanceTo(player) <= 3.0, "The butcher should walk up to the player, " + butcher.distanceTo(player) + " away"));
    }

    public static void aVillagerWithoutAJobSiteShakesItsHeadEvenOffDuty(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.LATE_AFTERNOON);
        GameTestHelpers.fence(helper);
        helper.setBlock(COMPOSTER, Blocks.COMPOSTER);
        Villager farmer = GameTestHelpers.spawnEmployed(helper, COMPOSTER.east(), COMPOSTER, VillagerProfession.FARMER, PoiTypes.FARMER);
        farmer.getInventory().addItem(new ItemStack(Items.IRON_HOE));
        farmer.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE);

        helper.assertTrue(IdleCues.show(helper.getLevel(), farmer) == IdleReason.NO_JOB_SITE, "A villager that lost its workstation should shake its head");
        helper.assertTrue(farmer.getUnhappyCounter() > 0, "The villager should be shaking its head");
        helper.succeed();
    }

    private static boolean noWorkArea(GameTestHelper helper, Villager villager) {
        return IdleReason.of(helper.getLevel(), villager).contains(IdleReason.NO_WORK_AREA);
    }

    public static void aFarmerWithNoFieldHasNoWorkArea(GameTestHelper helper) {
        Villager farmer = farmerWithoutAField(helper);
        farmer.getInventory().addItem(new ItemStack(Items.IRON_HOE));
        helper.assertTrue(noWorkArea(helper, farmer), "A farmer whose composter has no ground beside it has no field");
        helper.setBlock(COMPOSTER.north(), Blocks.AIR);
        helper.setBlock(COMPOSTER.north().below(), Blocks.FARMLAND);
        helper.assertTrue(!noWorkArea(helper, farmer), "Farmland by the composter is a field: " + IdleReason.of(helper.getLevel(), farmer));
        helper.succeed();
    }

    public static void aWorkerWithNoWorkAreaShakesItsHead(GameTestHelper helper) {
        Villager farmer = farmerWithoutAField(helper);
        farmer.getInventory().addItem(new ItemStack(Items.IRON_HOE));
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(IdleCues.show(helper.getLevel(), farmer) == IdleReason.NO_WORK_AREA, "A farmer at work with no field shakes its head");
            helper.assertTrue(farmer.getUnhappyCounter() > 0, "The villager should be shaking its head");
            helper.succeed();
        });
    }

    public static void aWorkerWithoutItsToolIsNotBlamedForNoWorkArea(GameTestHelper helper) {
        Villager farmer = farmerWithoutAField(helper);
        helper.assertTrue(!noWorkArea(helper, farmer), "Its tool comes first, SeekGoods handles it");
        helper.succeed();
    }

    public static void aFishermanWithNoWaterHasNoWorkArea(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        BlockPos barrel = new BlockPos(6, 1, 12);
        helper.setBlock(barrel, Blocks.BARREL);
        Villager fisherman = GameTestHelpers.spawnEmployed(helper, barrel.east(), barrel, VillagerProfession.FISHERMAN, PoiTypes.FISHERMAN);
        fisherman.getInventory().addItem(new ItemStack(Items.FISHING_ROD));
        helper.assertTrue(noWorkArea(helper, fisherman), "A fisherman with no water near its barrel has nowhere to fish");
        for (int x = 9; x <= 11; x++) {
            for (int z = 11; z <= 13; z++) helper.setBlock(x, 0, z, Blocks.WATER);
        }
        helper.assertTrue(!noWorkArea(helper, fisherman), "Water with a shore is somewhere to fish: " + IdleReason.of(helper.getLevel(), fisherman));
        helper.succeed();
    }

    public static void aShepherdWithNoSheepHasNoWorkArea(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        BlockPos loom = new BlockPos(6, 1, 12);
        helper.setBlock(loom, Blocks.LOOM);
        Villager shepherd = GameTestHelpers.spawnEmployed(helper, loom.east(), loom, VillagerProfession.SHEPHERD, PoiTypes.SHEPHERD);
        shepherd.getInventory().addItem(new ItemStack(Items.SHEARS));
        helper.assertTrue(noWorkArea(helper, shepherd), "A shepherd with no sheep by its loom has no flock");
        helper.spawn(net.minecraft.world.entity.EntityTypes.SHEEP, new BlockPos(9, 1, 12));
        helper.assertTrue(!noWorkArea(helper, shepherd), "A sheep by the loom is a flock: " + IdleReason.of(helper.getLevel(), shepherd));
        helper.succeed();
    }

    public static void aButcherWithNoLivestockHasNoWorkArea(GameTestHelper helper) {
        Villager butcher = butcherWithoutLivestock(helper);
        helper.assertTrue(noWorkArea(helper, butcher), "A butcher with no animals by its smoker has no herd");
        helper.spawn(net.minecraft.world.entity.EntityTypes.COW, new BlockPos(9, 1, 14));
        helper.assertTrue(!noWorkArea(helper, butcher), "A cow by the smoker is a herd: " + IdleReason.of(helper.getLevel(), butcher));
        helper.succeed();
    }

    public static void aMinerWithNoRimHasNoWorkArea(GameTestHelper helper) {
        Villager miner = com.sheepfromheaven.minenow.miner.MinerTests.spawnMiner(helper);
        miner.getInventory().addItem(new ItemStack(Items.WOODEN_PICKAXE));
        helper.assertTrue(noWorkArea(helper, miner), "A miner with no rim around its cart has no mine to dig");
        com.sheepfromheaven.minenow.miner.MinerTests.quarry(helper);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(!noWorkArea(helper, miner), "A closed rim marks a mine: " + IdleReason.of(helper.getLevel(), miner));
            helper.succeed();
        });
    }
}
