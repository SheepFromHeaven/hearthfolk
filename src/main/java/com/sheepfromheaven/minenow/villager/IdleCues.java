package com.sheepfromheaven.minenow.villager;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Vanilla's "no" head shake for a villager that can't work and has nothing to ask a player for, so players see something
 * is wrong without a screen and work out what: nowhere to unload, nothing to work on, no money for what its trade can't run without, and (even
 * off duty) no workstation. A missing tool or fuel it could pay a player for shows through {@link SeekGoods}.
 */
public final class IdleCues {
    private static final int INTERVAL = 80;
    private static final double PLAYER_RANGE = 24;

    private IdleCues() {}

    public static boolean isWorking(Villager villager) {
        return !villager.isBaby() && TradeTable.forVillager(villager).isPresent() && villager.getBrain().isActive(Activity.WORK);
    }

    private static boolean isAwake(Villager villager) {
        return !villager.isBaby() && !villager.isSleeping() && !villager.isTrading() && TradeTable.forVillager(villager).isPresent();
    }

    public static void onEntityTick(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof Villager villager) || !(villager.level() instanceof ServerLevel level)) return;
        if ((level.getGameTime() + villager.getId()) % INTERVAL != 0 || !isAwake(villager)) return;
        if (level.getNearestPlayer(villager, PLAYER_RANGE) == null) return;
        show(level, villager);
    }

    /** Shows the cue for the villager's most important problem and returns it, or null if nothing is wrong. */
    public static @Nullable IdleReason show(ServerLevel level, Villager villager) {
        if (!isAwake(villager)) return null;
        boolean working = isWorking(villager);
        for (IdleReason reason : IdleReason.of(level, villager)) {
            boolean shakes = switch (reason) {
                case NO_JOB_SITE -> true;
                case STORAGE_FULL, NO_MONEY, NO_WORK_AREA -> working;
                case NO_TOOL, NO_MATERIALS -> false;
            };
            if (shakes) {
                villager.setUnhappy();
                return reason;
            }
        }
        return null;
    }
}
