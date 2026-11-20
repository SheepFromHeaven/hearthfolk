package com.sheepfromheaven.minenow.villager;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** A rack hung before its profession was switched off stays locked as the villager's. */
public final class SwitchedDisplayTests {
    private static final BlockPos RACK = new BlockPos(7, 2, 14);

    private SwitchedDisplayTests() {}

    public static void aSwitchedOffWeaponsmithsEarlierRackStaysLocked(GameTestHelper helper) {
        lockedRack(helper, Blocks.GRINDSTONE, VillagerProfession.WEAPONSMITH, PoiTypes.WEAPONSMITH, new ItemStack(Items.IRON_SWORD));
    }

    public static void aSwitchedOffLeatherworkersEarlierRackStaysLocked(GameTestHelper helper) {
        lockedRack(helper, Blocks.CAULDRON, VillagerProfession.LEATHERWORKER, PoiTypes.LEATHERWORKER, new ItemStack(Items.LEATHER));
    }

    private static void lockedRack(GameTestHelper helper, net.minecraft.world.level.block.Block block, net.minecraft.resources.ResourceKey<VillagerProfession> profession,
                                   net.minecraft.resources.ResourceKey<net.minecraft.world.entity.ai.village.poi.PoiType> poi, ItemStack shown) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        BlockPos station = new BlockPos(5, 1, 12);
        helper.setBlock(station, block);
        Villager villager = GameTestHelpers.spawnEmployed(helper, station.east(), station, profession, poi);
        ItemFrame rack = new ItemFrame(helper.getLevel(), helper.absolutePos(RACK), Direction.WEST);
        rack.setItem(shown.copy(), false);
        rack.setData(WallDisplays.RACKED_BY, villager.getUUID());
        helper.getLevel().addFreshEntity(rack);
        @SuppressWarnings("removal")
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        helper.runAfterDelay(100, () -> {
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.interactOn(rack, InteractionHand.MAIN_HAND, rack.position());
            helper.assertTrue(rack.isAlive() && rack.getItem().is(shown.getItem()), "Expected the rack still showing " + shown.getItem() + ", shows " + rack.getItem());
            helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty(), "Expected nothing handed to the player");
            helper.succeed();
        });
    }
}
