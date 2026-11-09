package com.sheepfromheaven.minenow;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Per-world switches, in {@code serverconfig/minenow-server.toml}. */
public final class MineNowConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue VILLAGERS_SUMMON_GOLEMS = BUILDER
            .comment("Whether villagers summon iron golems as in vanilla. Off by default: the golemsmith builds a village's golems.")
            .define("villagersSummonGolems", false);

    public static final ModConfigSpec.BooleanValue TRAVELLING_MERCHANTS = BUILDER
            .comment("Whether a village's export goods draw travelling merchants that buy them for new emeralds.")
            .define("travellingMerchants", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private MineNowConfig() {}
}
