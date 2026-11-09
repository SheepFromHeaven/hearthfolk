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

    public static void rightClickingAnOwnedChestSaysWhoOwnsIt(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        helper.setBlock(CHEST, Blocks.CHEST);
        Villager owner = GameTestHelpers.spawnWithHome(helper, BED.east(2).south(), BED);
        owner.setCustomName(net.minecraft.network.chat.Component.literal("Jim"));
        @SuppressWarnings("removal")
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        helper.succeedWhen(() -> {
            helper.assertTrue(HomeStorage.storageOf(owner).isPresent(), "Chest not claimed yet");
            BlockPos abs = helper.absolutePos(CHEST);
            var event = net.neoforged.neoforge.common.CommonHooks.onRightClickBlock(player, InteractionHand.MAIN_HAND, abs,
                    new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
            helper.assertTrue(event.isCanceled(), "Our message should replace vanilla's 'Chest is locked!'");
            String message = HomeStorage.ownerMessage(helper.getLevel(), abs).map(net.minecraft.network.chat.Component::getString).orElse("");
            helper.assertTrue(message.contains("Jim"), "Message should name the owner, got '" + message + "'");
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

    public static void breakingAHomeChestCostsNothingAndItsGoodsStayTheOwners(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        helper.setBlock(CHEST, Blocks.CHEST);
        helper.getBlockEntity(CHEST, ChestBlockEntity.class).setItem(0, new ItemStack(Items.COBBLESTONE, 10));
        helper.getBlockEntity(CHEST, ChestBlockEntity.class).setItem(1, new ItemStack(Items.EMERALD, 3));
        Villager owner = GameTestHelpers.spawnWithHome(helper, BED.east(2).south(), BED);
        ServerPlayer player = survivalPlayer(helper);
        boolean[] broken = {false};

        helper.onEachTick(() -> {
            if (broken[0] || HomeStorage.storageOf(owner).isEmpty()) return;
            broken[0] = true;
            player.gameMode.destroyBlock(helper.absolutePos(CHEST));
            var dropped = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, helper.getBounds(), item -> item.getItem().is(Items.EMERALD));
            helper.assertTrue(!dropped.isEmpty(), "The chest's goods drop when it breaks");
            dropped.getFirst().setNoPickUpDelay();
            dropped.getFirst().playerTouch(player);
            helper.assertTrue(dropped.getFirst().isAlive() && player.getInventory().countItem(Items.EMERALD) == 0, "...but they're still the villager's, so the player can't pick them up");
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(broken[0], "Chest was never claimed, so it was never broken");
            int reputation = owner.getGossips().getReputation(player.getUUID(), type -> true);
            helper.assertTrue(reputation == 0, "Moving a villager's chest isn't theft, reputation " + reputation);
            helper.assertTrue(HomeStorage.storageOf(owner).isEmpty(), "Owner should forget a chest that no longer exists");
            helper.assertTrue(owner.getInventory().countItem(Items.EMERALD) == 3 && owner.getInventory().countItem(Items.COBBLESTONE) == 10,
                    "The owner gathers its goods up again, has " + owner.getInventory().countItem(Items.EMERALD) + " emeralds, " + owner.getInventory().countItem(Items.COBBLESTONE) + " cobblestone");
        });
    }

    public static void aHopperSwallowingAVillagersDroppedGoodsIsTheftByItsPlacer(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        @SuppressWarnings("removal")
        ServerPlayer thief = helper.makeMockServerPlayerInLevel();
        Villager owner = helper.spawn(net.minecraft.world.entity.EntityTypes.VILLAGER, new BlockPos(20, 1, 20));
        owner.setNoAi(true);
        BlockPos hopper = new BlockPos(10, 1, 10);
        useItemOn(helper, thief, Items.HOPPER, hopper.below(), Direction.UP);
        var goods = new net.minecraft.world.SimpleContainer(new ItemStack(Items.EMERALD, 3));
        Belongings.drop(helper.getLevel(), helper.absolutePos(hopper.above()), goods, owner);

        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.HOPPER, hopper);
            var container = helper.getBlockEntity(hopper, net.minecraft.world.level.block.entity.HopperBlockEntity.class);
            helper.assertTrue(container.countItem(Items.EMERALD) == 3, "The hopper swallows the villager's emeralds, has " + container.countItem(Items.EMERALD));
            int reputation = owner.getGossips().getReputation(thief.getUUID(), type -> true);
            helper.assertTrue(reputation < 0, "Whoever placed the hopper stole the villager's goods, reputation " + reputation);
        });
    }

    public static void aDeadOwnersGoodsAreFreeToTake(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        helper.setBlock(CHEST, Blocks.CHEST);
        helper.getBlockEntity(CHEST, ChestBlockEntity.class).setItem(0, new ItemStack(Items.EMERALD, 3));
        Villager owner = GameTestHelpers.spawnWithHome(helper, BED.east(2).south(), BED);
        ServerPlayer player = survivalPlayer(helper);
        boolean[] done = {false};

        helper.onEachTick(() -> {
            if (done[0] || HomeStorage.storageOf(owner).isEmpty()) return;
            done[0] = true;
            player.gameMode.destroyBlock(helper.absolutePos(CHEST));
            owner.kill(helper.getLevel());
            var dropped = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, helper.getBounds(), item -> item.getItem().is(Items.EMERALD));
            dropped.forEach(item -> {
                item.setNoPickUpDelay();
                item.playerTouch(player);
            });
            helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 3, "Once the owner is dead its goods are anyone's, the player picked up " + player.getInventory().countItem(Items.EMERALD));
            helper.succeed();
        });
    }

    private static ServerPlayer survivalPlayer(GameTestHelper helper) {
        @SuppressWarnings("removal")
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        return player;
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
            helper.assertTrue(owner.getGossips().getReputation(thief.getUUID(), type -> true) == 0, "Breaking half the chest isn't theft either");
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
