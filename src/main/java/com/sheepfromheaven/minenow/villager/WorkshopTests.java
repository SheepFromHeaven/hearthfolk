package com.sheepfromheaven.minenow.villager;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

public final class WorkshopTests {
    private static final BlockPos LOOM = new BlockPos(5, 1, 12);
    private static final BlockPos NEAR_CHEST = new BlockPos(10, 1, 12);
    private static final BlockPos FAR_CHEST = new BlockPos(10, 1, 15);
    private static final BlockPos BED = new BlockPos(18, 1, 4);
    private static final BlockPos HOME_CHEST = new BlockPos(20, 1, 4);

    private WorkshopTests() {}

    public static void aWorkshopClaimsAsManyChestsAsItsLevelAllows(GameTestHelper helper) {
        Villager shepherd = spawnShepherd(helper);
        helper.setBlock(NEAR_CHEST, Blocks.CHEST);
        helper.setBlock(FAR_CHEST, Blocks.CHEST);
        boolean[] promoted = {false};

        helper.succeedWhen(() -> {
            if (!promoted[0]) {
                helper.assertTrue(Workshop.chestsOf(helper.getLevel(), shepherd).size() == 1, "A novice claims one chest near its loom, has " + Workshop.chestsOf(helper.getLevel(), shepherd));
                helper.assertTrue(Workshop.chestsOf(helper.getLevel(), shepherd).contains(helper.absolutePos(NEAR_CHEST)), "...the nearest");
                helper.assertTrue(helper.getBlockEntity(NEAR_CHEST, ChestBlockEntity.class).isLocked(), "...and locks it for players");
                shepherd.setVillagerData(shepherd.getVillagerData().withLevel(2));
                promoted[0] = true;
            }
            helper.assertTrue(Workshop.chestsOf(helper.getLevel(), shepherd).size() == 2, "An apprentice claims a second chest, has " + Workshop.chestsOf(helper.getLevel(), shepherd));
        });
    }

    public static void trappedChestsAreNeverClaimed(GameTestHelper helper) {
        Villager shepherd = spawnShepherd(helper);
        GameTestHelpers.giveHome(helper, shepherd, BED);
        helper.setBlock(NEAR_CHEST, Blocks.TRAPPED_CHEST);
        helper.setBlock(HOME_CHEST, Blocks.TRAPPED_CHEST);

        helper.runAfterDelay(200, () -> {
            helper.assertTrue(Workshop.chestsOf(helper.getLevel(), shepherd).isEmpty(), "A trapped chest is the player's, not a workshop's");
            helper.assertTrue(HomeStorage.storageOf(shepherd).isEmpty(), "...nor a home chest");
            helper.assertFalse(helper.getBlockEntity(NEAR_CHEST, ChestBlockEntity.class).isLocked(), "It stays open to the player");
            helper.succeed();
        });
    }

    public static void tradeGoodsGoToTheWorkshopAndEmeraldsGoHome(GameTestHelper helper) {
        Villager shepherd = spawnShepherd(helper);
        GameTestHelpers.giveHome(helper, shepherd, BED);
        helper.setBlock(HOME_CHEST, Blocks.CHEST);
        helper.setBlock(NEAR_CHEST, Blocks.CHEST);
        shepherd.getInventory().addItem(new ItemStack(Items.SHEARS));
        shepherd.getInventory().addItem(new ItemStack(Items.WOOL.white(), 64));
        shepherd.getInventory().addItem(new ItemStack(Items.EMERALD, 5));

        helper.succeedWhen(() -> {
            var workshop = helper.getBlockEntity(NEAR_CHEST, ChestBlockEntity.class);
            var home = helper.getBlockEntity(HOME_CHEST, ChestBlockEntity.class);
            helper.assertTrue(workshop.countItem(Items.WOOL.white()) == 64, "Its wool goes into the chest by its loom, has " + workshop.countItem(Items.WOOL.white()));
            helper.assertTrue(home.countItem(Items.EMERALD) == 5, "...its emeralds into the chest by its bed, has " + home.countItem(Items.EMERALD)
                    + "; carries " + shepherd.getInventory().countItem(Items.EMERALD) + ", home " + HomeStorage.storageOf(shepherd) + ", workshop " + Workshop.chestsOf(helper.getLevel(), shepherd)
                    + ", at " + helper.relativePos(shepherd.blockPosition()).toShortString() + ", running " + shepherd.getBrain().getRunningBehaviors().stream().map(b -> b.getClass().getSimpleName()).filter(n -> !n.isEmpty()).toList());
            helper.assertTrue(home.countItem(Items.WOOL.white()) == 0 && workshop.countItem(Items.EMERALD) == 0, "...and neither ends up in the other");
        });
    }

    public static void goodsInAWorkshopChestAreForSale(GameTestHelper helper) {
        Villager shepherd = spawnShepherd(helper);
        shepherd.getInventory().addItem(new ItemStack(Items.SHEARS));
        helper.setBlock(NEAR_CHEST, Blocks.CHEST);
        helper.getBlockEntity(NEAR_CHEST, ChestBlockEntity.class).setItem(0, new ItemStack(Items.WOOL.white(), 36));
        @SuppressWarnings("removal")
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        helper.succeedWhen(() -> {
            helper.assertTrue(!Workshop.chestsOf(helper.getLevel(), shepherd).isEmpty(), "It claims the chest first");
            player.closeContainer();
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.interactOn(shepherd, InteractionHand.MAIN_HAND, shepherd.position());
            helper.assertTrue(shepherd.getOffers().stream().anyMatch(offer -> offer.getResult().is(Items.WOOL.white())),
                    "The wool in its workshop chest is for sale, offers " + shepherd.getOffers().stream().map(o -> o.getCostA() + "->" + o.getResult()).toList());
        });
    }

    private static Villager spawnShepherd(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(LOOM, Blocks.LOOM);
        return GameTestHelpers.spawnEmployed(helper, LOOM.east(), LOOM, VillagerProfession.SHEPHERD, PoiTypes.SHEPHERD);
    }
}
