package com.sheepfromheaven.minenow.farmer;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.level.gamerules.GameRules;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;

public final class FarmerRules {
    private FarmerRules() {}

    static boolean isFarmer(Villager villager) {
        return villager.getVillagerData().profession().is(VillagerProfession.FARMER);
    }

    /** Vanilla's HarvestFarmland needs no tool; denying farmers griefing switches it off so only TendFields farms. */
    public static void onMobGriefing(EntityMobGriefingEvent event) {
        if (event.getEntity() instanceof Villager villager && isFarmer(villager)) event.setCanGrief(false);
    }

    static boolean griefingAllowed(ServerLevel level) {
        return level.getGameRules().get(GameRules.MOB_GRIEFING);
    }
}
