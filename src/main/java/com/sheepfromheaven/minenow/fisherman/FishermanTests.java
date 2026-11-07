package com.sheepfromheaven.minenow.fisherman;

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
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Blocks;

import java.util.Optional;

public final class FishermanTests {
    static final BlockPos BARREL = new BlockPos(6, 1, 12);

    private FishermanTests() {}

    public static void fishermanWithoutARodOffersToBuyOne(GameTestHelper helper) {
        Villager fisherman = spawnFisherman(helper);
        @SuppressWarnings("removal")
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.interactOn(fisherman, InteractionHand.MAIN_HAND, fisherman.position());

        Optional<MerchantOffer> buyRod = fisherman.getOffers().stream().filter(o -> o.getCostA().is(Items.FISHING_ROD)).findFirst();
        helper.assertTrue(buyRod.isPresent() && buyRod.get().getResult().is(Items.EMERALD),
                "A fisherman without a rod should offer emeralds for one, offers: " + fisherman.getOffers().size());
        helper.succeed();
    }

    public static void fishermanFishesWaterNearItsBarrel(GameTestHelper helper) {
        Villager fisherman = spawnFisherman(helper);
        digPool(helper, 11, 10);
        fisherman.getInventory().addItem(new ItemStack(Items.FISHING_ROD));

        helper.succeedWhen(() -> {
            helper.assertTrue(catchCount(fisherman) >= 2, "The fisherman should catch something from its water, caught " + catchCount(fisherman));
            helper.assertTrue(rod(fisherman).getDamageValue() >= 2, "Each catch should wear the rod");
        });
    }

    public static void fishermanCastsABobberAndAFishBites(GameTestHelper helper) {
        Villager fisherman = spawnFisherman(helper);
        digPool(helper, 7, 10);
        fisherman.getInventory().addItem(new ItemStack(Items.FISHING_ROD));
        java.util.Set<VillagerBobber.State> seen = java.util.EnumSet.noneOf(VillagerBobber.State.class);
        int[] mostAtOnce = {0};
        helper.onEachTick(() -> {
            var bobbers = helper.getLevel().getEntitiesOfClass(VillagerBobber.class, helper.getBounds(), bobber -> bobber.getOwner() == fisherman);
            mostAtOnce[0] = Math.max(mostAtOnce[0], bobbers.size());
            bobbers.forEach(bobber -> seen.add(bobber.state()));
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(seen.containsAll(java.util.EnumSet.allOf(VillagerBobber.State.class)),
                    "The bobber should fly, float, have a fish approach and bite; saw " + seen);
            helper.assertTrue(catchCount(fisherman) >= 1, "Reeling in after the bite should land a catch");
            helper.assertTrue(mostAtOnce[0] == 1, "A fisherman has at most one bobber out, saw " + mostAtOnce[0]);
        });
    }

    public static void fishermanWithoutARodCatchesNothing(GameTestHelper helper) {
        Villager fisherman = spawnFisherman(helper);
        digPool(helper, 7, 10);

        helper.runAfterDelay(900, () -> {
            helper.assertTrue(catchCount(fisherman) == 0, "Without a rod the fisherman cannot fish");
            helper.succeed();
        });
    }

    public static void fishermanIgnoresWaterBeyondItsRange(GameTestHelper helper) {
        Villager fisherman = spawnFisherman(helper);
        fisherman.setVillagerData(fisherman.getVillagerData().withLevel(5));
        digPool(helper, 15, 10);
        fisherman.getInventory().addItem(new ItemStack(Items.FISHING_ROD));

        helper.runAfterDelay(900, () -> {
            helper.assertTrue(catchCount(fisherman) == 0, "A pond more than 8 blocks from the barrel is out of range, even for a master");
            helper.succeed();
        });
    }

    public static void fishermanUnloadsItsCatchAtHome(GameTestHelper helper) {
        Villager fisherman = spawnFisherman(helper);
        digPool(helper, 7, 10);
        fisherman.getInventory().addItem(new ItemStack(Items.FISHING_ROD));
        fisherman.getInventory().addItem(new ItemStack(Items.COD, 64));
        BlockPos chest = new BlockPos(4, 1, 18);
        helper.setBlock(chest, Blocks.CHEST);
        GameTestHelpers.giveHome(helper, fisherman, new BlockPos(4, 1, 15));

        helper.succeedWhen(() -> {
            var container = helper.getBlockEntity(chest, net.minecraft.world.level.block.entity.ChestBlockEntity.class);
            int cod = 0;
            for (int i = 0; i < container.getContainerSize(); i++) if (container.getItem(i).is(Items.COD)) cod += container.getItem(i).getCount();
            helper.assertTrue(cod >= 64, "The fisherman should carry its catch to its home chest, chest has " + cod + " cod");
            helper.assertTrue(rod(fisherman).is(Items.FISHING_ROD), "It keeps its rod");
        });
    }

    /** A 5x5 pond, one block deep, whose west edge starts at {@code x}. */
    private static void digPool(GameTestHelper helper, int x, int z) {
        for (int dx = 0; dx < 5; dx++) {
            for (int dz = 0; dz < 5; dz++) helper.setBlock(x + dx, 0, z + dz, Blocks.WATER);
        }
    }

    private static int catchCount(Villager fisherman) {
        int count = 0;
        for (ItemStack stack : fisherman.getInventory().getItems()) {
            if (!stack.isEmpty() && !stack.is(Items.FISHING_ROD)) count += stack.getCount();
        }
        return count;
    }

    private static ItemStack rod(Villager fisherman) {
        return fisherman.getInventory().getItems().stream().filter(stack -> stack.is(Items.FISHING_ROD)).findFirst().orElse(ItemStack.EMPTY);
    }

    static Villager spawnFisherman(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(BARREL, Blocks.BARREL);
        return GameTestHelpers.spawnEmployed(helper, BARREL.east(), BARREL, VillagerProfession.FISHERMAN, PoiTypes.FISHERMAN);
    }
}
