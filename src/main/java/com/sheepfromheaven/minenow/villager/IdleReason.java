package com.sheepfromheaven.minenow.villager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.ChestBlock;

import java.util.ArrayList;
import java.util.List;

/** What keeps an employed villager from doing its job, for any profession with a trade file. */
public enum IdleReason {
    NO_JOB_SITE("no job site, it hasn't claimed a workstation"),
    NO_TOOL("no tool of its trade, it won't work until it gets one"),
    STORAGE_FULL("its pockets are full and no chest has room, so it can't unload");

    public final String message;

    IdleReason(String message) {
        this.message = message;
    }

    /** Every reason that applies, most important first; empty when nothing is stopping it. */
    public static List<IdleReason> of(ServerLevel level, Villager villager) {
        List<IdleReason> reasons = new ArrayList<>();
        var table = TradeTable.forVillager(villager);
        if (table.isEmpty()) return reasons;
        boolean hasSite = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).filter(site -> site.dimension() == level.dimension()).isPresent();
        if (!hasSite) reasons.add(NO_JOB_SITE);
        if (requiresTool(table.get()) && !Unloading.ownsTool(level, villager)) reasons.add(NO_TOOL);
        if (storageFull(level, villager)) reasons.add(STORAGE_FULL);
        return reasons;
    }

    public static boolean requiresTool(TradeTable table) {
        return table.toolTag().isPresent() || !table.tools().isEmpty();
    }

    private static boolean storageFull(ServerLevel level, Villager villager) {
        if (Unloading.depositableCount(villager) == 0 || Unloading.hasEmptySlot(villager) && Unloading.depositableCount(villager) < Unloading.UNLOAD_AT) return false;
        return !Unloading.chestHasRoomForSurplus(level, villager);
    }
}
