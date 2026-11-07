package com.sheepfromheaven.minenow.gametest;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Which feature a gametest belongs to; the playground lays each group out as its own themed district. */
public enum TestGroup {
    VILLAGERS("Villagers", Blocks.SAND, Biomes.DESERT),
    STORAGE("Home storage", Blocks.RED_SAND, Biomes.BADLANDS),
    ROADS("Roads", Blocks.SNOW_BLOCK, Biomes.SNOWY_PLAINS),
    FARMER("Farmer", Blocks.GRASS_BLOCK, Biomes.PLAINS),
    TRADING("Farmer trading", Blocks.MYCELIUM, Biomes.MUSHROOM_FIELDS);

    private static final Map<String, TestGroup> BY_TEST = new ConcurrentHashMap<>();

    public final String title;
    public final Block floor;
    public final ResourceKey<Biome> biome;

    TestGroup(String title, Block floor, ResourceKey<Biome> biome) {
        this.title = title;
        this.floor = floor;
        this.biome = biome;
    }

    public static void assign(String testName, TestGroup group) {
        BY_TEST.put(testName, group);
    }

    public static TestGroup of(String testName) {
        return BY_TEST.getOrDefault(testName, VILLAGERS);
    }
}
