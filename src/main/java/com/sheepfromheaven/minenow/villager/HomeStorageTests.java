package com.sheepfromheaven.minenow.villager;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.GameType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.util.Optional;

public final class HomeStorageTests {
    private static final BlockPos BED = new BlockPos(4, 1, 4);
    private static final BlockPos CHEST = new BlockPos(6, 1, 4);

    private HomeStorageTests() {}

    public static void villagerClaimsAndLocksChestNearItsBed(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        helper.setBlock(CHEST, Blocks.CHEST);
        Villager villager = GameTestHelpers.spawnWithHome(helper, BED.east(2).south(), BED);

        helper.succeedWhen(() -> {
            Optional<GlobalPos> claimed = HomeStorage.storageOf(villager);
            helper.assertTrue(claimed.isPresent(), "Villager should claim the chest near its bed");
            helper.assertTrue(claimed.get().pos().equals(helper.absolutePos(CHEST)), "Claimed the wrong chest: " + claimed.get());
            ChestBlockEntity chest = helper.getBlockEntity(CHEST, ChestBlockEntity.class);
            helper.assertTrue(!chest.canOpen(helper.makeMockPlayer(GameType.SURVIVAL)), "Players should not be able to open a villager's chest");
        });
    }

    public static void twoVillagersDoNotShareOneChest(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        helper.setBlock(CHEST, Blocks.CHEST);
        Villager first = GameTestHelpers.spawnWithHome(helper, BED.south(2), BED);
        Villager second = GameTestHelpers.spawnWithHome(helper, BED.east(4).south(2), BED.east(4));

        helper.runAfterDelay(100, () -> helper.succeedWhen(() -> {
            boolean firstHas = HomeStorage.storageOf(first).isPresent();
            boolean secondHas = HomeStorage.storageOf(second).isPresent();
            helper.assertTrue(firstHas ^ secondHas, "Exactly one villager should own the chest (first=" + firstHas + ", second=" + secondHas + ")");
        }));
    }

