package com.sheepfromheaven.minenow.farmer;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Before breaking a ripe crop, the farmer right-clicks it through a fake player, so whatever installed mods do on a
 * right-click (Right Click Harvest, Quark, crops harvested that way natively) happens for villagers too. Items the
 * click drops or hands the fake player go to the farmer. Land-claim mods see a player and can refuse.
 */
final class RightClickHarvest {
    private static final GameProfile PROFILE = new GameProfile(UUID.fromString("6d696e65-6e6f-7746-6172-6d6572000001"), "[MineNow Farmer]");
    private static final double CAPTURE_RADIUS = 2.0;

    private record Capture(ServerLevel level, Vec3 center, List<ItemStack> items) {}

    private static @Nullable Capture capture;

    private RightClickHarvest() {}

    /** @return true when the right-click harvested the crop; the caller then skips breaking it. */
    static boolean tryHarvest(ServerLevel level, Villager farmer, BlockPos pos) {
        BlockState before = level.getBlockState(pos);
        FakePlayer player = FakePlayerFactory.get(level, PROFILE);
        player.getInventory().clearContent();
        player.snapTo(farmer.getX(), farmer.getY(), farmer.getZ(), farmer.getYRot(), farmer.getXRot());
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

        List<ItemStack> gained = new ArrayList<>();
        capture = new Capture(level, Vec3.atCenterOf(pos), gained);
        try {
            player.gameMode.useItemOn(player, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        } finally {
            capture = null;
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) gained.add(stack.copy());
        }
        player.getInventory().clearContent();

        boolean harvested = !gained.isEmpty() || !level.getBlockState(pos).equals(before);
        if (!harvested) return false;
        for (ItemStack stack : gained) {
            ItemStack rest = farmer.getInventory().addItem(stack);
            if (!rest.isEmpty()) Block.popResource(level, pos, rest);
        }
        return true;
    }

    static void onEntityJoin(EntityJoinLevelEvent event) {
        Capture active = capture;
        if (active == null || event.getLevel() != active.level() || !(event.getEntity() instanceof ItemEntity item)) return;
        if (item.position().distanceTo(active.center()) > CAPTURE_RADIUS) return;
        active.items().add(item.getItem().copy());
        event.setCanceled(true);
    }
}
