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
    NO_WORK_AREA("nothing to work on around its workstation (a field, water, animals, a marked mine)"),
    NO_MATERIALS("out of a supply its trade can't run without (fuel) and no villager sells it, only a player can"),
    NO_MONEY("out of a supply its trade can't run without and too broke to pay a player for it"),
    STORAGE_FULL("its pockets are full and no chest has room, so it can't unload");

    public final String message;

    /** Professions say when there is nothing to work on around their job site: no field, no water, no flock, no herd, no marked mine. */
    private static final java.util.Map<net.minecraft.resources.ResourceKey<net.minecraft.world.entity.npc.villager.VillagerProfession>, java.util.function.BiPredicate<ServerLevel, Villager>> NO_WORK_AREA_CHECKS = new java.util.HashMap<>();

    public static void addWorkAreaCheck(net.minecraft.resources.ResourceKey<net.minecraft.world.entity.npc.villager.VillagerProfession> profession,
                                        java.util.function.BiPredicate<ServerLevel, Villager> hasNoWorkArea) {
        NO_WORK_AREA_CHECKS.put(profession, hasNoWorkArea);
    }

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
        if (hasSite && !reasons.contains(NO_TOOL) && villager.getVillagerData().profession().unwrapKey().map(NO_WORK_AREA_CHECKS::get)
                .filter(check -> check.test(level, villager)).isPresent()) reasons.add(NO_WORK_AREA);
        if (!reasons.contains(NO_TOOL) && Shortage.fuel(level, villager).isPresent() && !Shortage.canBuyFromAVillager(level, villager)) {
            reasons.add(NO_MATERIALS);
            if (!Shortage.canPayAPlayer(level, villager)) reasons.add(NO_MONEY);
        }
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
