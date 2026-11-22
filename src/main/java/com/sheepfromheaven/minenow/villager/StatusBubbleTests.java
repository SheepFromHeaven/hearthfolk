package com.sheepfromheaven.minenow.villager;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

public final class StatusBubbleTests {
    private static final BlockPos COMPOSTER = new BlockPos(11, 1, 12);
    private static final BlockPos SMOKER = new BlockPos(5, 1, 12);
    private static final BlockPos BELL = new BlockPos(4, 1, 12);

    private StatusBubbleTests() {}

    /** A farmer with a hoe and a field, so it needs nothing. */
    private static Villager content(GameTestHelper helper, BlockPos composter) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(composter, Blocks.COMPOSTER);
        helper.setBlock(composter.north().below(), Blocks.FARMLAND);
        Villager farmer = GameTestHelpers.spawnEmployed(helper, composter.east(), composter, VillagerProfession.FARMER, PoiTypes.FARMER);
        farmer.getInventory().addItem(new ItemStack(Items.IRON_HOE));
        return farmer;
    }

    private static void showsItem(GameTestHelper helper, Villager villager, Item expected) {
        NeedBubbles.Shown shown = NeedBubbles.bubbleFor(helper.getLevel(), villager);
        helper.assertTrue(shown.item().is(expected) && shown.image() == null, "Expected a " + expected + " bubble, got " + shown);
    }

    public static void aHungryVillagerShowsBread(GameTestHelper helper) {
        Villager farmer = content(helper, COMPOSTER);
        helper.assertTrue(IdleReason.of(helper.getLevel(), farmer).isEmpty(), "The farmer needs nothing: " + IdleReason.of(helper.getLevel(), farmer));
        Meals.makeHungry(farmer);
        showsItem(helper, farmer, Items.BREAD);
        helper.succeed();
    }

    public static void withoutMealsAHungryVillagerShowsNoBread(GameTestHelper helper) {
        Villager farmer = content(helper, COMPOSTER);
        Meals.makeHungry(farmer);
        helper.assertTrue(NeedBubbles.bubbleFor(helper.getLevel(), farmer).isEmpty(), "With meals off nobody is hungry, so no bubble");
        helper.succeed();
    }

    public static void aNeedShowsInsteadOfHunger(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(COMPOSTER, Blocks.COMPOSTER);
        helper.setBlock(COMPOSTER.north().below(), Blocks.FARMLAND);
        Villager farmer = GameTestHelpers.spawnEmployed(helper, COMPOSTER.east(), COMPOSTER, VillagerProfession.FARMER, PoiTypes.FARMER);
        Meals.makeHungry(farmer);
        helper.assertTrue(IdleReason.of(helper.getLevel(), farmer).contains(IdleReason.NO_TOOL), "A farmer without a hoe lacks its tool");
        helper.assertTrue(NeedBubbles.statusFor(helper.getLevel(), farmer).item().is(Items.BREAD), "It is hungry too");
        showsItem(helper, farmer, TradeTable.forVillager(farmer).orElseThrow().tools().getFirst().item());
        helper.succeed();
    }

    public static void aVillagerWalkingToShelterShowsAWaterBucket(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        RainShelterTests.openSky(helper);
        helper.setBlock(SMOKER, Blocks.SMOKER);
        RainShelterTests.roof(helper, SMOKER.offset(4, 3, 1));
        RainShelterTests.whenItRains(helper, () -> {
            Villager butcher = GameTestHelpers.spawnEmployed(helper, SMOKER.west(), SMOKER, VillagerProfession.BUTCHER, PoiTypes.BUTCHER);
            boolean[] shown = {false};
            helper.onEachTick(() -> shown[0] |= butcher.getBrain().getRunningBehaviors().stream().anyMatch(SeekShelter.class::isInstance)
                    && NeedBubbles.statusFor(helper.getLevel(), butcher).item().is(Items.WATER_BUCKET));
            helper.succeedWhen(() -> {
                helper.assertTrue(shown[0], "A villager walking out of the rain shows a water bucket");
                RainShelterTests.restoreSky(helper);
            });
        });
    }

    public static void aPanickingVillagerShowsAFrightIcon(GameTestHelper helper) {
        Villager farmer = content(helper, COMPOSTER);
        helper.spawnWithNoFreeWill(EntityTypes.HUSK, new BlockPos(10, 1, 12));
        boolean[] shown = {false};
        helper.onEachTick(() -> {
            NeedBubbles.Shown status = NeedBubbles.statusFor(helper.getLevel(), farmer);
            shown[0] |= farmer.getBrain().isActive(Activity.PANIC) && NeedBubbles.FRIGHT.equals(status.image()) && status.item().isEmpty();
        });
        helper.succeedWhen(() -> helper.assertTrue(shown[0], "A villager fleeing a husk shows the fright icon"));
    }

    @SuppressWarnings("removal")
    public static void aVillagerInAnAlarmShowsABell(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(COMPOSTER, Blocks.COMPOSTER);
        Villager farmer = GameTestHelpers.spawnEmployed(helper, COMPOSTER.east(), COMPOSTER, VillagerProfession.FARMER, PoiTypes.FARMER);
        farmer.getInventory().addItem(new ItemStack(Items.IRON_HOE));
        VillageTests.claimBell(helper, farmer, BELL);
        var player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        net.minecraft.world.phys.Vec3 at = net.minecraft.world.phys.Vec3.atBottomCenterOf(helper.absolutePos(BELL.south()));
        player.snapTo(at.x, at.y, at.z);
        helper.runAfterDelay(20, () -> ((net.minecraft.world.level.block.BellBlock) Blocks.BELL).attemptToRing(helper.getLevel(), helper.absolutePos(BELL), net.minecraft.core.Direction.EAST));
        helper.succeedWhen(() -> {
            helper.assertTrue(Alarms.of(helper.getLevel(), farmer).isPresent(), "The village is in an alarm");
            helper.assertTrue(NeedBubbles.statusFor(helper.getLevel(), farmer).item().is(Items.BELL), "A villager in an alarm shows a bell");
        });
    }
}
