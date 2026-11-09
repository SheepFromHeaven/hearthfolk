package com.sheepfromheaven.minenow.villager;

import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * Replaces what vanilla's hostile sensor wrote into {@code NEAREST_HOSTILE} with what the villager sees by {@link Sight}
 * (height and light), every tick and ahead of everything else in CORE, so panic, fighting and hiding all react to that.
 */
public class Eyes extends Behavior<Villager> {
    public Eyes() {
        super(ImmutableMap.of());
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager body) {
        LivingEntity seen = Sight.spotted(level, body);
        var brain = body.getBrain();
        if (seen == null) brain.eraseMemory(MemoryModuleType.NEAREST_HOSTILE);
        else if (brain.getMemory(MemoryModuleType.NEAREST_HOSTILE).filter(seen::equals).isEmpty()) brain.setMemory(MemoryModuleType.NEAREST_HOSTILE, seen);
        return false;
    }
}
