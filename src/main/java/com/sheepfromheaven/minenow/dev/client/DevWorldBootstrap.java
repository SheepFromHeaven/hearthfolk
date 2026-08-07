package com.sheepfromheaven.minenow.dev.client;

import com.sheepfromheaven.minenow.dev.DevWorldState;
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
 * Dev-only convenience enabled by {@code -Dminenow.devWorld=true} (set on the {@code playtest}
 * Gradle run): as soon as the title screen appears, jumps straight into a fixed superflat
 * playtest world instead of making you click through "Singleplayer" -> "Create New World" every
 * time. If the world already exists it's simply reopened, so in-progress villages persist across
 * runs instead of being recreated.
 */
public final class DevWorldBootstrap {
    private static final String LEVEL_ID = "minenow_playtest";
    private static boolean triggered = false;

    private DevWorldBootstrap() {}

    public static void register() {
        if (!Boolean.getBoolean("minenow.devWorld")) {
            return;
        }
        NeoForge.EVENT_BUS.addListener(ScreenEvent.Init.Post.class, event -> {
            if (triggered || !(event.getScreen() instanceof TitleScreen titleScreen)) {
                return;
            }
            triggered = true;

            var minecraft = Minecraft.getInstance();
            if (minecraft.getLevelSource().levelExists(LEVEL_ID)) {
                minecraft.createWorldOpenFlows().openWorld(LEVEL_ID, () -> {});
            } else {
                DevWorldState.pendingStarterKit = true;
                var levelSettings = new LevelSettings(
                        "MineNow Playtest",
                        GameType.CREATIVE,
                        false,
                        Difficulty.PEACEFUL,
                        true,
                        new GameRules(FeatureFlags.DEFAULT_FLAGS),
                        WorldDataConfiguration.DEFAULT);
                var options = new WorldOptions(0L, false, false);
                minecraft.createWorldOpenFlows().createFreshLevel(
                        LEVEL_ID, levelSettings, options, WorldPresets::createFlatWorldDimensions, titleScreen);
            }
        });
    }
}
