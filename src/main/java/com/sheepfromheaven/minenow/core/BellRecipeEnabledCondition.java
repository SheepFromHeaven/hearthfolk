package com.sheepfromheaven.minenow.core;

import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.common.conditions.ICondition;

public record BellRecipeEnabledCondition() implements ICondition {
    public static final MapCodec<BellRecipeEnabledCondition> CODEC =
            MapCodec.unit(BellRecipeEnabledCondition::new);

    @Override
    public boolean test(IContext context) {
        return Config.addBellRecipe;
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }
}
