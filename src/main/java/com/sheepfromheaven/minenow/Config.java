package com.sheepfromheaven.minenow;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid = MineNow.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.IntValue DEFAULT_DOMINION_RADIUS = BUILDER
            .comment("Default radius (in blocks) for a new dominion")
            .defineInRange("defaultDominionRadius", 64, 16, 256);

    public static final ForgeConfigSpec.IntValue MAX_ROOM_SIZE = BUILDER
            .comment("Maximum number of air blocks a room can contain for structure detection")
            .defineInRange("maxRoomSize", 200, 50, 2000);

    public static final ForgeConfigSpec.IntValue MAX_TRUNK_SIZE = BUILDER
            .comment("Maximum number of connected logs a tree's trunk can contain before the lumberjack " +
                    "gives up recognizing it as a tree (large jungle and dark oak trees need more than the default)")
            .defineInRange("maxTrunkSize", 128, 32, 2000);

    public static final ForgeConfigSpec.BooleanValue DEBUG_INHABITANT_VIEW = BUILDER
            .comment("Adds a Debug tab to the inhabitant screen showing current AI goals, job state, " +
                    "and pathfinding info, and visualizes the active path with particles")
            .define("debugInhabitantView", false);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    public static int defaultDominionRadius;
    public static int maxRoomSize;
    public static int maxTrunkSize;
    public static boolean debugInhabitantView;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        defaultDominionRadius = DEFAULT_DOMINION_RADIUS.get();
        maxRoomSize = MAX_ROOM_SIZE.get();
        maxTrunkSize = MAX_TRUNK_SIZE.get();
        debugInhabitantView = DEBUG_INHABITANT_VIEW.get();
    }
}
