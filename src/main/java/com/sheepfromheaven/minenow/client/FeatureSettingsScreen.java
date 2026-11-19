package com.sheepfromheaven.minenow.client;

import com.sheepfromheaven.minenow.MineNowConfig;
import com.sheepfromheaven.minenow.MineNowConfig.Feature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Hearthfolk's settings, opened from the mod list. In a single player world it edits that world's values, from the main
 * menu the toml's defaults for new worlds; connected to a server it only shows what this game's defaults are, since the
 * world's values live on the server.
 */
public final class FeatureSettingsScreen extends OptionsSubScreen {
    private enum Target { WORLD, DEFAULTS, REMOTE }

    private final Target target;

    public FeatureSettingsScreen(Screen lastScreen) {
        this(lastScreen, target(Minecraft.getInstance()));
    }

    private FeatureSettingsScreen(Screen lastScreen, Target target) {
        super(lastScreen, Minecraft.getInstance().options,
                Component.translatable("minenow.settings.title." + target.name().toLowerCase(java.util.Locale.ROOT)));
        this.target = target;
    }

    private static Target target(Minecraft minecraft) {
        if (minecraft.getSingleplayerServer() != null) return Target.WORLD;
        return minecraft.level == null ? Target.DEFAULTS : Target.REMOTE;
    }

    @Override
    protected void addOptions() {
        if (target == Target.REMOTE) list.addHeader(Component.translatable("minenow.settings.remote"));
        list.addHeader(Component.translatable("minenow.settings.section.village"));
        List<AbstractWidget> village = new ArrayList<>();
        for (Feature feature : Feature.values()) {
            village.add(toggle(feature.getSerializedName(), MineNowConfig.enabled(feature), on -> MineNowConfig.set(feature, on)));
        }
        village.add(toggle("travellingMerchants", MineNowConfig.travellingMerchants(), MineNowConfig::setTravellingMerchants));
        village.add(toggle("villagersSummonGolems", MineNowConfig.villagersSummonGolems(), MineNowConfig::setVillagersSummonGolems));
        village.add(toggle("inkAndQuillRecipes", MineNowConfig.inkAndQuillRecipes(), MineNowConfig::setInkAndQuillRecipes));
        village.add(number("maxMerchantTours", MineNowConfig.maxMerchantTours(), 0, 64, MineNowConfig::setMaxMerchantTours));
        list.addSmall(village);
    }

    private AbstractWidget toggle(String key, boolean value, Consumer<Boolean> setter) {
        CycleButton<Boolean> button = CycleButton.onOffBuilder(value)
                .withTooltip(on -> tooltip(key))
                .create(name(key), (b, on) -> apply(() -> setter.accept(on)));
        button.active = target != Target.REMOTE;
        return button;
    }

    private AbstractWidget number(String key, int value, int min, int max, IntConsumer setter) {
        OptionInstance<Integer> option = new OptionInstance<>("minenow.settings." + key, OptionInstance.cachedConstantTooltip(tooltipText(key)),
                Options::genericValueLabel, new OptionInstance.IntRange(min, max), value, tours -> apply(() -> setter.accept(tours)));
        AbstractWidget slider = option.createButton(options);
        slider.active = target != Target.REMOTE;
        return slider;
    }

    /** The world's values are the server thread's, so a change in a world is handed to it; the defaults are written to the toml at once. */
    private void apply(Runnable change) {
        switch (target) {
            case WORLD -> {
                IntegratedServer server = minecraft.getSingleplayerServer();
                if (server != null) server.execute(change);
            }
            case DEFAULTS -> {
                change.run();
                MineNowConfig.SPEC.save();
            }
            case REMOTE -> {}
        }
    }

    private static Component name(String key) {
        return Component.translatable("minenow.settings." + key);
    }

    private static Component tooltipText(String key) {
        return Component.translatable("minenow.settings." + key + ".tooltip");
    }

    private static Tooltip tooltip(String key) {
        return Tooltip.create(tooltipText(key));
    }
}
