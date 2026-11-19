package com.sheepfromheaven.minenow.gametest;

import com.sheepfromheaven.minenow.MineNowConfig;
import com.sheepfromheaven.minenow.MineNowConfig.Profession;
import com.sheepfromheaven.minenow.WorldSettings;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.level.storage.SavedDataStorage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** The per-world profession switches, kept like the feature switches ({@link FeatureTests}). */
public final class ProfessionTests {
    private ProfessionTests() {}

    public static void everyProfessionIsOnByDefault(GameTestHelper helper) {
        for (Profession profession : Profession.values()) {
            helper.assertTrue(MineNowConfig.professionEnabled(profession), profession.getSerializedName() + " is on by default");
        }
        helper.succeed();
    }

    /** A villager reads its own profession's switch from the running world. */
    public static void aVillagersProfessionSwitchIsTheWorldsValue(GameTestHelper helper) {
        Holder<VillagerProfession> farmer = helper.getLevel().registryAccess().lookupOrThrow(Registries.VILLAGER_PROFESSION)
                .getOrThrow(VillagerProfession.FARMER);
        MineNowConfig.set(Profession.FARMER, false);
        try {
            helper.assertTrue(!MineNowConfig.professionEnabled(farmer), "A farmer is off when its profession is switched off in the world");
            helper.assertTrue(MineNowConfig.defaultValue(Profession.FARMER), "...and the toml, the default for new worlds, still has it on");
        } finally {
            MineNowConfig.set(Profession.FARMER, true);
        }
        helper.assertTrue(MineNowConfig.professionEnabled(farmer), "A farmer is on again once the switch is");
        helper.succeed();
    }

    /** The world's own value is saved with the world, as the feature switches are. */
    public static void aProfessionSwitchSurvivesASaveAndReload(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        WorldSettings settings = WorldSettings.TYPE.constructor().get();
        settings.set(Profession.FARMER, false);
        Path folder = tempFolder();
        SavedDataStorage before = new SavedDataStorage(folder, server.getFixerUpper(), server.registryAccess());
        before.set(WorldSettings.TYPE, settings);
        before.saveAndJoin();
        before.close();

        SavedDataStorage after = new SavedDataStorage(folder, server.getFixerUpper(), server.registryAccess());
        WorldSettings loaded = after.get(WorldSettings.TYPE);
        after.close();
        helper.assertTrue(loaded != null, "The settings are saved with the world");
        helper.assertTrue(!loaded.enabled(Profession.FARMER), "A profession turned off stays off after a save and load");
        helper.succeed();
    }

    /** A world without settings (a new one) starts each profession from what the toml says. */
    public static void aNewWorldStartsProfessionsFromTheToml(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        MineNowConfig.setDefault(Profession.FARMER, false);
        try {
            SavedDataStorage storage = new SavedDataStorage(tempFolder(), server.getFixerUpper(), server.registryAccess());
            WorldSettings fresh = storage.computeIfAbsent(WorldSettings.TYPE);
            storage.close();
            helper.assertTrue(!fresh.enabled(Profession.FARMER), "A new world takes a profession the toml has off");
            helper.assertTrue(MineNowConfig.professionEnabled(Profession.FARMER), "The running world keeps its own value whatever the toml says");
        } finally {
            MineNowConfig.setDefault(Profession.FARMER, true);
        }
        helper.succeed();
    }

    private static Path tempFolder() {
        try {
            Path folder = Files.createTempDirectory("minenow-professions");
            folder.toFile().deleteOnExit();
            return folder;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
