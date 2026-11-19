package com.sheepfromheaven.minenow.villager;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * A bed a player has used is the player's: its sleeping ticket is taken, so no villager can claim it, and a villager
 * that already holds it gives it up. Villagers claim beds through the same ticket vanilla hands out ({@code HOME}
 * point of interest), the way {@link HomeStorage} keeps a barrel from being taken as a workplace. A player's respawn
 * bed counts too, checked at login, so beds claimed before this existed are covered.
 */
public final class PlayerBeds {
    private static final int SEARCH_RADIUS = 64;

    private PlayerBeds() {}

    public static void onCanSleep(CanPlayerSleepEvent event) {
        if (event.getLevel() instanceof ServerLevel level) claim(level, event.getPos());
    }

    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.getRespawnConfig() == null) return;
        var respawn = player.getRespawnConfig().respawnData();
        ServerLevel level = player.level().getServer().getLevel(respawn.dimension());
        if (level != null) claim(level, respawn.pos());
    }

    static void claim(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BedBlock)) return;
        BlockPos head = state.getValue(BedBlock.PART) == BedPart.HEAD ? pos : pos.relative(state.getValue(BedBlock.FACING));
        PoiManager pois = level.getPoiManager();
        if (!pois.exists(head, type -> type.is(PoiTypes.HOME))) return;
        var holders = level.getEntitiesOfClass(Villager.class, new AABB(head).inflate(SEARCH_RADIUS),
                villager -> villager.getBrain().getMemory(MemoryModuleType.HOME).filter(home -> home.dimension() == level.dimension() && home.pos().equals(head)).isPresent());
        for (Villager holder : holders) holder.getBrain().eraseMemory(MemoryModuleType.HOME);
        if (!holders.isEmpty() && taken(pois, head)) pois.release(head);
        if (!taken(pois, head)) pois.take(type -> type.is(PoiTypes.HOME), (type, at) -> at.equals(head), head, 1);
    }

    private static boolean taken(PoiManager pois, BlockPos head) {
        return pois.getCountInRange(type -> type.is(PoiTypes.HOME), head, 0, PoiManager.Occupancy.IS_OCCUPIED) > 0;
    }
}
