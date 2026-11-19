package com.sheepfromheaven.minenow.gametest;

import com.sheepfromheaven.minenow.MineNowConfig;
import com.sheepfromheaven.minenow.MineNowConfig.Profession;
import com.sheepfromheaven.minenow.WorldSettings;
import com.sheepfromheaven.minenow.villager.IdleReason;
import com.sheepfromheaven.minenow.villager.IdleReport;
import com.sheepfromheaven.minenow.villager.TradeTable;
import com.sheepfromheaven.minenow.villager.WorkXp;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.level.storage.SavedDataStorage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

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

    /** The {@code profession_farmer} environment switches the farmer off for its batch and no other profession. */
    public static void aProfessionEnvironmentSwitchesOnlyItsProfessionOff(GameTestHelper helper) {
        for (Profession profession : Profession.values()) {
            boolean expected = profession != Profession.FARMER;
            helper.assertTrue(MineNowConfig.professionEnabled(profession) == expected,
                    profession.getSerializedName() + (expected ? " stays on" : " is off in its environment"));
        }
        helper.succeed();
    }

    /**
     * Switched off, a villager has no trade table, so villager trading, idle reasons and work experience all leave it alone,
     * while its report still finds it and says so. Switched back on, it trades and earns experience again.
     */
    public static void aSwitchedOffProfessionTakesNoPartInVillagerTrading(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Villager farmer = helper.spawn(EntityTypes.VILLAGER, new BlockPos(2, 1, 2));
        farmer.setVillagerData(farmer.getVillagerData().withProfession(level.registryAccess(), VillagerProfession.FARMER));
        helper.assertTrue(TradeTable.forVillager(farmer).isPresent(), "A farmer trades while its profession is on");
        helper.assertTrue(!IdleReason.of(level, farmer).isEmpty(), "A farmer without a job site is idle while its profession is on");
        int earned = farmer.getVillagerXp();
        WorkXp.award(farmer, 2);
        helper.assertTrue(farmer.getVillagerXp() > earned, "A farmer earns work experience while its profession is on");

        MineNowConfig.set(Profession.FARMER, false);
        try {
            helper.assertTrue(TradeTable.forVillager(farmer).isEmpty(), "A switched-off farmer takes no part in villager trading");
            helper.assertTrue(TradeTable.hasTradeFile(farmer), "...but it still has its trade file, so its report finds it");
            helper.assertTrue(IdleReason.of(level, farmer).isEmpty(), "...is never reported idle");
            String report = IdleReport.describe(level, farmer).stream().map(Component::getString).collect(Collectors.joining("\n"));
            helper.assertTrue(report.contains("Switched off in this world"), "...and its report says it is switched off, got: " + report);
            int offEarned = farmer.getVillagerXp();
            WorkXp.award(farmer, 2);
            helper.assertTrue(farmer.getVillagerXp() == offEarned, "...and its work earns it no experience");
        } finally {
            MineNowConfig.set(Profession.FARMER, true);
        }
        helper.assertTrue(TradeTable.forVillager(farmer).isPresent(), "A farmer trades again once switched back on");
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
