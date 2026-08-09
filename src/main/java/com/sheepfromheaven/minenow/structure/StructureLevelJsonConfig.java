package com.sheepfromheaven.minenow.structure;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.sheepfromheaven.minenow.effect.ProductivityMultiplierEffect;
import com.sheepfromheaven.minenow.effect.StructureEffect;
import com.sheepfromheaven.minenow.effect.InhabitantSlotsEffect;
import com.sheepfromheaven.minenow.requirement.BlockCountRequirement;
import com.sheepfromheaven.minenow.requirement.Requirement;
import com.sheepfromheaven.minenow.requirement.RoomSizeRequirement;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * What every {@link StructureType} needs at each level and what it grants there - the sole source of
 * this data (no compiled-in Java fallback). Ships as a bundled resource ({@code /minenow/structure_levels.json}
 * in the mod jar) that gets copied out to {@code minenow-structure-levels.json} in the config folder the
 * first time the mod runs, so modpack makers get a ready-to-edit file instead of having to write one from
 * scratch. Editing that file and calling {@link #load} again (e.g. via the {@code /minenow reloadconfig}
 * command) picks up changes without restarting the server. If the config-folder file is ever missing or
 * broken, {@link #load} falls back to re-reading the bundled resource directly rather than leaving
 * structures with no requirements at all.
 */
public final class StructureLevelJsonConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String FILE_NAME = "minenow-structure-levels.json";
    private static final String BUNDLED_RESOURCE = "/minenow/structure_levels.json";

    private record LevelData(List<Requirement> requirements, List<StructureEffect> effects) {}

    private static final Map<StructureType, Map<Integer, LevelData>> LEVELS = new EnumMap<>(StructureType.class);

    private StructureLevelJsonConfig() {}

    public static void load(Path configDir) {
        var path = configDir.resolve(FILE_NAME);
        if (!Files.exists(path)) {
            copyBundledDefaultsTo(path);
        }
        try {
            parseInto(Files.readString(path));
            LOGGER.info("Loaded MineNow structure level config");
        } catch (Exception e) {
            LOGGER.error("Failed to load {}, falling back to the bundled defaults", FILE_NAME, e);
            try {
                parseInto(readBundledDefaults());
            } catch (Exception fallbackFailure) {
                LOGGER.error("Bundled defaults for {} are also broken - structure levels will be empty", FILE_NAME, fallbackFailure);
                LEVELS.clear();
            }
        }
    }

    /** Everything declared at exactly {@code type}/{@code level} (not cumulative). Empty if that level has nothing. */
    public static List<Requirement> getRequirements(StructureType type, int level) {
        var data = dataFor(type, level);
        return data != null ? data.requirements() : List.of();
    }

    /** Effects granted once a structure reaches exactly {@code type}/{@code level}. Empty if that level grants none. */
    public static List<StructureEffect> getLevelEffects(StructureType type, int level) {
        var data = dataFor(type, level);
        return data != null ? data.effects() : List.of();
    }

    private static LevelData dataFor(StructureType type, int level) {
        var byLevel = LEVELS.get(type);
        return byLevel != null ? byLevel.get(level) : null;
    }

    // --- bundled resource handling ---

    private static void copyBundledDefaultsTo(Path path) {
        try (var in = StructureLevelJsonConfig.class.getResourceAsStream(BUNDLED_RESOURCE)) {
            if (in == null) {
                LOGGER.error("Bundled resource {} is missing from the mod jar", BUNDLED_RESOURCE);
                return;
            }
            Files.createDirectories(path.getParent());
            Files.copy(in, path, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.error("Failed to copy bundled defaults to {}", path, e);
        }
    }

    private static String readBundledDefaults() throws IOException {
        try (InputStream in = StructureLevelJsonConfig.class.getResourceAsStream(BUNDLED_RESOURCE)) {
            if (in == null) {
                throw new IOException("Bundled resource " + BUNDLED_RESOURCE + " is missing from the mod jar");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // --- parsing ---

    private static void parseInto(String json) {
        var root = JsonParser.parseString(json).getAsJsonObject();
        var parsed = new EnumMap<StructureType, Map<Integer, LevelData>>(StructureType.class);
        for (var type : StructureType.values()) {
            if (!root.has(type.name())) continue;
            var levelsJson = root.getAsJsonObject(type.name());
            var byLevel = new HashMap<Integer, LevelData>();
            for (var entry : levelsJson.entrySet()) {
                int level;
                try {
                    level = Integer.parseInt(entry.getKey());
                } catch (NumberFormatException e) {
                    LOGGER.warn("Invalid level key '{}' for {} in {}, skipping", entry.getKey(), type.name(), FILE_NAME);
                    continue;
                }
                var levelObj = entry.getValue().getAsJsonObject();
                var requirements = levelObj.has("requirements")
                        ? parseRequirements(levelObj.getAsJsonArray("requirements"), type.name(), level)
                        : List.<Requirement>of();
                var effects = levelObj.has("effects")
                        ? parseEffects(levelObj.getAsJsonArray("effects"), type.name(), level)
                        : List.<StructureEffect>of();
                byLevel.put(level, new LevelData(requirements, effects));
            }
            parsed.put(type, byLevel);
        }
        // Only swapped in after fully parsing, so a mid-parse failure never leaves LEVELS half-updated.
        LEVELS.clear();
        LEVELS.putAll(parsed);
    }

    private static List<Requirement> parseRequirements(JsonArray arr, String typeName, int level) {
        var list = new ArrayList<Requirement>();
        for (var element : arr) {
            var obj = element.getAsJsonObject();
            if (obj.has("space")) {
                list.add(new RoomSizeRequirement(obj.get("space").getAsInt()));
                continue;
            }

            String name = obj.has("name") ? obj.get("name").getAsString() : "Unknown";
            int count = obj.has("count") ? obj.get("count").getAsInt() : 1;
            int airAbove = obj.has("airAbove") ? obj.get("airAbove").getAsInt() : 0;
            Predicate<BlockState> matcher;
            Supplier<ItemStack> icon;

            if (obj.has("tag")) {
                var tagId = Identifier.tryParse(obj.get("tag").getAsString());
                if (tagId == null) {
                    LOGGER.warn("Invalid tag in {} level {} requirements, skipping", typeName, level);
                    continue;
                }
                var tagKey = TagKey.create(Registries.BLOCK, tagId);
                matcher = state -> state.is(tagKey);
                icon = obj.has("icon") ? iconSupplier(obj.get("icon").getAsString()) : () -> ItemStack.EMPTY;
            } else if (obj.has("block")) {
                var blockId = Identifier.tryParse(obj.get("block").getAsString());
                if (blockId == null) {
                    LOGGER.warn("Invalid block in {} level {} requirements, skipping", typeName, level);
                    continue;
                }
                matcher = state -> blockId.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
                icon = iconSupplier(obj.has("icon") ? obj.get("icon").getAsString() : obj.get("block").getAsString());
            } else {
                LOGGER.warn("Requirement in {} level {} has no 'space', 'block' or 'tag' field, skipping", typeName, level);
                continue;
            }

            list.add(new BlockCountRequirement(new StructureType.Requirement(name, count, matcher, icon, airAbove)));
        }
        return List.copyOf(list);
    }

    private static List<StructureEffect> parseEffects(JsonArray arr, String typeName, int level) {
        var list = new ArrayList<StructureEffect>();
        for (var element : arr) {
            var obj = element.getAsJsonObject();
            String type = obj.has("type") ? obj.get("type").getAsString() : "";
            switch (type) {
                case "inhabitantSlots" -> list.add(new InhabitantSlotsEffect(obj.get("amount").getAsInt()));
                case "productivity" -> list.add(new ProductivityMultiplierEffect(obj.get("multiplier").getAsDouble()));
                default -> LOGGER.warn("Unknown effect type '{}' in {} level {}, skipping", type, typeName, level);
            }
        }
        return List.copyOf(list);
    }

    private static Supplier<ItemStack> iconSupplier(String itemId) {
        return () -> {
            var id = Identifier.tryParse(itemId);
            if (id == null) return ItemStack.EMPTY;
            var itemOpt = BuiltInRegistries.ITEM.getOptional(id);
            return itemOpt.isPresent() && itemOpt.get() != net.minecraft.world.item.Items.AIR ? new ItemStack(itemOpt.get()) : ItemStack.EMPTY;
        };
    }
}