    public static void breakingAClaimedChestIsTheft(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        helper.setBlock(CHEST, Blocks.CHEST);
        Villager owner = GameTestHelpers.spawnWithHome(helper, BED.east(2).south(), BED);
        @SuppressWarnings("removal")
        ServerPlayer thief = helper.makeMockServerPlayerInLevel();
        boolean[] broken = {false};

        helper.onEachTick(() -> {
            if (!broken[0] && HomeStorage.storageOf(owner).isPresent()) {
                broken[0] = true;
                thief.gameMode.destroyBlock(helper.absolutePos(CHEST));
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(broken[0], "Chest was never claimed, so it was never broken");
            int reputation = owner.getGossips().getReputation(thief.getUUID(), type -> true);
            helper.assertTrue(reputation < 0, "Owner should think worse of the thief, reputation=" + reputation);
            helper.assertTrue(HomeStorage.storageOf(owner).isEmpty(), "Owner should forget a chest that no longer exists");
        });
    }

    public static void ownerDyingReleasesAndUnlocksItsChest(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        helper.setBlock(CHEST, Blocks.CHEST);
        Villager owner = GameTestHelpers.spawnWithHome(helper, BED.east(2).south(), BED);
        boolean[] killed = {false};

        helper.onEachTick(() -> {
            if (!killed[0] && HomeStorage.storageOf(owner).isPresent()) {
                killed[0] = true;
                owner.kill(helper.getLevel());
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(killed[0], "Chest was never claimed");
            helper.assertTrue(!helper.getBlockEntity(CHEST, ChestBlockEntity.class).isLocked(), "Chest should unlock when its owner dies");
            long free = helper.getLevel().getPoiManager().getCountInRange(
                    HomeStorage::isStoragePoi, helper.absolutePos(CHEST), 0, PoiManager.Occupancy.HAS_SPACE);
            helper.assertTrue(free == 1, "Chest should be claimable again");
        });
    }

    public static void placingAChestNextToAnOwnedChestExtendsTheClaim(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        helper.setBlock(CHEST, Blocks.CHEST);
        Villager owner = GameTestHelpers.spawnWithHome(helper, BED.east(2).south(), BED);
        Villager neighbour = helper.spawn(net.minecraft.world.entity.EntityTypes.VILLAGER, BED.east(6).south(2));
        BlockPos[] added = {null};

        helper.onEachTick(() -> {
            if (added[0] == null && HomeStorage.storageOf(owner).isPresent()) {
                added[0] = makeDouble(helper, CHEST);
                GameTestHelpers.giveHome(helper, neighbour, BED.east(6));
            }
        });
        helper.runAfterDelay(200, () -> helper.succeedWhen(() -> {
            helper.assertTrue(added[0] != null, "Chest was never claimed");
            helper.assertTrue(HomeStorage.storageOf(neighbour).isEmpty(), "Neighbour must not claim the other half of an owned chest");
            helper.assertTrue(helper.getBlockEntity(added[0], ChestBlockEntity.class).isLocked(), "Added half should be locked too");
            helper.assertTrue(helper.getBlockEntity(CHEST, ChestBlockEntity.class).isLocked(), "Original half should stay locked");
        }));
    }

    public static void breakingOneHalfOfAnOwnedDoubleChestKeepsTheOtherHalf(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        helper.setBlock(CHEST, Blocks.CHEST);
        BlockPos otherHalf = makeDouble(helper, CHEST);
        Villager owner = GameTestHelpers.spawnWithHome(helper, BED.east(2).south(), BED);
        @SuppressWarnings("removal")
        ServerPlayer thief = helper.makeMockServerPlayerInLevel();
        BlockPos[] remaining = {null};

        helper.onEachTick(() -> {
            if (remaining[0] == null && HomeStorage.storageOf(owner).isPresent()) {
                BlockPos claimed = helper.relativePos(HomeStorage.storageOf(owner).get().pos());
                remaining[0] = claimed.equals(CHEST) ? otherHalf : CHEST;
                thief.gameMode.destroyBlock(helper.absolutePos(claimed));
                helper.runAfterDelay(1, () -> assertKeptRemainingHalf(helper, owner, thief, remaining[0]));
            }
        });
    }

    private static void assertKeptRemainingHalf(GameTestHelper helper, Villager owner, ServerPlayer thief, BlockPos remaining) {
        {
            helper.assertTrue(owner.getGossips().getReputation(thief.getUUID(), type -> true) < 0, "Breaking half the chest is still theft");
            helper.assertTrue(HomeStorage.storageOf(owner).map(pos -> pos.pos().equals(helper.absolutePos(remaining))).orElse(false),
                    "Owner should keep the remaining half, has " + HomeStorage.storageOf(owner));
            helper.assertTrue(helper.getBlockEntity(remaining, ChestBlockEntity.class).isLocked(), "Remaining half should stay locked");
            helper.succeed();
        }
    }

    private static final BlockPos RAISED_CHEST = new BlockPos(6, 2, 4);

    public static void hopperDrainingAnOwnedChestIsTheftByItsPlacer(GameTestHelper helper) {
        @SuppressWarnings("removal")
        ServerPlayer thief = helper.makeMockServerPlayerInLevel();
        Villager owner = setUpRaisedChest(helper);
        boolean[] placed = {false};

        helper.onEachTick(() -> {
            if (!placed[0] && HomeStorage.storageOf(owner).isPresent()) {
                placed[0] = true;
                useItemOn(helper, thief, Items.HOPPER, RAISED_CHEST.below(2), Direction.UP);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(placed[0], "Chest was never claimed");
            helper.assertBlockPresent(Blocks.HOPPER, RAISED_CHEST.below());
            int reputation = owner.getGossips().getReputation(thief.getUUID(), type -> true);
            helper.assertTrue(reputation < 0, "Whoever placed the draining hopper is a thief, reputation=" + reputation);
        });
    }

    public static void hopperMinecartDrainingAnOwnedChestIsTheftByItsPlacer(GameTestHelper helper) {
        @SuppressWarnings("removal")
        ServerPlayer thief = helper.makeMockServerPlayerInLevel();
        Villager owner = setUpRaisedChest(helper);
        helper.setBlock(RAISED_CHEST.below(), Blocks.RAIL);
        boolean[] placed = {false};

        helper.onEachTick(() -> {
            if (!placed[0] && HomeStorage.storageOf(owner).isPresent()) {
                placed[0] = true;
                useItemOn(helper, thief, Items.HOPPER_MINECART, RAISED_CHEST.below(), Direction.UP);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(placed[0], "Chest was never claimed");
            helper.assertEntityPresent(net.minecraft.world.entity.EntityTypes.HOPPER_MINECART);
            int reputation = owner.getGossips().getReputation(thief.getUUID(), type -> true);
            helper.assertTrue(reputation < 0, "Whoever placed the draining hopper minecart is a thief, reputation=" + reputation);
        });
    }

    public static void villagerDoesNotClaimAChestAboveAHopper(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        helper.setBlock(RAISED_CHEST.below(), Blocks.HOPPER);
        helper.setBlock(RAISED_CHEST, Blocks.CHEST);
        Villager owner = GameTestHelpers.spawnWithHome(helper, BED.east(2).south(), BED);

        helper.runAfterDelay(120, () -> {
            helper.assertTrue(HomeStorage.storageOf(owner).isEmpty(), "Villager should not claim a chest with a hopper under it");
            helper.succeed();
        });
    }

    private static Villager setUpRaisedChest(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        helper.setBlock(RAISED_CHEST, Blocks.CHEST);
        helper.getBlockEntity(RAISED_CHEST, ChestBlockEntity.class).setItem(0, new ItemStack(Items.WHEAT, 64));
        return GameTestHelpers.spawnWithHome(helper, BED.east(2).south(), BED);
    }

    private static void useItemOn(GameTestHelper helper, ServerPlayer player, net.minecraft.world.item.Item item, BlockPos clicked, Direction face) {
        ItemStack stack = new ItemStack(item);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos abs = helper.absolutePos(clicked);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs).relative(face, 0.5), face, abs, false);
        player.gameMode.useItemOn(player, helper.getLevel(), stack, InteractionHand.MAIN_HAND, hit);
    }

    private static BlockPos makeDouble(GameTestHelper helper, BlockPos existing) {
        BlockState right = helper.getBlockState(existing).setValue(ChestBlock.TYPE, ChestType.RIGHT);
        BlockPos other = existing.relative(ChestBlock.getConnectedDirection(right));
        helper.setBlock(existing, right);
        helper.setBlock(other, right.setValue(ChestBlock.TYPE, ChestType.LEFT));
        return other;
    }
}
