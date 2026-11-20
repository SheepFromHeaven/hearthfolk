package com.sheepfromheaven.minenow.gametest;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sheepfromheaven.minenow.MineNow;
import com.sheepfromheaven.minenow.MineNowConfig;
import com.sheepfromheaven.minenow.MineNowConfig.Profession;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * The {@code minenow:profession_off} test environment: one profession switched off for its whole batch, as
 * {@link FeatureOff} does for a feature, so no test of another batch sees it off.
 */
public record ProfessionOff(Profession profession) implements TestEnvironmentDefinition<Boolean> {
    public static final MapCodec<ProfessionOff> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Profession.CODEC.fieldOf("profession").forGetter(ProfessionOff::profession)
    ).apply(i, ProfessionOff::new));

    public static void register(RegisterEvent event) {
        event.register(Registries.TEST_ENVIRONMENT_DEFINITION_TYPE,
                Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "profession_off"), () -> CODEC);
    }

    @Override
    public Boolean setup(ServerLevel level) {
        boolean before = MineNowConfig.professionEnabled(profession);
        MineNowConfig.set(profession, false);
        return before;
    }

    @Override
    public void teardown(ServerLevel level, Boolean before) {
        MineNowConfig.set(profession, before);
    }

    @Override
    public MapCodec<ProfessionOff> codec() {
        return CODEC;
    }
}
