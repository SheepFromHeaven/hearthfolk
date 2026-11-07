package com.sheepfromheaven.minenow.dev.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.Difficulty;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Dev-only convenience enabled by {@code -Dminenow.gametestPlayground=true} (set on the
 * {@code gametestPlayground} Gradle run): as soon as the title screen appears, jumps straight
 * into a fixed superflat, peaceful, creative world dedicated to eyeballing gametests in bulk -
 * separate from {@link DevWorldBootstrap}'s normal-terrain {@code minenow_playtest} world, so
 * that one stays untouched for real gameplay testing. On every login into this world, all
 * {@code minenow:*} test instances are run via {@code /test runmultiple minenow:*}, see
 * {@link com.sheepfromheaven.minenow.dev.GametestPlaygroundRunner}. Since gametests populate the
 * world with leftover test structures/entities every run, the previous save is deleted and a fresh
 * one created every time the playground launches, rather than reopening the old one.
 */
public final class GametestPlaygroundBootstrap {
    private static final String LEVEL_ID = "minenow_gametest_playground";
    private static boolean triggered = false;

    private GametestPlaygroundBootstrap() {}

    public static void register() {
        if (!Boolean.getBoolean("minenow.gametestPlayground")) {
            return;
        }
        NeoForge.EVENT_BUS.addListener(ScreenEvent.Init.Post.class, event -> {
            if (triggered || !(event.getScreen() instanceof TitleScreen titleScreen)) {
                return;
            }
            triggered = true;

            var minecraft = Minecraft.getInstance();
            var levelSource = minecraft.getLevelSource();
            if (levelSource.levelExists(LEVEL_ID)) {
                try (var access = levelSource.createAccess(LEVEL_ID)) {
                    access.deleteLevel();
                } catch (java.io.IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            }
            var levelSettings = new LevelSettings(
                    "MineNow Gametest Playground",
                    GameType.CREATIVE,
                    false,
                    Difficulty.PEACEFUL,
                    true,
                    new GameRules(FeatureFlags.DEFAULT_FLAGS),
                    WorldDataConfiguration.DEFAULT);
            var options = new WorldOptions(0L, false, false);
            minecraft.createWorldOpenFlows().createFreshLevel(
                    LEVEL_ID, levelSettings, options, WorldPresets::createFlatWorldDimensions, titleScreen);
        });
    }
}
