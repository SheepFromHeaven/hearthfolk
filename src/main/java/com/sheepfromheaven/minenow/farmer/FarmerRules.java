package com.sheepfromheaven.minenow.farmer;

import com.sheepfromheaven.minenow.MineNowConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.level.gamerules.GameRules;
import net.neoforged.neoforge.event.level.BlockEvent;

public final class FarmerRules {
    private FarmerRules() {}

    public static boolean isFarmer(Villager villager) {
        return villager.getVillagerData().profession().is(VillagerProfession.FARMER);
    }

    /**
     * A farmer whose profession is switched on: Hearthfolk tends its field and it never tramples farmland. Switched off, a
     * farmer is vanilla's and does vanilla's farm work ({@link VanillaFarmWork}).
     */
    public static boolean hearthfolkFarmer(Villager villager) {
        return isFarmer(villager) && MineNowConfig.professionEnabled(villager.getVillagerData().profession());
    }

    public static void onEntityJoin(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        RightClickHarvest.onEntityJoin(event);
    }

    public static void onFarmlandTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (event.getEntity() instanceof Villager villager && hearthfolkFarmer(villager)) event.setCanceled(true);
    }

    static boolean griefingAllowed(ServerLevel level) {
        return level.getGameRules().get(GameRules.MOB_GRIEFING);
    }
}
