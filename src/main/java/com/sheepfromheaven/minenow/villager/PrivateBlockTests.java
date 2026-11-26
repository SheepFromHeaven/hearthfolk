package com.sheepfromheaven.minenow.villager;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class PrivateBlockTests {
    private static final BlockPos BED = new BlockPos(4, 1, 4);
    private static final BlockPos CHEST = new BlockPos(6, 1, 4);
    private static final BlockPos COMPOSTER = new BlockPos(8, 1, 8);

    private PrivateBlockTests() {}

    public static void aChestAPlayerPlacesStaysTheirs(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        ServerPlayer player = player(helper);
        place(helper, player, Items.CHEST, CHEST);
        Villager villager = GameTestHelpers.spawnWithHome(helper, BED.east(2).south(), BED);

        helper.runAfterDelay(300, () -> {
            helper.assertTrue(helper.getBlockState(CHEST).is(Blocks.CHEST), "The player placed a chest");
            helper.assertTrue(HomeStorage.storageOf(villager).isEmpty(), "A chest the player placed is theirs until they allow it, the villager claimed " + HomeStorage.storageOf(villager));
            helper.succeed();
        });
    }

    public static void aChestAllowedWithAnEmeraldIsClaimed(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        ServerPlayer player = player(helper);
        place(helper, player, Items.CHEST, CHEST);
        Villager villager = GameTestHelpers.spawnWithHome(helper, BED.east(2).south(), BED);
        helper.runAfterDelay(20, () -> toggle(helper, player, CHEST));

        helper.succeedWhen(() -> helper.assertTrue(HomeStorage.storageOf(villager).filter(claimed -> claimed.pos().equals(helper.absolutePos(CHEST))).isPresent(),
                "A sneak-click with an emerald lets villagers claim the chest, claimed " + HomeStorage.storageOf(villager)));
    }

    public static void makingAClaimedChestPrivateFreesItAndItsGoods(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        helper.setBlock(CHEST, Blocks.CHEST);
        Villager villager = GameTestHelpers.spawnWithHome(helper, BED.east(2).south(), BED);
        ServerPlayer player = player(helper);
        GameTestHelpers.fillOnceClaimed(helper, () -> HomeStorage.storageOf(villager).isPresent(), CHEST, new ItemStack(Items.BREAD, 5));
        boolean[] toggled = {false};
        helper.onEachTick(() -> {
            if (toggled[0] || HomeStorage.storageOf(villager).isEmpty() || !helper.getBlockEntity(CHEST, net.minecraft.world.level.block.entity.ChestBlockEntity.class).getItem(0).is(Items.BREAD)) return;
            toggled[0] = true;
            toggle(helper, player, CHEST);
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(toggled[0], "Waiting for the villager to claim and fill its chest");
            helper.assertTrue(HomeStorage.storageOf(villager).isEmpty() || !HomeStorage.storageOf(villager).get().pos().equals(helper.absolutePos(CHEST)),
                    "A chest the player makes theirs is no longer the villager's, it holds " + HomeStorage.storageOf(villager));
            int bread = helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds(), item -> item.getItem().is(Items.BREAD)).stream().mapToInt(item -> item.getItem().getCount()).sum()
                    + Economy.count(helper.getLevel(), villager, Items.BREAD);
            helper.assertTrue(bread == 5, "...and the villager's bread comes out of it as its belongings, found " + bread);
            helper.assertTrue(helper.getBlockEntity(CHEST, net.minecraft.world.level.block.entity.ChestBlockEntity.class).isEmpty(), "The chest is left empty for the player");
        });
    }

    public static void aJobBlockAPlayerPlacesIsNotTaken(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        ServerPlayer player = player(helper);
        place(helper, player, Items.COMPOSTER, COMPOSTER);
        Villager villager = helper.spawn(EntityTypes.VILLAGER, COMPOSTER.west(3));

        helper.runAfterDelay(400, () -> {
            helper.assertTrue(helper.getBlockState(COMPOSTER).is(Blocks.COMPOSTER), "The player placed a composter");
            helper.assertTrue(villager.getVillagerData().profession().is(VillagerProfession.NONE) && villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty(),
                    "A composter the player placed is decoration until they allow it, the villager is " + villager.getVillagerData().profession().getRegisteredName());
            helper.succeed();
        });
    }

    public static void aJobBlockAllowedWithAnEmeraldIsTaken(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        ServerPlayer player = player(helper);
        place(helper, player, Items.COMPOSTER, COMPOSTER);
        Villager villager = helper.spawn(EntityTypes.VILLAGER, COMPOSTER.west(3));
        helper.runAfterDelay(20, () -> toggle(helper, player, COMPOSTER));

        helper.succeedWhen(() -> helper.assertTrue(villager.getVillagerData().profession().is(VillagerProfession.FARMER),
                "Allowed with an emerald, the composter makes the villager a farmer, it is " + villager.getVillagerData().profession().getRegisteredName()));
    }

    public static void makingAClaimedJobBlockPrivateReleasesIt(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(COMPOSTER, Blocks.COMPOSTER);
        Villager farmer = GameTestHelpers.spawnEmployed(helper, COMPOSTER.west(), COMPOSTER, VillagerProfession.FARMER, PoiTypes.FARMER);
        farmer.setVillagerData(farmer.getVillagerData().withLevel(2));
        ServerPlayer player = player(helper);
        helper.runAfterDelay(20, () -> toggle(helper, player, COMPOSTER));

        helper.runAfterDelay(200, () -> {
            helper.assertTrue(farmer.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty(), "A job block the player makes theirs is no longer the farmer's job site");
            helper.assertTrue(farmer.getVillagerData().profession().is(VillagerProfession.FARMER), "...but an apprentice keeps its trade and may find another composter (a novice with no experience loses it, as vanilla does when its block is broken)");
            helper.succeed();
        });
    }

    private static ServerPlayer player(GameTestHelper helper) {
        @SuppressWarnings("removal")
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.CREATIVE);
        player.snapTo(helper.absoluteVec(new Vec3(2.5, 1, 12.5)));
        return player;
    }

    /** The player places the block on the floor at {@code at}, as a player does, so the place event fires. */
    private static void place(GameTestHelper helper, ServerPlayer player, net.minecraft.world.item.Item item, BlockPos at) {
        player.setShiftKeyDown(false);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
        BlockPos floor = helper.absolutePos(at.below());
        player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false));
    }

    /** A sneak-click with an emerald. */
    static void toggle(GameTestHelper helper, ServerPlayer player, BlockPos at) {
        player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.EMERALD));
        BlockPos abs = helper.absolutePos(at);
        player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
        player.setShiftKeyDown(false);
    }
}
