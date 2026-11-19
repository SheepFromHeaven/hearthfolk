package com.sheepfromheaven.minenow.gametest;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sheepfromheaven.minenow.MineNow;
import com.sheepfromheaven.minenow.MineNowConfig;
import com.sheepfromheaven.minenow.MineNowConfig.Feature;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * The {@code minenow:feature_off} test environment: one switch off for its whole batch, so no test of
 * another batch sees it off and none in the batch sees it flipped back.
 */
public record FeatureOff(Feature feature) implements TestEnvironmentDefinition<Boolean> {
    public static final MapCodec<FeatureOff> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Feature.CODEC.fieldOf("feature").forGetter(FeatureOff::feature)
    ).apply(i, FeatureOff::new));

    public static void register(RegisterEvent event) {
        event.register(Registries.TEST_ENVIRONMENT_DEFINITION_TYPE,
                Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "feature_off"), () -> CODEC);
    }

    @Override
    public Boolean setup(ServerLevel level) {
        boolean before = MineNowConfig.enabled(feature);
        MineNowConfig.set(feature, false);
        return before;
    }

    @Override
    public void teardown(ServerLevel level, Boolean before) {
        MineNowConfig.set(feature, before);
    }

    @Override
    public MapCodec<FeatureOff> codec() {
        return CODEC;
    }
}
