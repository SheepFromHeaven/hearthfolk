package com.sheepfromheaven.minenow.villager;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** The village-wide alarm ({@link Alarms}) and where its defenders stand; on the 72-block-long field, in the {@code bell} environment. */
public final class AlarmTests {
    private AlarmTests() {}

    public static void aBellAlertsTheWholeVillage(GameTestHelper helper) {
        start(helper);
        BlockPos bell = new BlockPos(4, 1, 12);
        Villager swordsman = member(helper, new BlockPos(66, 1, 12), bell);
        Gear.wear(swordsman, new ItemStack(Items.IRON_SWORD));
        Villager unarmed = member(helper, new BlockPos(66, 1, 18), bell);
        boolean[] hid = {false};
        helper.onEachTick(() -> hid[0] |= unarmed.getBrain().isActive(Activity.HIDE));

        helper.runAfterDelay(20, () -> ring(helper, bell));
        helper.succeedWhen(() -> {
            helper.assertTrue(hid[0], "An unarmed villager of the village hides, though it's 62 blocks from the bell, beyond its sound");
            double away = swordsman.position().distanceTo(Vec3.atBottomCenterOf(helper.absolutePos(bell)));
            helper.assertTrue(away <= 4.0, "...and an armed one comes to the bell, " + away + " blocks away");
        });
    }

    public static void anArcherTakesTheFreePostNearestTheThreat(GameTestHelper helper) {
        start(helper);
        BlockPos bell = new BlockPos(36, 1, 12);
        Villager archer = archer(helper, new BlockPos(30, 1, 12), bell);
        resident(helper, new BlockPos(10, 1, 20), bell);
        resident(helper, new BlockPos(62, 1, 20), bell);
        BlockPos west = post(helper, new BlockPos(14, 1, 8));
        BlockPos east = post(helper, new BlockPos(58, 1, 8));
        ringer(helper, bell);
        helper.spawnWithNoFreeWill(EntityTypes.HUSK, new BlockPos(66, 1, 6));

        helper.runAfterDelay(20, () -> ring(helper, bell));
        helper.succeedWhen(() -> helper.assertTrue(on(helper, archer, east),
                "The archer takes the post nearest the husk the ringer saw, not the one nearest itself (" + west.toShortString() + "), is at " + helper.relativePos(archer.blockPosition()).toShortString() + debug(helper, archer)));
    }

