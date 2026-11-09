package com.sheepfromheaven.minenow.client;

import com.sheepfromheaven.minenow.dev.GametestPlayground;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.io.IOException;
import java.io.UncheckedIOException;

/** For the {@code gametestPlayground} run: skips the title screen into a fresh creative test world, on easy so tests can spawn hostiles (natural mob spawning is off, see GametestPlayground). */
final class GametestPlaygroundWorld {
    private static final String LEVEL_ID = "minenow_gametest_playground";
    private static boolean triggered;

    private GametestPlaygroundWorld() {}

    static void onScreenInit(ScreenEvent.Init.Post event) {
        if (triggered || !GametestPlayground.enabled() || !(event.getScreen() instanceof TitleScreen titleScreen)) return;
        triggered = true;
        Minecraft minecraft = Minecraft.getInstance();
        var levels = minecraft.getLevelSource();
        if (levels.levelExists(LEVEL_ID)) {
            try (var access = levels.createAccess(LEVEL_ID)) {
                access.deleteLevel();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        var settings = new LevelSettings("MineNow Gametest Playground", GameType.CREATIVE,
                new LevelSettings.DifficultySettings(Difficulty.EASY, false, false), true, WorldDataConfiguration.DEFAULT);
        minecraft.createWorldOpenFlows().createFreshLevel(LEVEL_ID, settings, new WorldOptions(0L, false, false),
                WorldPresets::createTestWorldDimensions, titleScreen);
    }
}
