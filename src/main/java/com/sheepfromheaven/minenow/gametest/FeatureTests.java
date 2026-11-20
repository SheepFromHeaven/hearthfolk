package com.sheepfromheaven.minenow.gametest;

import com.sheepfromheaven.minenow.MineNowConfig;
import com.sheepfromheaven.minenow.MineNowConfig.Feature;
import com.sheepfromheaven.minenow.WorldSettings;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.SavedDataStorage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FeatureTests {
    private FeatureTests() {}

    public static void everyFeatureIsOnByDefault(GameTestHelper helper) {
        for (Feature feature : Feature.values()) {
            helper.assertTrue(MineNowConfig.enabled(feature), feature.getSerializedName() + " is on by default");
        }
        helper.succeed();
    }

    public static void aFeatureEnvironmentSwitchesOnlyItsFeatureOff(GameTestHelper helper) {
        for (Feature feature : Feature.values()) {
            boolean expected = feature != Feature.VILLAGERS_EAT_MEALS;
            helper.assertTrue(MineNowConfig.enabled(feature) == expected,
                    feature.getSerializedName() + (expected ? " stays on" : " is off in its environment"));
        }
        helper.succeed();
    }

    /** The world's own value is what counts and what is saved: flipping it leaves the toml alone, and a save and load keeps it. */
    public static void aSwitchSurvivesASaveAndReload(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Feature feature = Feature.VILLAGERS_GET_NAMES;
        MineNowConfig.set(feature, false);
        try {
            helper.assertTrue(!MineNowConfig.enabled(feature), "A switch set in the world is off");
            helper.assertTrue(!WorldSettings.of(server).enabled(feature), "...in the world's settings");
            helper.assertTrue(MineNowConfig.defaultValue(feature), "...and the toml, the default for new worlds, still has it on");
        } finally {
            MineNowConfig.set(feature, true);
        }

        Path folder = tempFolder();
        WorldSettings settings = WorldSettings.TYPE.constructor().get();
        settings.set(feature, false);
        settings.setMaxMerchantTours(7);
        SavedDataStorage before = new SavedDataStorage(folder, server.getFixerUpper(), server.registryAccess());
        before.set(WorldSettings.TYPE, settings);
        before.saveAndJoin();
        before.close();

        SavedDataStorage after = new SavedDataStorage(folder, server.getFixerUpper(), server.registryAccess());
        WorldSettings loaded = after.get(WorldSettings.TYPE);
        after.close();
        helper.assertTrue(loaded != null, "The settings are saved with the world");
        helper.assertTrue(!loaded.enabled(feature), "A switch turned off stays off after a save and load");
        helper.assertTrue(loaded.enabled(Feature.VILLAGERS_EAT_MEALS), "...and the others stay on");
        helper.assertTrue(loaded.maxMerchantTours() == 7, "...and so does the tour cap, " + loaded.maxMerchantTours());
        helper.succeed();
    }

    /** A world without settings (a new one, or one from before switches were per world) starts from what the toml says. */
    public static void aNewWorldStartsFromTheToml(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Feature feature = Feature.VILLAGERS_PREFER_ROADS;
        boolean merchants = MineNowConfig.TRAVELLING_MERCHANTS.get();
        MineNowConfig.setDefault(feature, false);
        MineNowConfig.TRAVELLING_MERCHANTS.set(false);
        try {
            SavedDataStorage storage = new SavedDataStorage(tempFolder(), server.getFixerUpper(), server.registryAccess());
            WorldSettings fresh = storage.computeIfAbsent(WorldSettings.TYPE);
            storage.close();
            helper.assertTrue(!fresh.enabled(feature), "A new world takes a switch the toml has off");
            helper.assertTrue(fresh.enabled(Feature.VILLAGERS_EAT_MEALS), "...and one it has on");
            helper.assertTrue(!fresh.travellingMerchants(), "...and the older settings too");
            helper.assertTrue(MineNowConfig.enabled(feature), "The running world keeps its own value whatever the toml says");
        } finally {
            MineNowConfig.setDefault(feature, true);
            MineNowConfig.TRAVELLING_MERCHANTS.set(merchants);
        }
        helper.succeed();
    }

    private static Path tempFolder() {
        try {
            Path folder = Files.createTempDirectory("minenow-settings");
            folder.toFile().deleteOnExit();
            return folder;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
