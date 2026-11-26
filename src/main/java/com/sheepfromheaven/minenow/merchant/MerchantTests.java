package com.sheepfromheaven.minenow.merchant;

import com.sheepfromheaven.minenow.MineNowConfig;
import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import com.sheepfromheaven.minenow.villager.Economy;
import com.sheepfromheaven.minenow.villager.VillageTests;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.equine.Mule;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import java.util.Optional;
import java.util.List;

public final class MerchantTests {
    private static final BlockPos STATION = new BlockPos(10, 1, 12);

    private MerchantTests() {}

    public static void sneakingWithASaddleOnAHayBaleMakesAPackStation(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        helper.setBlock(STATION, Blocks.HAY_BLOCK);
        ServerPlayer player = player(helper);
        player.setShiftKeyDown(true);

        useOn(helper, player);

        helper.assertTrue(helper.getBlockState(STATION).is(Merchant.PACK_STATION.get()), "A saddle used while sneaking on a hay bale makes a pack station, got " + helper.getBlockState(STATION));
        helper.assertTrue(player.getMainHandItem().isEmpty(), "The saddle goes into it, still holding " + player.getMainHandItem());
        helper.succeed();
    }

    public static void withoutSneakingTheHayBaleStaysAsItIs(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        helper.setBlock(STATION, Blocks.HAY_BLOCK);
        ServerPlayer player = player(helper);

        useOn(helper, player);

        helper.assertTrue(helper.getBlockState(STATION).is(Blocks.HAY_BLOCK), "Without sneaking nothing happens to the hay bale, got " + helper.getBlockState(STATION));
        helper.succeed();
    }

    public static void breakingAPackStationGivesBackTheHayBaleAndTheSaddle(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        helper.setBlock(STATION, Merchant.PACK_STATION.get());

        List<ItemStack> drops = Block.getDrops(helper.getBlockState(STATION), helper.getLevel(), helper.absolutePos(STATION), null);

        helper.assertTrue(drops.stream().anyMatch(stack -> stack.is(Items.HAY_BLOCK)) && drops.stream().anyMatch(stack -> stack.is(Items.SADDLE)), "Both parts drop, got " + drops);
        helper.succeed();
    }

    public static void anUnemployedVillagerBecomesAMerchantAtAFreePackStation(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(STATION, Merchant.PACK_STATION.get());
        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(13, 1, 12));

