package com.sheepfromheaven.minenow.dominion;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class StructureRequirementsConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Map<StructureType, List<StructureType.Requirement>> REQUIREMENTS = new EnumMap<>(StructureType.class);

    public static void load(Path configDir) {
        var path = configDir.resolve("minenow-structures.json");
        if (!Files.exists(path)) {
            writeDefaults(path);
        }
        try {
            var json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            REQUIREMENTS.clear();
            for (var type : StructureType.values()) {
                if (json.has(type.name())) {
                    var entry = json.getAsJsonObject(type.name());
                    var reqs = parseRequirements(entry.getAsJsonArray("requirements"), type.name());
                    REQUIREMENTS.put(type, reqs);
                } else {
                    REQUIREMENTS.put(type, List.of());
                }
            }
            LOGGER.info("Loaded MineNow structure requirements config");
        } catch (Exception e) {
            LOGGER.error("Failed to load minenow-structures.json, using defaults", e);
            loadHardcodedDefaults();
        }
    }

    public static List<StructureType.Requirement> getRequirements(StructureType type) {
        return REQUIREMENTS.getOrDefault(type, List.of());
    }

    private static List<StructureType.Requirement> parseRequirements(JsonArray arr, String typeName) {
        var list = new ArrayList<StructureType.Requirement>();
        for (var element : arr) {
            var obj = element.getAsJsonObject();
            String name = obj.has("name") ? obj.get("name").getAsString() : "Unknown";
            int count = obj.has("count") ? obj.get("count").getAsInt() : 1;

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
                matcher = state -> ForgeRegistries.BLOCKS.getKey(state.getBlock()).equals(blockId);
                icon = iconSupplier(blockStr);
            } else {
                LOGGER.warn("Requirement in {} has no 'block' or 'tag' field, skipping", typeName);
                continue;
            }

            list.add(new StructureType.Requirement(name, count, matcher, icon));
        }
        return List.copyOf(list);
    }

    private static Supplier<ItemStack> iconSupplier(String blockId) {
        return () -> {
            var id = Identifier.tryParse(blockId);
            if (id == null) return ItemStack.EMPTY;
            var block = ForgeRegistries.BLOCKS.getValue(id);
            if (block == null) return ItemStack.EMPTY;
            var item = block.asItem();
            return item != net.minecraft.world.item.Items.AIR ? new ItemStack(item) : ItemStack.EMPTY;
        };
    }

    private static void writeDefaults(Path path) {
        var root = new JsonObject();

        var house = new JsonObject();
        var houseReqs = new JsonArray();
        var bed = new JsonObject();
        bed.addProperty("tag", "minecraft:beds");
        bed.addProperty("icon", "minecraft:red_bed");
        bed.addProperty("name", "Bed");
        bed.addProperty("count", 1);
        houseReqs.add(bed);
        house.add("requirements", houseReqs);
        root.add("HOUSE", house);

        var fishery = new JsonObject();
        var fishReqs = new JsonArray();
        addBlockReq(fishReqs, "minecraft:barrel", "Barrel", 1);
        addBlockReq(fishReqs, "minecraft:crafting_table", "Crafting Table", 1);
        addBlockReq(fishReqs, "minecraft:smoker", "Smoker", 1);
        fishery.add("requirements", fishReqs);
        root.add("FISHERY", fishery);

        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(root));
        } catch (IOException e) {
            LOGGER.error("Failed to write default minenow-structures.json", e);
        }
    }

    private static void addBlockReq(JsonArray arr, String block, String name, int count) {
        var obj = new JsonObject();
        obj.addProperty("block", block);
        obj.addProperty("name", name);
        obj.addProperty("count", count);
        arr.add(obj);
    }

    private static void loadHardcodedDefaults() {
        REQUIREMENTS.clear();
        REQUIREMENTS.put(StructureType.HOUSE, List.of(
                new StructureType.Requirement("Bed", 1,
                        s -> s.getBlock() instanceof net.minecraft.world.level.block.BedBlock,
                        () -> new ItemStack(net.minecraft.world.item.Items.RED_BED))
        ));
        REQUIREMENTS.put(StructureType.FISHERY, List.of(
                new StructureType.Requirement("Barrel", 1,
                        s -> s.is(net.minecraft.world.level.block.Blocks.BARREL),
                        () -> new ItemStack(net.minecraft.world.item.Items.BARREL)),
                new StructureType.Requirement("Crafting Table", 1,
                        s -> s.is(net.minecraft.world.level.block.Blocks.CRAFTING_TABLE),
                        () -> new ItemStack(net.minecraft.world.item.Items.CRAFTING_TABLE)),
                new StructureType.Requirement("Smoker", 1,
                        s -> s.is(net.minecraft.world.level.block.Blocks.SMOKER),
                        () -> new ItemStack(net.minecraft.world.item.Items.SMOKER))
        ));
    }
}
