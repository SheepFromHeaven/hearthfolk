package com.sheepfromheaven.minenow.villager;

import com.sheepfromheaven.minenow.MineNowConfig;
import net.minecraft.world.entity.npc.villager.Villager;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** A villager that isn't hungry heals half a heart every 4 seconds, as a well fed player does ({@code villagersHeal}, which also needs meals on). */
public final class Regeneration {
    static final int INTERVAL = 80;

    private Regeneration() {}

    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Villager villager) || villager.level().isClientSide() || !villager.isAlive()
                || villager.tickCount % INTERVAL != 0 || villager.getHealth() >= villager.getMaxHealth()) return;
        if (!MineNowConfig.enabled(MineNowConfig.Feature.VILLAGERS_HEAL) || !Meals.on() || Meals.isHungry(villager)) return;
        villager.heal(1.0F);
    }
}
