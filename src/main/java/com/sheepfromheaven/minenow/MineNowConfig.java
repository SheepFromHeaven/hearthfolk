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

    public static final ModConfigSpec.BooleanValue INK_AND_QUILL_RECIPES = BUILDER
            .comment("Whether players can craft the cartographer's ink and quill (feather, ink sac and glass bottle), and whether a book and quill takes it in place of an ink sac and a feather. Off by default: vanilla's book and quill recipe stays as it is.")
            .define("inkAndQuillRecipes", false);

    public static final ModConfigSpec.IntValue MAX_TOURS = BUILDER
            .comment("How many merchant tours may run at once in a world. A touring merchant keeps the chunks around it ticking, so each tour costs a few loaded chunks; a merchant over the cap waits at home.")
            .defineInRange("maxMerchantTours", 4, 0, 64);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private MineNowConfig() {}

    /** Safe before the world's config has loaded (recipes are shown to the client early): off then. */
    public static boolean inkAndQuillRecipes() {
        try {
            return INK_AND_QUILL_RECIPES.get();
        } catch (IllegalStateException notLoaded) {
            return false;
        }
    }
}
