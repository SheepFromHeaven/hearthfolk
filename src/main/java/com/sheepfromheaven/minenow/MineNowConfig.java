package com.sheepfromheaven.minenow;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.EnumMap;
import java.util.Map;

/** Switches in {@code config/minenow-local.toml}, one file for the whole game; edits apply while it runs. */
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

    private static final Map<Feature, ModConfigSpec.BooleanValue> FEATURES = new EnumMap<>(Feature.class);

    static {
        BUILDER.comment("Systems of Hearthfolk that can each be switched off. Off, villagers do as in vanilla there.").push("features");
        for (Feature feature : Feature.values()) {
            FEATURES.put(feature, BUILDER.comment(feature.comment).define(feature.key, true));
        }
        BUILDER.pop();
    }

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

    /** Read live, so a behaviour can ask on every start check. On until the config has loaded. */
    public static boolean enabled(Feature feature) {
        try {
            return FEATURES.get(feature).get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }

    public static void set(Feature feature, boolean on) {
        FEATURES.get(feature).set(on);
    }

    public enum Feature implements StringRepresentable {
        VILLAGERS_DEFEND_THE_VILLAGE("villagersDefendTheVillage",
                "Whether armed villagers fight hostiles, man archer posts and gather when the bell rings. Off: every villager panics and hides as in vanilla, and a bell only warns those within earshot."),
        VILLAGERS_EAT_MEALS("villagersEatMeals",
                "Whether villagers eat two meals a day from their pockets and home chest. Off: they never eat, and no food is spent on meals."),
        VILLAGERS_PAY_EACH_OTHER("villagersPayEachOther",
                "Whether villagers pay emeralds when they buy from each other. Off: villagers take what they need from each other for free. Players still pay."),
        VILLAGERS_FOLLOW_EMERALDS("villagersFollowEmeralds",
                "Whether jobless villagers follow a player holding an emerald, so they can be led to a new home. Off: they ignore it."),
        VILLAGER_CHESTS_LOCKED("villagerChestsLocked",
                "Whether a villager's chests, barrels, armor stands and wall displays are locked for players. Off: players can open and take from them, and taking is not theft."),
        VILLAGERS_PREFER_ROADS("villagersPreferRoads",
                "Whether villagers prefer walking on paths and roads, and stroll along them when idle. Off: they walk as in vanilla."),
        VILLAGERS_LEVEL_THROUGH_WORK("villagersLevelThroughWork",
                "Whether villagers earn experience from their work, not only from trading with players. Off: only trading levels them up, as in vanilla."),
        VILLAGERS_GET_NAMES("villagersGetNames",
                "Whether every villager gets a name, and their births and deaths are announced. Off: new villagers stay nameless as in vanilla.");

        public static final Codec<Feature> CODEC = StringRepresentable.fromEnum(Feature::values);

        private final String key;
        private final String comment;

        Feature(String key, String comment) {
            this.key = key;
            this.comment = comment;
        }

        @Override
        public String getSerializedName() {
            return key;
        }
    }
}
