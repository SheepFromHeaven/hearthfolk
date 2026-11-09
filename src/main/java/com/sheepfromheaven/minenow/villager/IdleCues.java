package com.sheepfromheaven.minenow.villager;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Vanilla-style cues for a villager that should be working but can't, so players see why without a screen: a head shake
 * (vanilla's "no") when it has nowhere to unload. A missing tool shows through {@link SeekTool}.
 */
public final class IdleCues {
    private static final int INTERVAL = 80;
    private static final double PLAYER_RANGE = 24;

    private IdleCues() {}

    public static boolean isWorking(Villager villager) {
        return !villager.isBaby() && TradeTable.forVillager(villager).isPresent() && villager.getBrain().isActive(Activity.WORK);
    }

    public static void onEntityTick(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof Villager villager) || !(villager.level() instanceof ServerLevel level)) return;
        if ((level.getGameTime() + villager.getId()) % INTERVAL != 0 || !isWorking(villager)) return;
        if (level.getNearestPlayer(villager, PLAYER_RANGE) == null) return;
        show(level, villager);
    }

    /** Shows the cue for the villager's most important problem and returns it, or null if nothing is wrong. */
    public static @Nullable IdleReason show(ServerLevel level, Villager villager) {
        List<IdleReason> reasons = IdleReason.of(level, villager);
        for (IdleReason reason : reasons) {
            switch (reason) {
                case STORAGE_FULL -> {
                    villager.setUnhappy();
                    return reason;
                }
                case NO_TOOL, NO_JOB_SITE -> {}
            }
        }
        return null;
    }
}
