package com.sheepfromheaven.minenow.dev;

import com.sheepfromheaven.minenow.dominion.townbanner.TownBannerItems;
import com.sheepfromheaven.minenow.structure.StructureItems;
import com.sheepfromheaven.minenow.inhabitant.InhabitantItems;
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
            serverPlayer.getInventory().add(new ItemStack(TownBannerItems.TOWN_BANNER.get()));
            serverPlayer.getInventory().add(new ItemStack(StructureItems.STRUCTURE_WAND.get()));
            serverPlayer.getInventory().add(new ItemStack(InhabitantItems.INHABITANT_SPAWN_EGG.get(), 8));
        }
    }
}
