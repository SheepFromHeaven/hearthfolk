package com.sheepfromheaven.minenow.core;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

@EventBusSubscriber(modid = MineNow.MOD_ID)
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue DEFAULT_DOMINION_RADIUS = BUILDER
            .comment("Default radius (in blocks) for a new dominion")
            .defineInRange("defaultDominionRadius", 64, 16, 256);

    public static final ModConfigSpec.IntValue MAX_ROOM_SIZE = BUILDER
            .comment("Maximum number of air blocks a room can contain for structure detection")
            .defineInRange("maxRoomSize", 200, 50, 2000);

    public static final ModConfigSpec.IntValue MAX_TRUNK_SIZE = BUILDER
            .comment("Maximum number of connected logs a tree's trunk can contain before the lumberjack " +
                    "gives up recognizing it as a tree (large jungle and dark oak trees need more than the default)")
            .defineInRange("maxTrunkSize", 128, 32, 2000);

    public static final ModConfigSpec.BooleanValue DEBUG_INHABITANT_VIEW = BUILDER
            .comment("Adds a Debug tab to the inhabitant screen showing current AI goals, job state, " +
                    "and pathfinding info, and visualizes the active path with particles")
            .define("debugInhabitantView", false);

    public static final ModConfigSpec.BooleanValue ADD_BELL_RECIPE = BUILDER
            .comment("Adds a crafting recipe for the vanilla bell (3 sticks, a gold ingot, and a golden " +
                    "nugget), since bells are normally not craftable and are needed for the dominion banner")
            .define("addBellRecipe", false);

    static final ModConfigSpec SPEC = BUILDER.build();

    public static int defaultDominionRadius;
    public static int maxRoomSize;
    public static int maxTrunkSize;
    public static boolean debugInhabitantView;
    public static boolean addBellRecipe;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        defaultDominionRadius = DEFAULT_DOMINION_RADIUS.get();
        maxRoomSize = MAX_ROOM_SIZE.get();
        maxTrunkSize = MAX_TRUNK_SIZE.get();
        debugInhabitantView = DEBUG_INHABITANT_VIEW.get();
        addBellRecipe = ADD_BELL_RECIPE.get();
    }
}
