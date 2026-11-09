package com.sheepfromheaven.minenow.gametest;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Which feature a gametest belongs to; the playground gives each group its own district (wool floor) and a hub portal framed in {@link #wall}. */
public enum TestGroup {
    VILLAGERS("Villagers", Blocks.WOOL.yellow(), Blocks.CONCRETE.yellow()),
    STORAGE("Home storage", Blocks.WOOL.red(), Blocks.CONCRETE.red()),
    ROADS("Roads", Blocks.WOOL.lightBlue(), Blocks.CONCRETE.lightBlue()),
    FARMER("Farmer", Blocks.WOOL.lime(), Blocks.CONCRETE.lime()),
    TRADING("Farmer trading", Blocks.WOOL.purple(), Blocks.CONCRETE.purple()),
    FISHERMAN("Fisherman", Blocks.WOOL.cyan(), Blocks.CONCRETE.cyan()),
    SHEPHERD("Shepherd", Blocks.WOOL.white(), Blocks.CONCRETE.white()),
    BUTCHER("Butcher", Blocks.WOOL.brown(), Blocks.CONCRETE.brown()),
    VILLAGER_TRADING("Villager trading", Blocks.WOOL.orange(), Blocks.CONCRETE.orange()),
    TOOLSMITH("Toolsmith", Blocks.WOOL.gray(), Blocks.CONCRETE.gray()),
    MINER("Miner", Blocks.WOOL.black(), Blocks.CONCRETE.black()),
    FORESTER("Forester", Blocks.WOOL.green(), Blocks.CONCRETE.green()),
    FLETCHER("Fletcher", Blocks.WOOL.pink(), Blocks.CONCRETE.pink());

    private static final Map<String, TestGroup> BY_TEST = new ConcurrentHashMap<>();

    public final String title;
    public final Block floor;
    public final Block wall;

    TestGroup(String title, Block floor, Block wall) {
        this.title = title;
        this.floor = floor;
        this.wall = wall;
    }

    public static void assign(String testName, TestGroup group) {
        BY_TEST.put(testName, group);
    }

    public static TestGroup of(String testName) {
        return BY_TEST.getOrDefault(testName, VILLAGERS);
    }
}
