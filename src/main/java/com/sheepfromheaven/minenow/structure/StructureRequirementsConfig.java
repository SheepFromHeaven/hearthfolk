package com.sheepfromheaven.minenow.structure;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.registries.BuiltInRegistries;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Player-editable config for the "range" requirements only (a tree/water tile somewhere nearby, not
 * inside the structure's own room) - things like a fishery needing water in range or a lumberyard
 * needing a tree. The in-room build/level requirements used to live here too but now live in each
 * type's own {@link StructureDefinition} (e.g. {@code structure.fishery.FisheryDefinition}), since they
 * needed to vary per level as well as per type.
 */
public class StructureRequirementsConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Map<StructureType, Integer> RANGE_RADII = new EnumMap<>(StructureType.class);
    private static final Map<StructureType, Integer> RANGE_HEIGHTS = new EnumMap<>(StructureType.class);
    private static final Map<StructureType, List<StructureType.Requirement>> RANGE_REQUIREMENTS = new EnumMap<>(StructureType.class);

    public static void load(Path configDir) {
        var path = configDir.resolve("minenow-structures.json");
        if (!Files.exists(path)) {
            writeDefaults(path);
        }
        try {
            var json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            RANGE_RADII.clear();
            RANGE_HEIGHTS.clear();
            RANGE_REQUIREMENTS.clear();
            for (var type : StructureType.values()) {
                if (json.has(type.name())) {
                    var entry = json.getAsJsonObject(type.name());
                    RANGE_RADII.put(type, entry.has("rangeRadius") ? entry.get("rangeRadius").getAsInt() : 0);
                    RANGE_HEIGHTS.put(type, entry.has("rangeHeight") ? entry.get("rangeHeight").getAsInt() : 0);
                    RANGE_REQUIREMENTS.put(type, entry.has("rangeRequirements")
                            ? parseRequirements(entry.getAsJsonArray("rangeRequirements"), type.name())
                            : List.of());
                } else {
                    RANGE_RADII.put(type, 0);
                    RANGE_HEIGHTS.put(type, 0);
                    RANGE_REQUIREMENTS.put(type, List.of());
                }
            }
            LOGGER.info("Loaded MineNow structure requirements config");
        } catch (Exception e) {
            LOGGER.error("Failed to load minenow-structures.json, using defaults", e);
            loadHardcodedDefaults();
        }
    }

    public static int getRangeRadius(StructureType type) {
        return RANGE_RADII.getOrDefault(type, 0);
    }

    public static int getRangeHeight(StructureType type) {
        return RANGE_HEIGHTS.getOrDefault(type, 0);
    }

    public static List<StructureType.Requirement> getRangeRequirements(StructureType type) {
        return RANGE_REQUIREMENTS.getOrDefault(type, List.of());
    }

    private static List<StructureType.Requirement> parseRequirements(JsonArray arr, String typeName) {
        var list = new ArrayList<StructureType.Requirement>();
        for (var element : arr) {
            var obj = element.getAsJsonObject();
            String name = obj.has("name") ? obj.get("name").getAsString() : "Unknown";
            int count = obj.has("count") ? obj.get("count").getAsInt() : 1;
            int airAbove = obj.has("airAbove") ? obj.get("airAbove").getAsInt() : 0;

            Predicate<BlockState> matcher;
            Supplier<ItemStack> icon;

            if (obj.has("tag")) {
                String tagStr = obj.get("tag").getAsString();
                var tagId = Identifier.tryParse(tagStr);
                if (tagId == null) {
                    LOGGER.warn("Invalid tag '{}' in {} requirements, skipping", tagStr, typeName);
                    continue;
                }
                var tagKey = TagKey.create(Registries.BLOCK, tagId);
                matcher = state -> state.is(tagKey);

                String iconBlock = obj.has("icon") ? obj.get("icon").getAsString() : null;
                icon = iconBlock != null ? iconSupplier(iconBlock) : () -> ItemStack.EMPTY;
            } else if (obj.has("block")) {
                String blockStr = obj.get("block").getAsString();
                var blockId = Identifier.tryParse(blockStr);
                if (blockId == null) {
                    LOGGER.warn("Invalid block '{}' in {} requirements, skipping", blockStr, typeName);
                    continue;
                }
                matcher = state -> blockId.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
                String iconBlock = obj.has("icon") ? obj.get("icon").getAsString() : blockStr;
                icon = iconSupplier(iconBlock);
            } else {
                LOGGER.warn("Requirement in {} has no 'block' or 'tag' field, skipping", typeName);
                continue;
            }

            list.add(new StructureType.Requirement(name, count, matcher, icon, airAbove));
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

    private static void writeDefaults(Path path) {
        var root = new JsonObject();

        var fishery = new JsonObject();
        fishery.addProperty("rangeRadius", 8);
        fishery.addProperty("rangeHeight", 8);
        var fishRangeReqs = new JsonArray();
        var water = new JsonObject();
        water.addProperty("block", "minecraft:water");
        water.addProperty("icon", "minecraft:water_bucket");
        water.addProperty("name", "Water");
        water.addProperty("count", 1);
        water.addProperty("airAbove", 2);
        fishRangeReqs.add(water);
        fishery.add("rangeRequirements", fishRangeReqs);
        root.add("FISHERY", fishery);

        var lumberyard = new JsonObject();
        lumberyard.addProperty("rangeRadius", 8);
        lumberyard.addProperty("rangeHeight", 4);
        var lumberRangeReqs = new JsonArray();
        var logs = new JsonObject();
        logs.addProperty("tag", "minecraft:logs");
        logs.addProperty("icon", "minecraft:oak_log");
        logs.addProperty("name", "Tree");
        logs.addProperty("count", 1);
        lumberRangeReqs.add(logs);
        lumberyard.add("rangeRequirements", lumberRangeReqs);
        root.add("LUMBERYARD", lumberyard);

        var nursery = new JsonObject();
        nursery.addProperty("rangeRadius", 8);
        nursery.addProperty("rangeHeight", 2);
        var nurseryRangeReqs = new JsonArray();
        var soil = new JsonObject();
        soil.addProperty("tag", "minecraft:dirt");
        soil.addProperty("icon", "minecraft:oak_sapling");
        soil.addProperty("name", "Plantable Ground");
        soil.addProperty("count", 1);
        soil.addProperty("airAbove", 1);
        nurseryRangeReqs.add(soil);
        nursery.add("rangeRequirements", nurseryRangeReqs);
        root.add("NURSERY", nursery);

        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(root));
        } catch (IOException e) {
            LOGGER.error("Failed to write default minenow-structures.json", e);
        }
    }

    private static void loadHardcodedDefaults() {
        RANGE_RADII.clear();
        RANGE_RADII.put(StructureType.HOUSE, 0);
        RANGE_RADII.put(StructureType.FISHERY, 8);
        RANGE_RADII.put(StructureType.LUMBERYARD, 8);
        RANGE_RADII.put(StructureType.TAVERN, 0);
        RANGE_RADII.put(StructureType.STOREHOUSE, 0);
        RANGE_RADII.put(StructureType.MINE, 0);
        RANGE_RADII.put(StructureType.NURSERY, 8);
        RANGE_RADII.put(StructureType.CHARCOAL_KILN, 0);
        RANGE_RADII.put(StructureType.FARM, 0);

        RANGE_HEIGHTS.clear();
        RANGE_HEIGHTS.put(StructureType.HOUSE, 0);
        RANGE_HEIGHTS.put(StructureType.FISHERY, 4);
        RANGE_HEIGHTS.put(StructureType.LUMBERYARD, 4);
        RANGE_HEIGHTS.put(StructureType.TAVERN, 0);
        RANGE_HEIGHTS.put(StructureType.STOREHOUSE, 0);
        RANGE_HEIGHTS.put(StructureType.MINE, 0);
        RANGE_HEIGHTS.put(StructureType.NURSERY, 2);
        RANGE_HEIGHTS.put(StructureType.CHARCOAL_KILN, 0);
        RANGE_HEIGHTS.put(StructureType.FARM, 0);

        RANGE_REQUIREMENTS.clear();
        RANGE_REQUIREMENTS.put(StructureType.HOUSE, List.of());
        RANGE_REQUIREMENTS.put(StructureType.FISHERY, List.of(
                new StructureType.Requirement("Water", 1,
                        s -> s.is(net.minecraft.world.level.block.Blocks.WATER),
                        () -> new ItemStack(net.minecraft.world.item.Items.WATER_BUCKET), 2)
        ));
        RANGE_REQUIREMENTS.put(StructureType.LUMBERYARD, List.of(
                new StructureType.Requirement("Tree", 1,
                        s -> s.is(net.minecraft.tags.BlockTags.LOGS),
                        () -> new ItemStack(net.minecraft.world.item.Items.OAK_LOG))
        ));
        RANGE_REQUIREMENTS.put(StructureType.TAVERN, List.of());
        RANGE_REQUIREMENTS.put(StructureType.STOREHOUSE, List.of());
        RANGE_REQUIREMENTS.put(StructureType.MINE, List.of());
        RANGE_REQUIREMENTS.put(StructureType.NURSERY, List.of(
                new StructureType.Requirement("Plantable Ground", 1,
                        s -> s.is(net.minecraft.tags.BlockTags.DIRT)
                                || s.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK)
                                || s.is(net.minecraft.world.level.block.Blocks.PODZOL)
                                || s.is(net.minecraft.world.level.block.Blocks.MYCELIUM)
                                || s.is(net.minecraft.world.level.block.Blocks.FARMLAND),
                        () -> new ItemStack(net.minecraft.world.item.Items.OAK_SAPLING), 1)
        ));
        RANGE_REQUIREMENTS.put(StructureType.CHARCOAL_KILN, List.of());
        RANGE_REQUIREMENTS.put(StructureType.FARM, List.of());
    }
}
