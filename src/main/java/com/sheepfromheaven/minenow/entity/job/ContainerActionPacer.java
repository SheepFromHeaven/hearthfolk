package com.sheepfromheaven.minenow.entity.job;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;

/**
 * Paces repeated transfers to/from the same container to about one per second, so grabbing a
 * whole stack reads as a deliberate action rather than an instant swap. The cooldown is scoped to
 * whichever container was last acted on: switching to a different container (e.g. walking from
 * the barrel to the smoker) always acts immediately, since that's a fresh interaction rather than
 * a rapid-fire repeat on the same one.
 */
public final class ContainerActionPacer {
    private static final int COOLDOWN_TICKS = 20;

    private int cooldown;
    @Nullable
    private BlockPos lastContainer;

    /** Call before acting on {@code target}; returns true once its cooldown (if any) has elapsed. */
    public boolean isReady(BlockPos target) {
        if (!target.equals(lastContainer)) {
            cooldown = 0;
            lastContainer = target;
        }
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        return true;
    }

    /** Call right after a successful transfer to start the pacing window for {@code target}. */
    public void recordAction(BlockPos target) {
        lastContainer = target;
        cooldown = COOLDOWN_TICKS;
    }

    public void save(CompoundTag tag, String prefix) {
        tag.putInt(prefix + "Cooldown", cooldown);
        if (lastContainer != null) tag.putLong(prefix + "LastContainer", lastContainer.asLong());
    }

    public void load(CompoundTag tag, String prefix) {
        cooldown = tag.getIntOr(prefix + "Cooldown", 0);
        lastContainer = tag.contains(prefix + "LastContainer") ? BlockPos.of(tag.getLongOr(prefix + "LastContainer", 0)) : null;
    }
}
