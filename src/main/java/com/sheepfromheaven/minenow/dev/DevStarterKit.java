package com.sheepfromheaven.minenow.dev;

import com.sheepfromheaven.minenow.core.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Hands the {@link DevWorldBootstrap} playtest world its starting toolkit the moment the local
 * player first spawns in it - only when {@link DevWorldState#pendingStarterKit} says the world
 * was just created, never on a later reopen of the same save.
 */
public final class DevStarterKit {
    private DevStarterKit() {}

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!DevWorldState.pendingStarterKit) {
            return;
        }
        DevWorldState.pendingStarterKit = false;
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            serverPlayer.getInventory().add(new ItemStack(ModItems.TOWN_BANNER.get()));
            serverPlayer.getInventory().add(new ItemStack(ModItems.STRUCTURE_WAND.get()));
            serverPlayer.getInventory().add(new ItemStack(ModItems.INHABITANT_SPAWN_EGG.get(), 8));
        }
    }
}
