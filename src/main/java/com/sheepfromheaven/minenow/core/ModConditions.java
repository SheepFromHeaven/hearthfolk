package com.sheepfromheaven.minenow.core;

import com.mojang.serialization.MapCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public class ModConditions {
    public static final DeferredRegister<MapCodec<? extends ICondition>> CONDITION_CODECS =
            DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, MineNow.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends ICondition>, MapCodec<BellRecipeEnabledCondition>> BELL_RECIPE_ENABLED =
            CONDITION_CODECS.register("bell_recipe_enabled", () -> BellRecipeEnabledCondition.CODEC);

    public static void register(IEventBus modBus) {
        CONDITION_CODECS.register(modBus);
    }
}
