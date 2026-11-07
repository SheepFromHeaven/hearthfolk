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

    static Villager spawnFisherman(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(BARREL, Blocks.BARREL);
        return GameTestHelpers.spawnEmployed(helper, BARREL.east(), BARREL, VillagerProfession.FISHERMAN, PoiTypes.FISHERMAN);
    }
}