    static String debug(GameTestHelper helper, Villager v) {
        return " activity " + v.getBrain().getActiveNonCoreActivity().map(Object::toString).orElse("-")
                + " running " + v.getBrain().getRunningBehaviors().stream().map(b -> b.getClass().getSimpleName()).filter(n -> !n.isEmpty()).toList()
                + " walk " + v.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET).map(w -> helper.relativePos(w.getTarget().currentBlockPosition()).toShortString()).orElse("-")
                + " alarm " + Alarms.of(helper.getLevel(), v).map(a -> a.threat().map(t -> helper.relativePos(t).toShortString()).orElse("unknown")).orElse("none")
                + " village " + Villages.of(helper.getLevel(), v).map(x -> x.anchors().size()).orElse(-1)
                + " posts " + Villages.of(helper.getLevel(), v).map(x -> HighGround.markedPosts(helper.getLevel(), x).stream().map(p -> helper.relativePos(p).toShortString()).toList().toString()).orElse("-");
    }

    public static void withNoThreatKnownArchersSpreadOverThePosts(GameTestHelper helper) {
        start(helper);
        BlockPos bell = new BlockPos(36, 1, 12);
        Villager first = archer(helper, new BlockPos(30, 1, 12), bell);
        Villager second = archer(helper, new BlockPos(42, 1, 12), bell);
        BlockPos west = post(helper, new BlockPos(20, 1, 4));
        BlockPos east = post(helper, new BlockPos(52, 1, 4));

        helper.runAfterDelay(20, () -> ring(helper, bell));
        helper.succeedWhen(() -> helper.assertTrue((on(helper, first, west) && on(helper, second, east)) || (on(helper, first, east) && on(helper, second, west)),
                "With no threat known the archers spread over the village's posts, 16 blocks from the bell, are at "
                        + helper.relativePos(first.blockPosition()).toShortString() + " and " + helper.relativePos(second.blockPosition()).toShortString()));
    }

    public static void swordsmenGatherOnTheThreatenedSide(GameTestHelper helper) {
        start(helper);
        BlockPos bell = new BlockPos(36, 1, 12);
        Villager swordsman = member(helper, new BlockPos(34, 1, 12), bell);
        Gear.wear(swordsman, new ItemStack(Items.IRON_SWORD));
        resident(helper, new BlockPos(10, 1, 20), bell);
        resident(helper, new BlockPos(62, 1, 20), bell);
        ringer(helper, bell);
        helper.spawnWithNoFreeWill(EntityTypes.HUSK, new BlockPos(68, 1, 20));

        helper.runAfterDelay(20, () -> ring(helper, bell));
        helper.succeedWhen(() -> helper.assertTrue(helper.relativePos(swordsman.blockPosition()).getX() >= 55,
                "The swordsman gathers on the side the husk is on, not at the bell, is at " + helper.relativePos(swordsman.blockPosition()).toShortString()));
    }

    public static void anArcherClimbsHighGroundOnTheSideFacingTheThreat(GameTestHelper helper) {
        start(helper);
        BlockPos bell = new BlockPos(8, 1, 12);
        Villager archer = archer(helper, new BlockPos(10, 1, 12), bell);
        resident(helper, new BlockPos(40, 1, 20), bell);
        resident(helper, new BlockPos(56, 1, 18), bell);
        for (int x = 50; x <= 56; x++) {
            int height = Math.min(x - 49, 4);
            for (int z = x < 54 ? 8 : 7; z <= (x < 54 ? 8 : 9); z++) for (int y = 1; y <= height; y++) helper.setBlock(x, y, z, Blocks.STONE_BRICKS);
        }
        ringer(helper, bell);
        helper.spawnWithNoFreeWill(EntityTypes.HUSK, new BlockPos(52, 1, 3));

        helper.runAfterDelay(20, () -> ring(helper, bell));
        helper.succeedWhen(() -> helper.assertTrue(archer.onGround() && helper.relativePos(archer.blockPosition()).getY() >= 5 && helper.relativePos(archer.blockPosition()).getX() >= 54,
                "The archer climbs the tower at the village's edge facing the husk, 46 blocks from the bell, is at " + helper.relativePos(archer.blockPosition()).toShortString() + debug(helper, archer)));
    }

    private static void start(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
    }

    /** A farmer at its composter that claimed the bell. */
    private static Villager member(GameTestHelper helper, BlockPos composter, BlockPos bell) {
        helper.setBlock(composter, Blocks.COMPOSTER);
        Villager villager = GameTestHelpers.spawnEmployed(helper, composter.east(), composter, VillagerProfession.FARMER, PoiTypes.FARMER);
        VillageTests.claimBell(helper, villager, bell);
        return villager;
    }

    /** A villager whose bed, out at the edge, makes the village reach that far; it stays put. */
    private static void resident(GameTestHelper helper, BlockPos bed, BlockPos bell) {
        Villager villager = GameTestHelpers.spawnWithHome(helper, bed.east(), bed);
        VillageTests.claimBell(helper, villager, bell);
        villager.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
    }

    private static Villager archer(GameTestHelper helper, BlockPos composter, BlockPos bell) {
        Villager archer = member(helper, composter, bell);
        archer.setVillagerData(archer.getVillagerData().withLevel(5));
        archer.setData(ModVillagers.ARCHER, true);
        Gear.wear(archer, new ItemStack(Items.BOW));
        Gear.wear(archer, new ItemStack(Items.ARROW, 32));
        return archer;
    }

    /** A hay bale post; returns the cell an archer stands in on top of it. */
    private static BlockPos post(GameTestHelper helper, BlockPos bale) {
        helper.setBlock(bale, Blocks.HAY_BLOCK);
        return bale.above();
    }

    /** The player who rings the bell, standing by it, who sees what's coming. */
    @SuppressWarnings("removal")
    private static void ringer(GameTestHelper helper, BlockPos bell) {
        var player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(bell.south()));
        player.snapTo(at.x, at.y, at.z);
    }

    private static void ring(GameTestHelper helper, BlockPos bell) {
        ((net.minecraft.world.level.block.BellBlock) Blocks.BELL).attemptToRing(helper.getLevel(), helper.absolutePos(bell), net.minecraft.core.Direction.EAST);
    }

    private static boolean on(GameTestHelper helper, Villager archer, BlockPos post) {
        Vec3 top = Vec3.atBottomCenterOf(helper.absolutePos(post));
        return archer.onGround() && archer.getY() >= top.y - 0.1 && Math.hypot(archer.getX() - top.x, archer.getZ() - top.z) <= 1.0;
    }
}
