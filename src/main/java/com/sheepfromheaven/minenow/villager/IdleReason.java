package com.sheepfromheaven.minenow.villager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.ChestBlock;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.Map;
import java.util.HashMap;

/** What keeps an employed villager from doing its job, for any profession with a trade file. */
public enum IdleReason {
    NO_JOB_SITE("no job site, it hasn't claimed a workstation"),
    NO_TOOL("no tool of its trade, it won't work until it gets one"),
    NO_WORK_AREA("nothing to work on around its workstation (a field, water, animals, a marked mine)"),
    NO_MATERIALS("out of fuel for what it has to cook and no villager sells it, only a player can"),
    NO_MONEY("out of a supply its trade can't run without and too broke to pay a player for it"),
    STORAGE_FULL("its pockets are full and no chest has room, so it can't unload");

    public final String message;

    /** Professions say when there is nothing to work on around their job site: no field, no water, no flock, no herd, no marked mine. */
    private static final java.util.Map<net.minecraft.resources.ResourceKey<net.minecraft.world.entity.npc.villager.VillagerProfession>, java.util.function.BiPredicate<ServerLevel, Villager>> NO_WORK_AREA_CHECKS = new java.util.HashMap<>();
    private static final java.util.Map<net.minecraft.resources.ResourceKey<net.minecraft.world.entity.npc.villager.VillagerProfession>, net.minecraft.world.item.Item> NO_WORK_AREA_ICONS = new java.util.HashMap<>();

    public static void addWorkAreaCheck(net.minecraft.resources.ResourceKey<net.minecraft.world.entity.npc.villager.VillagerProfession> profession,
                                        java.util.function.BiPredicate<ServerLevel, Villager> hasNoWorkArea,
                                        net.minecraft.world.item.Item icon) {
        NO_WORK_AREA_CHECKS.put(profession, hasNoWorkArea);
        NO_WORK_AREA_ICONS.put(profession, icon);
    }

    /** What the profession's work needs, shown in the speech bubble when it has no work area; null if it has none registered. */
    static net.minecraft.world.item.Item workAreaIcon(Villager villager) {
        return villager.getVillagerData().profession().unwrapKey().map(NO_WORK_AREA_ICONS::get).orElse(null);
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
        if (needsToolNow(level, villager)) reasons.add(NO_TOOL);
        if (hasSite && !reasons.contains(NO_TOOL) && villager.getVillagerData().profession().unwrapKey().map(NO_WORK_AREA_CHECKS::get)
                .filter(check -> check.test(level, villager)).isPresent()) reasons.add(NO_WORK_AREA);
        if (!reasons.contains(NO_TOOL) && Shortage.fuel(level, villager).isPresent() && !Shortage.canBuyFromAVillager(level, villager)) {
            reasons.add(NO_MATERIALS);
            if (!Shortage.canPayAPlayer(level, villager)) reasons.add(NO_MONEY);
        }
        if (storageFull(level, villager)) reasons.add(STORAGE_FULL);
        return reasons;
    }

    /**
     * What a profession with several tasks wants begged for: it only begs when {@code blocked}, that is when it wants to do
     * something and holds what none of those things needs, and then for the tool only if {@code toolWanted} and for fuel only
     * if {@code fuelWanted}. What it buys never depends on this: it stocks its tool and its fuel whatever the task at hand.
     * Professions that don't register are always blocked and want both, so they beg as soon as they lack either.
     */
    public record Wants(BiPredicate<ServerLevel, Villager> blocked, BiPredicate<ServerLevel, Villager> toolWanted, BiPredicate<ServerLevel, Villager> fuelWanted) {}

    private static final Map<ResourceKey<VillagerProfession>, Wants> WANTS = new HashMap<>();

    public static void addWants(ResourceKey<VillagerProfession> profession, Wants wants) {
        WANTS.put(profession, wants);
    }

    private static boolean ask(ServerLevel level, Villager villager, java.util.function.Function<Wants, BiPredicate<ServerLevel, Villager>> which) {
        return villager.getVillagerData().profession().unwrapKey().map(WANTS::get).map(wants -> which.apply(wants).test(level, villager)).orElse(true);
    }

    /** It has no tool of its trade, whether or not it needs one at the moment. */
    public static boolean lacksTool(ServerLevel level, Villager villager) {
        return TradeTable.forVillager(villager).filter(IdleReason::requiresTool).isPresent() && !Unloading.ownsTool(level, villager);
    }

    /** It lacks its tool and nothing else it could do is left: only then does it ask a player for one. */
    public static boolean needsToolNow(ServerLevel level, Villager villager) {
        return lacksTool(level, villager) && ask(level, villager, Wants::blocked) && ask(level, villager, Wants::toolWanted);
    }

    /** Nothing else it could do is left and cooking is among what it wants to do. */
    static boolean needsFuelNow(ServerLevel level, Villager villager) {
        return ask(level, villager, Wants::blocked) && ask(level, villager, Wants::fuelWanted);
    }

    public static boolean requiresTool(TradeTable table) {
        return table.toolTag().isPresent() || !table.tools().isEmpty();
    }

    private static boolean storageFull(ServerLevel level, Villager villager) {
        if (Unloading.depositableCount(villager) == 0 || Unloading.hasEmptySlot(villager) && Unloading.depositableCount(villager) < Unloading.UNLOAD_AT) return false;
        return !Unloading.chestHasRoomForSurplus(level, villager);
    }
}
