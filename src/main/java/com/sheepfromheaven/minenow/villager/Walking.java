package com.sheepfromheaven.minenow.villager;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * Vanilla's work package keeps pulling a villager back within 9 blocks of its job site, replacing its walk target; a
 * behaviour walking somewhere further (a home chest by the bed) re-pins its target whenever this says it was replaced.
 */
public final class Walking {
    private Walking() {}

    public static boolean towards(Villager body, BlockPos pos) {
        return body.getBrain().getMemory(MemoryModuleType.WALK_TARGET).filter(walk -> walk.getTarget().currentBlockPosition().equals(pos)).isPresent();
    }

    public static boolean towards(Villager body, Entity entity) {
        return body.getBrain().getMemory(MemoryModuleType.WALK_TARGET).filter(walk -> walk.getTarget() instanceof EntityTracker tracker && tracker.getEntity() == entity).isPresent();
    }
}