        helper.succeedWhen(() -> helper.assertTrue(villager.getVillagerData().profession().is(Merchant.PROFESSION),
                "An unemployed villager should take a free pack station and become a merchant"));
    }

    public static void aMerchantClaimsAMuleNearItsPackStation(GameTestHelper helper) {
        Villager merchant = spawnMerchant(helper, 1);
        Mule mule = helper.spawn(EntityTypes.MULE, new BlockPos(13, 1, 12));

        helper.succeedWhen(() -> helper.assertTrue(merchant.getUUID().equals(mule.getData(Merchant.MULE_OWNER).orElse(null)),
                "The merchant should claim a mule within reach of its station, owner " + mule.getData(Merchant.MULE_OWNER)));
    }

    public static void aNoviceMerchantClaimsOnlyOneMule(GameTestHelper helper) {
        Villager merchant = spawnMerchant(helper, 1);
        Mule first = helper.spawn(EntityTypes.MULE, new BlockPos(12, 1, 12));
        Mule second = helper.spawn(EntityTypes.MULE, new BlockPos(14, 1, 12));

        helper.runAfterDelay(200, () -> {
            long claimed = java.util.stream.Stream.of(first, second).filter(mule -> merchant.getUUID().equals(mule.getData(Merchant.MULE_OWNER).orElse(null))).count();
            helper.assertTrue(claimed == 1, "A novice keeps one mule, it claimed " + claimed);
            helper.succeed();
        });
    }

    public static void aMasterMerchantClaimsFourMules(GameTestHelper helper) {
        Villager merchant = spawnMerchant(helper, 5);
        List<Mule> mules = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) mules.add(helper.spawn(EntityTypes.MULE, new BlockPos(8 + i, 1, 14)));

        helper.runAfterDelay(400, () -> {
            long claimed = mules.stream().filter(mule -> merchant.getUUID().equals(mule.getData(Merchant.MULE_OWNER).orElse(null))).count();
            helper.assertTrue(claimed == 4, "A master keeps four mules, it claimed " + claimed);
            helper.succeed();
        });
    }

    public static void aMerchantLeavesFarMulesAlone(GameTestHelper helper) {
        Villager merchant = spawnMerchant(helper, 1);
        Mule mule = helper.spawnWithNoFreeWill(EntityTypes.MULE, new BlockPos(22, 1, 22));

        helper.runAfterDelay(200, () -> {
            helper.assertTrue(mule.getData(Merchant.MULE_OWNER).isEmpty(), "A mule 12 blocks from the station isn't the merchant's, owner " + mule.getData(Merchant.MULE_OWNER) + " " + merchant.getUUID());
            helper.succeed();
        });
    }

    public static void aMerchantLeavesAPlayersMuleAlone(GameTestHelper helper) {
        spawnMerchant(helper, 1);
        Mule mule = helper.spawn(EntityTypes.MULE, new BlockPos(13, 1, 12));
        mule.tameWithName(player(helper));

        helper.runAfterDelay(200, () -> {
            helper.assertTrue(mule.getData(Merchant.MULE_OWNER).isEmpty(), "A tamed mule belongs to its player, owner " + mule.getData(Merchant.MULE_OWNER));
            helper.succeed();
        });
    }

    public static void aMerchantsMuleIsFreedWhenItDies(GameTestHelper helper) {
        Villager merchant = spawnMerchant(helper, 1);
        Mule mule = helper.spawn(EntityTypes.MULE, new BlockPos(13, 1, 12));

        helper.succeedWhen(() -> {
            if (mule.getData(Merchant.MULE_OWNER).isPresent()) {
                merchant.kill(helper.getLevel());
                throw helper.assertionException("Waiting for the merchant to claim its mule, then killing it");
            }
            helper.assertTrue(merchant.isDeadOrDying() && mule.getData(Merchant.MULE_OWNER).isEmpty(), "A dead merchant's mule is nobody's again");
        });
    }

    public static void aMerchantBringsAVillageWhatItLacks(GameTestHelper helper) {
        Villager merchant = homeVillage(helper, true);
        Villager butcher = butcherAcross(helper);

        helper.succeedWhen(() -> {
            helper.assertTrue(butcher.getInventory().countItem(Items.WHEAT) >= 16 || Economy.count(helper.getLevel(), butcher, Items.WHEAT) >= 16,
                    "The butcher across the fields should buy the wheat its village lacks from the merchant, has " + Economy.count(helper.getLevel(), butcher, Items.WHEAT)
                            + "; merchant " + merchant.getData(Merchant.TOUR) + " at " + helper.relativePos(merchant.blockPosition()).toShortString() + ", rested " + merchant.getData(Merchant.RESTED_AT)
                            + ", carries " + Economy.count(helper.getLevel(), merchant, Items.WHEAT) + " wheat, " + Economy.emeralds(helper.getLevel(), merchant) + " emeralds");
            helper.assertTrue(merchant.getData(Merchant.TOUR).isPresent() || merchant.getData(Merchant.RESTED_AT) > 0, "It went on a tour");
        });
    }

    public static void withoutEmeraldsABrokeMerchantPlansATourToAVillageThatNeedsWhatItsVillageSells(GameTestHelper helper) {
        Villager merchant = homeVillage(helper, true);
        Economy.take(helper.getLevel(), merchant, Items.EMERALD, Economy.emeralds(helper.getLevel(), merchant));
        Villager butcher = butcherAcross(helper);
        Economy.take(helper.getLevel(), butcher, Items.EMERALD, Economy.emeralds(helper.getLevel(), butcher));

        helper.succeedWhen(() -> helper.assertTrue(merchant.getData(Merchant.TOUR).filter(tour -> tour.wanted().stream().anyMatch(want -> want.item() == Items.WHEAT)).isPresent()
                        || merchant.getData(Merchant.RESTED_AT) > 0,
                "A merchant with no emeralds plans a tour carrying wheat to the broke butcher across the fields, tour " + merchant.getData(Merchant.TOUR)));
    }

    public static void aMerchantSellsAboveWhatItPaid(GameTestHelper helper) {
        Villager merchant = homeVillage(helper, true);
        Villager butcher = butcherAcross(helper);
        int purse = Economy.emeralds(helper.getLevel(), merchant);

        helper.succeedWhen(() -> {
            helper.assertTrue(Economy.count(helper.getLevel(), butcher, Items.WHEAT) >= 16, "Waiting for the butcher to buy");
            helper.assertTrue(Economy.emeralds(helper.getLevel(), butcher) == 5, "The butcher pays an emerald for 16 wheat, has " + Economy.emeralds(helper.getLevel(), butcher));
            helper.assertTrue(Economy.count(helper.getLevel(), merchant, Items.WHEAT) == 4 && Economy.emeralds(helper.getLevel(), merchant) == purse - 1 + 1,
                    "The merchant paid the farmer an emerald for 20 and got one for 16, holds " + Economy.count(helper.getLevel(), merchant, Items.WHEAT) + " wheat and "
                            + Economy.emeralds(helper.getLevel(), merchant) + " emeralds");
        });
    }

    public static void withoutEmeraldsWhatAMerchantSellsAtAMarketIsKeptForThePlayer(GameTestHelper helper) {
        Villager merchant = homeVillage(helper, true);
        Villager butcher = butcherAcross(helper);

        helper.succeedWhen(() -> {
            helper.assertTrue(Economy.count(helper.getLevel(), butcher, Items.WHEAT) >= 16, "Waiting for the butcher to buy, has " + Economy.count(helper.getLevel(), butcher, Items.WHEAT)
                    + "; merchant " + merchant.getData(Merchant.TOUR));
            helper.assertTrue(butcher.getInventory().countItem(Items.EMERALD) == 6, "The butcher pays nothing with the emerald economy off, has " + butcher.getInventory().countItem(Items.EMERALD));
            helper.assertTrue(merchant.getData(Merchant.EARNINGS) == 1, "The merchant keeps the emerald 16 wheat would have fetched for the player, earned " + merchant.getData(Merchant.EARNINGS));
        });
    }

    public static void aMerchantHomeFromItsTourGivesItsEarningsToANearbyPlayer(GameTestHelper helper) {
        Villager merchant = spawnMerchant(helper, 1);
        merchant.setData(Merchant.EARNINGS, 5);
        ServerPlayer player = player(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.snapTo(helper.absoluteVec(new Vec3(4.5, 1, 6.5)));

        helper.succeedWhen(() -> {
            int handed = player.getInventory().countItem(Items.EMERALD) + thrownEmeralds(helper);
            helper.assertTrue(handed == 5, "The merchant walks up to the player and hands over its 5 earned emeralds, handed " + handed
                    + ", merchant at " + helper.relativePos(merchant.blockPosition()).toShortString() + " still holding " + merchant.getData(Merchant.EARNINGS));
            helper.assertTrue(merchant.getData(Merchant.EARNINGS) == 0, "...and owes nothing more, holds " + merchant.getData(Merchant.EARNINGS));
        });
    }

    public static void aTouringMerchantKeepsItsEarningsUntilItIsHome(GameTestHelper helper) {
        Villager merchant = touring(helper, Tour.Phase.MARKET);
        merchant.getInventory().addItem(new ItemStack(Items.WHEAT, 20));
        merchant.setData(Merchant.EARNINGS, 5);
        ServerPlayer player = player(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.snapTo(helper.absoluteVec(new Vec3(12.5, 1, 14.5)));

        helper.runAfterDelay(200, () -> {
            helper.assertTrue(merchant.getData(Merchant.TOUR).isPresent(), "The merchant is still holding its market, tour " + merchant.getData(Merchant.TOUR));
            helper.assertTrue(merchant.getData(Merchant.EARNINGS) == 5 && thrownEmeralds(helper) == 0 && player.getInventory().countItem(Items.EMERALD) == 0,
                    "A merchant on its tour brings its earnings home before it hands them over, holds " + merchant.getData(Merchant.EARNINGS));
            helper.succeed();
        });
    }

    private static int thrownEmeralds(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(helper.absolutePos(BlockPos.ZERO)).inflate(30),
                        item -> item.getItem().is(Items.EMERALD)).stream().mapToInt(item -> item.getItem().getCount()).sum();
    }

    public static void aMerchantStaysHomeWhenNothingIsNeeded(GameTestHelper helper) {
        Villager merchant = homeVillage(helper, true);
        helper.getLevel().getEntities(EntityTypes.VILLAGER, merchant.getBoundingBox().inflate(80), v -> v.getVillagerData().profession().is(VillagerProfession.BUTCHER)).forEach(v -> v.discard());

        helper.runAfterDelay(600, () -> {
            helper.assertTrue(merchant.getData(Merchant.TOUR).isEmpty() && merchant.getData(Merchant.RESTED_AT) == 0, "With nothing to sell anyone it stays home");
            helper.succeed();
        });
    }

    public static void aMerchantStaysHomeWhenItsVillageCannotSellWhatIsNeeded(GameTestHelper helper) {
        Villager merchant = homeVillage(helper, false);
        butcherAcross(helper);

        helper.runAfterDelay(600, () -> {
            helper.assertTrue(merchant.getData(Merchant.TOUR).isEmpty(), "Nobody at home sells wheat, so there's nothing to carry");
            helper.succeed();
        });
    }

    public static void toursAreCapped(GameTestHelper helper) {
        int before = MineNowConfig.maxMerchantTours();
        MineNowConfig.setMaxMerchantTours(0);
        Villager merchant = homeVillage(helper, true);
        butcherAcross(helper);

        helper.runAfterDelay(600, () -> {
            try {
                helper.assertTrue(merchant.getData(Merchant.TOUR).isEmpty(), "With no tour slots a merchant stays home");
                helper.succeed();
            } finally {
                MineNowConfig.setMaxMerchantTours(before);
            }
        });
    }

    public static void aMerchantRestsAtHomeAfterItsTour(GameTestHelper helper) {
        Villager merchant = homeVillage(helper, true);
        butcherAcross(helper);
        boolean[] set = {false};

        helper.succeedWhen(() -> {
            if (merchant.getData(Merchant.TOUR).isPresent()) set[0] = true;
            helper.assertTrue(set[0] && merchant.getData(Merchant.TOUR).isEmpty(), "Waiting for the tour to start and end");
            helper.assertTrue(merchant.getData(Merchant.RESTED_AT) > helper.getLevel().getGameTime(), "It rests at home after a tour");
            helper.assertTrue(merchant.blockPosition().closerThan(helper.absolutePos(new BlockPos(8, 1, 12)), 8.0), "It is back home");
        });
    }

    public static void aMerchantCarriesItsGoodsOnItsMules(GameTestHelper helper) {
        Villager merchant = spawnMerchant(helper, 1);
        Mule mule = helper.spawn(EntityTypes.MULE, new BlockPos(13, 1, 12));
        mule.getSlot(499).set(new ItemStack(Items.CHEST));
        for (int i = 0; i < merchant.getInventory().getContainerSize(); i++) merchant.getInventory().setItem(i, new ItemStack(Items.DIRT, 64));

        helper.succeedWhen(() -> {
            helper.assertTrue(merchant.getUUID().equals(mule.getData(Merchant.MULE_OWNER).orElse(null)), "Waiting for the merchant to claim its mule");
            Economy.give(helper.getLevel(), merchant, new ItemStack(Items.WHEAT, 20));
            helper.assertTrue(mule.getInventory().countItem(Items.WHEAT) == 20 && Economy.count(helper.getLevel(), merchant, Items.WHEAT) == 20,
                    "Its pockets are full, so the wheat rides in the mule's chest, which counts as its goods");
        });
    }

    public static void aMerchantPutsAChestOnItsMule(GameTestHelper helper) {
        Villager merchant = spawnMerchant(helper, 1);
        merchant.getInventory().addItem(new ItemStack(Items.CHEST));
        Mule mule = helper.spawn(EntityTypes.MULE, new BlockPos(13, 1, 12));

        helper.succeedWhen(() -> helper.assertTrue(mule.hasChest() && Economy.count(helper.getLevel(), merchant, Items.CHEST) == 0, "It fits the chest it holds on its mule"));
    }

    public static void aTouringMerchantSleepsOnAStrawBedAtNightAndGetsUpAtDawn(GameTestHelper helper) {
        Villager merchant = touring(helper, Tour.Phase.TRAVEL);
        merchant.getInventory().addItem(new ItemStack(Items.STRAW_BED, 2));
        helper.setTime(14000);
        boolean[] slept = {false};
        BlockPos[] headAt = {null};

        helper.succeedWhen(() -> {
            if (!slept[0]) {
                helper.assertTrue(merchant.isSleeping(), "A merchant on the road lies down at night on a straw bed it carries");
                helper.assertTrue(Economy.count(helper.getLevel(), merchant, Items.STRAW_BED) == 1, "One bed is down, one is left");
                helper.assertTrue(merchant.getBrain().getMemory(MemoryModuleType.HOME).isEmpty(), "The bed in a meadow is not its home");
                slept[0] = true;
                headAt[0] = merchant.getData(Merchant.CAMP).orElseThrow();
                helper.setTime(1000);
            }
            helper.assertTrue(!merchant.isSleeping() && merchant.getData(Merchant.CAMP).isEmpty(), "At dawn it gets up");
            helper.assertTrue(helper.getLevel().getBlockState(headAt[0]).isAir(), "Its bed falls apart, as vanilla's straw bed does");
        });
    }

    public static void aMerchantOnTheRoadWithNoBedStandsThroughTheNight(GameTestHelper helper) {
        Villager merchant = touring(helper, Tour.Phase.TRAVEL);
        helper.setTime(14000);

        helper.runAfterDelay(200, () -> {
            helper.assertFalse(merchant.isSleeping(), "Without a bed it doesn't lie down");
            helper.succeed();
        });
    }

    public static void aTouringMerchantDrinksInvisibilityAtNight(GameTestHelper helper) {
        Villager merchant = touring(helper, Tour.Phase.TRAVEL);
        merchant.getInventory().addItem(PotionContents.createItemStack(Items.POTION, Potions.INVISIBILITY));
        helper.setTime(14000);

        helper.succeedWhen(() -> {
            helper.assertTrue(merchant.hasEffect(MobEffects.INVISIBILITY), "It drinks the invisibility potion it carries at nightfall");
            helper.assertTrue(com.sheepfromheaven.minenow.cleric.PotionGoods.count(helper.getLevel(), merchant, Items.POTION, Potions.INVISIBILITY) == 0, "The potion is used up");
        });
    }

    public static void aMerchantBuysInvisibilityFromTheClericBeforeItSetsOut(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        BlockPos bell = new BlockPos(4, 1, 12);
        helper.setBlock(STATION, Merchant.PACK_STATION.get());
        Villager merchant = GameTestHelpers.spawnEmployed(helper, new BlockPos(9, 1, 12), STATION, Merchant.PROFESSION, Merchant.POI);
        merchant.getInventory().addItem(new ItemStack(Items.EMERALD, 8));
        VillageTests.claimBell(helper, merchant, bell);
        BlockPos stand = new BlockPos(14, 1, 15);
        helper.setBlock(stand, Blocks.BREWING_STAND);
        Villager cleric = GameTestHelpers.spawnEmployed(helper, stand.east(), stand, VillagerProfession.CLERIC, PoiTypes.CLERIC);
        VillageTests.claimBell(helper, cleric, bell);
        cleric.getInventory().addItem(PotionContents.createItemStack(Items.POTION, Potions.INVISIBILITY));
        long now = helper.getLevel().getGameTime();
        merchant.setData(Merchant.TOUR, Optional.of(new Tour(List.of(helper.absolutePos(new BlockPos(22, 1, 22))), 0, Tour.Phase.SHOP, now, now, merchant.blockPosition(), List.of())));

        helper.succeedWhen(() -> {
            helper.assertTrue(com.sheepfromheaven.minenow.cleric.PotionGoods.count(helper.getLevel(), merchant, Items.POTION, Potions.INVISIBILITY) == 1, "It buys the potion");
            helper.assertTrue(Economy.count(helper.getLevel(), cleric, Items.EMERALD) == 4 && Economy.count(helper.getLevel(), merchant, Items.EMERALD) == 4, "At the cleric's price of 4 emeralds");
        });
    }

    public static void aMerchantBuysWheatForItsBedAndCraftsIt(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(STATION, Merchant.PACK_STATION.get());
        Villager merchant = GameTestHelpers.spawnEmployed(helper, new BlockPos(11, 1, 12), STATION, Merchant.PROFESSION, Merchant.POI);
        merchant.getInventory().addItem(new ItemStack(Items.EMERALD, 8));
        BlockPos composter = new BlockPos(16, 1, 14);
        helper.setBlock(composter, Blocks.COMPOSTER);
        Villager farmer = GameTestHelpers.spawnEmployed(helper, composter.east(), composter, VillagerProfession.FARMER, PoiTypes.FARMER);
        farmer.getInventory().addItem(new ItemStack(Items.WHEAT, 64));

        helper.succeedWhen(() -> helper.assertTrue(Economy.count(helper.getLevel(), merchant, Items.STRAW_BED) >= 1,
                "It buys the farmer's wheat and crafts hay and a straw bed, has " + Economy.count(helper.getLevel(), merchant, Items.STRAW_BED) + " beds, "
                        + Economy.count(helper.getLevel(), merchant, Items.HAY_BLOCK) + " hay, " + Economy.count(helper.getLevel(), merchant, Items.WHEAT) + " wheat"));
    }

    /** A merchant away from home (pack station at x 10) on its way to a bell, ticks from the road. */
    private static Villager touring(GameTestHelper helper, Tour.Phase phase) {
        Villager merchant = spawnMerchant(helper, 1);
        long now = helper.getLevel().getGameTime();
        merchant.setData(Merchant.TOUR, Optional.of(new Tour(List.of(helper.absolutePos(new BlockPos(22, 1, 22))), 0, phase, now, now, merchant.blockPosition(), List.of(new Tour.Want(Items.WHEAT, 20)))));
        return merchant;
    }

    /** Village A (bell at x 4): a merchant at its pack station, and a farmer with wheat when {@code farmerSells}. */
    private static Villager homeVillage(GameTestHelper helper, boolean farmerSells) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        BlockPos bell = new BlockPos(4, 1, 12);
        helper.setBlock(STATION, Merchant.PACK_STATION.get());
        Villager merchant = GameTestHelpers.spawnEmployed(helper, new BlockPos(9, 1, 12), STATION, Merchant.PROFESSION, Merchant.POI);
        GameTestHelpers.giveStartingPurse(helper, merchant);
        VillageTests.claimBell(helper, merchant, bell);
        BlockPos composter = new BlockPos(12, 1, 15);
        helper.setBlock(composter, Blocks.COMPOSTER);
        Villager farmer = GameTestHelpers.spawnEmployed(helper, composter.east(), composter, VillagerProfession.FARMER, PoiTypes.FARMER);
        VillageTests.claimBell(helper, farmer, bell);
        if (farmerSells) farmer.getInventory().addItem(new ItemStack(Items.WHEAT, 40));
        return merchant;
    }

    /** Village B across the field (bell at x 67): a butcher with money and no wheat, and nobody who sells it. */
    private static Villager butcherAcross(GameTestHelper helper) {
        BlockPos bell = new BlockPos(67, 1, 12);
        BlockPos smoker = new BlockPos(64, 1, 12);
        helper.setBlock(smoker, Blocks.SMOKER);
        Villager butcher = GameTestHelpers.spawnEmployed(helper, smoker.east(), smoker, VillagerProfession.BUTCHER, PoiTypes.BUTCHER);
        butcher.getInventory().addItem(new ItemStack(Items.STONE_AXE));
        butcher.getInventory().addItem(new ItemStack(Items.EMERALD, 6));
        VillageTests.claimBell(helper, butcher, bell);
        return butcher;
    }

    static Villager spawnMerchant(GameTestHelper helper, int level) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(STATION, Merchant.PACK_STATION.get());
        Villager merchant = GameTestHelpers.spawnEmployed(helper, new BlockPos(11, 1, 12), STATION, Merchant.PROFESSION, Merchant.POI);
        merchant.setVillagerData(merchant.getVillagerData().withLevel(level));
        return merchant;
    }

    private static ServerPlayer player(GameTestHelper helper) {
        @SuppressWarnings("removal")
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SADDLE));
        return player;
    }

    private static void useOn(GameTestHelper helper, ServerPlayer player) {
        BlockPos absolute = helper.absolutePos(STATION);
        var hit = new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false);
        player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
    }
}
