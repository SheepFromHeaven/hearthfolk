package com.sheepfromheaven.minenow.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sheepfromheaven.minenow.MineNow;
import com.sheepfromheaven.minenow.MineNowConfig;
import com.sheepfromheaven.minenow.MineNowConfig.Feature;
import com.sheepfromheaven.minenow.WorldSettings;
import net.minecraft.gametest.framework.GameTestHelper;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** What {@code client/FeatureSettingsScreen} relies on, testable without a client. */
public final class SettingsMenuTests {
    private SettingsMenuTests() {}

    public static void everySettingHasANameAndTooltip(GameTestHelper helper) {
        JsonObject lang = lang();
        List<String> keys = new ArrayList<>();
        for (Feature feature : Feature.values()) keys.add(feature.getSerializedName());
        keys.addAll(List.of("travellingMerchants", "villagersSummonGolems", "inkAndQuillRecipes", "maxMerchantTours"));
        for (String key : keys) {
            helper.assertTrue(lang.has("minenow.settings." + key), key + " has a readable name");
            helper.assertTrue(lang.has("minenow.settings." + key + ".tooltip"), key + " has a tooltip");
        }
        for (String key : List.of("title.world", "title.defaults", "title.remote", "remote", "section.village")) {
            helper.assertTrue(lang.has("minenow.settings." + key), key + " has a text");
        }
        helper.succeed();
    }

    /** In a world the screen's setters change that world's value and leave the toml, the default for new worlds, alone. */
    public static void theMenusSettersChangeOnlyTheWorld(GameTestHelper helper) {
        WorldSettings world = WorldSettings.of(helper.getLevel().getServer());
        boolean merchants = world.travellingMerchants();
        boolean golems = world.villagersSummonGolems();
        boolean ink = world.inkAndQuillRecipes();
        int tours = world.maxMerchantTours();
        boolean tomlMerchants = MineNowConfig.TRAVELLING_MERCHANTS.get();
        boolean tomlGolems = MineNowConfig.VILLAGERS_SUMMON_GOLEMS.get();
        boolean tomlInk = MineNowConfig.INK_AND_QUILL_RECIPES.get();
        int tomlTours = MineNowConfig.MAX_TOURS.get();
        try {
            MineNowConfig.set(Feature.VILLAGERS_FOLLOW_EMERALDS, false);
            MineNowConfig.setTravellingMerchants(!merchants);
            MineNowConfig.setVillagersSummonGolems(!golems);
            MineNowConfig.setInkAndQuillRecipes(!ink);
            MineNowConfig.setMaxMerchantTours(tours + 3);

            helper.assertTrue(!world.enabled(Feature.VILLAGERS_FOLLOW_EMERALDS), "A feature flipped in the menu is off in the world");
            helper.assertTrue(MineNowConfig.defaultValue(Feature.VILLAGERS_FOLLOW_EMERALDS), "...and still on in the toml");
            helper.assertTrue(world.travellingMerchants() != merchants && MineNowConfig.TRAVELLING_MERCHANTS.get() == tomlMerchants, "Travelling merchants change in the world only");
            helper.assertTrue(world.villagersSummonGolems() != golems && MineNowConfig.VILLAGERS_SUMMON_GOLEMS.get() == tomlGolems, "Golem summoning changes in the world only");
            helper.assertTrue(world.inkAndQuillRecipes() != ink && MineNowConfig.INK_AND_QUILL_RECIPES.get() == tomlInk, "Ink and quill recipes change in the world only");
            helper.assertTrue(world.maxMerchantTours() == tours + 3 && MineNowConfig.MAX_TOURS.get() == tomlTours, "The tour cap changes in the world only");
            helper.assertTrue(MineNowConfig.maxMerchantTours() == tours + 3, "...and is what the accessor reads, " + MineNowConfig.maxMerchantTours());
        } finally {
            MineNowConfig.set(Feature.VILLAGERS_FOLLOW_EMERALDS, true);
            MineNowConfig.setTravellingMerchants(merchants);
            MineNowConfig.setVillagersSummonGolems(golems);
            MineNowConfig.setInkAndQuillRecipes(ink);
            MineNowConfig.setMaxMerchantTours(tours);
        }
        helper.succeed();
    }

    private static JsonObject lang() {
        try (InputStream in = MineNow.class.getResourceAsStream("/assets/minenow/lang/en_us.json")) {
            if (in == null) throw new IllegalStateException("en_us.json not found");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
