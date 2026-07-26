package com.sheepfromheaven.minenow.block;

import com.sheepfromheaven.minenow.client.DominionScreen;
import com.sheepfromheaven.minenow.dominion.DominionSavedData;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

final class TownBannerBehavior {
    private TownBannerBehavior() {}

    static void onPlaced(Level level, BlockPos pos, @Nullable LivingEntity placer) {
        if (level instanceof ServerLevel serverLevel && placer instanceof Player player) {
            var data = DominionSavedData.get(serverLevel);
            var dominion = data.createDominion(player.getUUID(), player.getName().getString() + "'s Settlement", pos);
            player.displayClientMessage(Component.literal("Founded " + dominion.getName() + "!"), false);

            if (level.getBlockEntity(pos) instanceof TownBannerBlockEntity be) {
                be.setDominionData(dominion.getName(), dominion.getTier());
            }
        }
    }

    static InteractionResult onUse(Level level, BlockPos pos, Player player) {
        if (level.isClientSide()) {
            if (level.getBlockEntity(pos) instanceof TownBannerBlockEntity be && !be.getDominionName().isEmpty()) {
                Minecraft.getInstance().setScreen(new DominionScreen(be.getDominionName(), be.getDominionTier(), pos));
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.SUCCESS;
    }

    static void onDestroyed(Level level, BlockPos pos, Player player) {
        if (level instanceof ServerLevel serverLevel) {
            var data = DominionSavedData.get(serverLevel);
            var dominion = data.getDominionAt(pos);
            if (dominion != null) {
                data.removeDominion(dominion.getId());
                player.displayClientMessage(Component.literal(dominion.getName() + " has been disbanded."), false);
            }
        }
    }
}
