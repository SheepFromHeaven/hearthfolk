package com.sheepfromheaven.minenow;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sheepfromheaven.minenow.MineNowConfig.Feature;
import com.sheepfromheaven.minenow.MineNowConfig.Profession;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A world's own switches, saved with it. A world without them (a new one, or one from before switches were per world) starts
 * from the toml's current values, and so does a switch added since the world was last saved.
 */
public final class WorldSettings extends SavedData {
    static final String SUMMON_GOLEMS = "villagersSummonGolems";
    static final String TRAVELLING_MERCHANTS = "travellingMerchants";
    static final String INK_AND_QUILL = "inkAndQuillRecipes";

    public static final Codec<WorldSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Codec.STRING, Codec.BOOL).optionalFieldOf("switches", Map.of()).forGetter(settings -> settings.switches),
            Codec.INT.optionalFieldOf("maxMerchantTours").forGetter(settings -> Optional.ofNullable(settings.maxMerchantTours))
    ).apply(i, WorldSettings::new));

    public static final SavedDataType<WorldSettings> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "settings"), WorldSettings::fromToml, CODEC, null);

    private record Cache(MinecraftServer server, WorldSettings settings) {}

    private static volatile Cache cache;

    private final Map<String, Boolean> switches;
    private volatile Integer maxMerchantTours;

    private WorldSettings(Map<String, Boolean> switches, Optional<Integer> maxMerchantTours) {
        this.switches = new ConcurrentHashMap<>(switches);
        this.maxMerchantTours = maxMerchantTours.orElse(null);
    }

    private static WorldSettings fromToml() {
        WorldSettings settings = new WorldSettings(Map.of(), Optional.empty());
        for (Feature feature : Feature.values()) settings.enabled(feature);
        for (Profession profession : Profession.values()) settings.enabled(profession);
        settings.villagersSummonGolems();
        settings.travellingMerchants();
        settings.inkAndQuillRecipes();
        settings.maxMerchantTours();
        settings.setDirty();
        return settings;
    }

    public static WorldSettings of(MinecraftServer server) {
        Cache current = cache;
        if (current != null && current.server == server) return current.settings;
        WorldSettings settings = server.getDataStorage().computeIfAbsent(TYPE);
        cache = new Cache(server, settings);
        return settings;
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        cache = null;
    }

    public boolean enabled(Feature feature) {
        return flag(feature.getSerializedName(), MineNowConfig.defaultValue(feature));
    }

    public void set(Feature feature, boolean on) {
        setFlag(feature.getSerializedName(), on);
    }

    public boolean enabled(Profession profession) {
        return flag(profession.getSerializedName(), MineNowConfig.defaultValue(profession));
    }

    public void set(Profession profession, boolean on) {
        setFlag(profession.getSerializedName(), on);
    }

    public boolean villagersSummonGolems() {
        return flag(SUMMON_GOLEMS, MineNowConfig.tomlOr(MineNowConfig.VILLAGERS_SUMMON_GOLEMS, false));
    }

    public boolean travellingMerchants() {
        return flag(TRAVELLING_MERCHANTS, MineNowConfig.tomlOr(MineNowConfig.TRAVELLING_MERCHANTS, true));
    }

    public boolean inkAndQuillRecipes() {
        return flag(INK_AND_QUILL, MineNowConfig.tomlOr(MineNowConfig.INK_AND_QUILL_RECIPES, false));
    }

    public int maxMerchantTours() {
        Integer tours = maxMerchantTours;
        if (tours == null) {
            tours = MineNowConfig.tomlOr(MineNowConfig.MAX_TOURS, 4);
            maxMerchantTours = tours;
            setDirty();
        }
        return tours;
    }

    public void setMaxMerchantTours(int tours) {
        maxMerchantTours = tours;
        setDirty();
    }

    void setFlag(String key, boolean on) {
        switches.put(key, on);
        setDirty();
    }

    private boolean flag(String key, boolean toml) {
        Boolean on = switches.get(key);
        if (on != null) return on;
        switches.put(key, toml);
        setDirty();
        return toml;
    }
}
