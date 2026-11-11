package com.sheepfromheaven.minenow.cartographer;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/** What a cartographer's map looks like at each level: how far it zooms out, what it draws, and where a block lands on it. */
public final class MapPlan {
    public static final int SIZE = 128;
    private static final int HALF = SIZE / 2;

    /** Markers use the darkest shade, which terrain only has under deep water, so they never look like land. */
    public static final byte ROAD_COLOR = MapColor.GOLD.getPackedId(MapColor.Brightness.LOWEST);
    public static final byte BORDER_COLOR = MapColor.COLOR_RED.getPackedId(MapColor.Brightness.LOWEST);
    public static final byte JOB_SITE_COLOR = MapColor.COLOR_PURPLE.getPackedId(MapColor.Brightness.LOWEST);
    public static final byte HOME_COLOR = MapColor.COLOR_PINK.getPackedId(MapColor.Brightness.LOWEST);

    private MapPlan() {}

    /** Vanilla's zoom: 0 shows 128 blocks across, each step doubling it, up to 4. */
    public static int scale(int level) {
        return Math.max(0, Math.min(MapItemSavedData.MAX_SCALE, level - 1));
    }

    public static int blocksAcross(int scale) {
        return SIZE << scale;
    }

    public static boolean showsRoads(int level) {
        return level >= 2;
    }

    public static boolean showsBorders(int level) {
        return level >= 3;
    }

    public static boolean showsPlaces(int level) {
        return level >= 4;
    }

    /** The map column (or row) a block coordinate falls in; outside 0 to 127 it is off the map. */
    public static int pixel(int coordinate, int center, int scale) {
        return Math.floorDiv(coordinate - center, 1 << scale) + HALF;
    }

    public static boolean onMap(int pixel) {
        return pixel >= 0 && pixel < SIZE;
    }

    /** The index into {@link MapItemSavedData#colors} of the pixel {@code pos} falls on, or -1 off the map. */
    public static int index(MapItemSavedData map, BlockPos pos) {
        int x = pixel(pos.getX(), map.centerX, map.scale);
        int z = pixel(pos.getZ(), map.centerZ, map.scale);
        return onMap(x) && onMap(z) ? x + z * SIZE : -1;
    }
}
