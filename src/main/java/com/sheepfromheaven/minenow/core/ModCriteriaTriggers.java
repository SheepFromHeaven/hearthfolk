package com.sheepfromheaven.minenow.core;

import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.criterion.PlayerTrigger;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Custom advancement triggers for milestones that have no vanilla criterion to hook into -
 * immigration events and dominion tier growth are pure mod game-state transitions.
 */
public class ModCriteriaTriggers {
    public static final DeferredRegister<CriterionTrigger<?>> TRIGGERS =
            DeferredRegister.create(Registries.TRIGGER_TYPE, MineNow.MOD_ID);

    public static final DeferredHolder<CriterionTrigger<?>, PlayerTrigger> ATTRACTED_WANDERER =
            TRIGGERS.register("attracted_wanderer", PlayerTrigger::new);

    public static final DeferredHolder<CriterionTrigger<?>, PlayerTrigger> ACCEPTED_NEWCOMER =
            TRIGGERS.register("accepted_newcomer", PlayerTrigger::new);

    public static final DeferredHolder<CriterionTrigger<?>, PlayerTrigger> INVITED_VILLAGER =
            TRIGGERS.register("invited_villager", PlayerTrigger::new);

    public static final DeferredHolder<CriterionTrigger<?>, PlayerTrigger> DOMINION_TIER_SETTLEMENT =
            TRIGGERS.register("dominion_tier_settlement", PlayerTrigger::new);

    public static final DeferredHolder<CriterionTrigger<?>, PlayerTrigger> DOMINION_TIER_VILLAGE =
            TRIGGERS.register("dominion_tier_village", PlayerTrigger::new);

    public static final DeferredHolder<CriterionTrigger<?>, PlayerTrigger> DOMINION_TIER_TOWN =
            TRIGGERS.register("dominion_tier_town", PlayerTrigger::new);

    public static final DeferredHolder<CriterionTrigger<?>, PlayerTrigger> DOMINION_TIER_CITY =
            TRIGGERS.register("dominion_tier_city", PlayerTrigger::new);

    public static void register(IEventBus modBus) {
        TRIGGERS.register(modBus);
    }
}
