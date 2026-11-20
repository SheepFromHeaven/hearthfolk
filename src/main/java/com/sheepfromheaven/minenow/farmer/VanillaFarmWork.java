package com.sheepfromheaven.minenow.farmer;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.HarvestFarmland;
import net.minecraft.world.entity.ai.behavior.UseBonemeal;
import net.minecraft.world.entity.ai.behavior.WorkAtComposter;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * Vanilla's farm work, for a farmer whose profession is switched off: wheat baked into bread at the composter, ripe crops
 * harvested without a tool, and bone meal on growing crops. Each one starts and keeps going only while the farmer is not
 * Hearthfolk's, so switching the profession back on stops it and TendFields does that work again.
 */
final class VanillaFarmWork {
    private VanillaFarmWork() {}

    private static boolean applies(Villager villager) {
        return FarmerRules.isFarmer(villager) && !FarmerRules.hearthfolkFarmer(villager);
    }

    static final class Composting extends WorkAtComposter {
        @Override
        protected boolean checkExtraStartConditions(ServerLevel level, Villager body) {
            return applies(body) && super.checkExtraStartConditions(level, body);
        }

        @Override
        protected boolean canStillUse(ServerLevel level, Villager body, long timestamp) {
            return applies(body) && super.canStillUse(level, body, timestamp);
        }
    }

    static final class Harvesting extends HarvestFarmland {
        @Override
        protected boolean checkExtraStartConditions(ServerLevel level, Villager body) {
            return applies(body) && super.checkExtraStartConditions(level, body);
        }

        @Override
        protected boolean canStillUse(ServerLevel level, Villager body, long timestamp) {
            return applies(body) && super.canStillUse(level, body, timestamp);
        }
    }

    static final class Bonemealing extends UseBonemeal {
        @Override
        protected boolean checkExtraStartConditions(ServerLevel level, Villager body) {
            return applies(body) && super.checkExtraStartConditions(level, body);
        }

        @Override
        protected boolean canStillUse(ServerLevel level, Villager body, long timestamp) {
            return applies(body) && super.canStillUse(level, body, timestamp);
        }
    }
}
