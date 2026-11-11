package com.sheepfromheaven.minenow.cartographer;

import com.sheepfromheaven.minenow.villager.CraftResults;
import com.sheepfromheaven.minenow.villager.Villages;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Draws the map a cartographer makes: a locked snapshot of its village centred on the middle of its bells, as wide as its
 * level allows, with the roads, the village border and the job sites and homes of its members on top ({@link MapPlan}).
 */
public final class CartographerMaps implements CraftResults.Maker {
    private static final int DOT = 2;

    @Override
    public boolean canMake(ServerLevel level, Villager villager) {
        return Villages.of(level, villager).isPresent();
    }

    @Override
    public ItemStack make(ServerLevel level, Villager villager, int count) {
        Villages.Village village = Villages.of(level, villager).orElseThrow();
        return draw(level, village, villager.getVillagerData().level());
    }

    public static ItemStack draw(ServerLevel level, Villages.Village village, int villagerLevel) {
        byte scale = (byte) MapPlan.scale(villagerLevel);
        BlockPos center = centre(village);
        MapItemSavedData map = new MapItemSavedData(center.getX(), center.getZ(), scale, false, false, true, level.dimension());
        paintLand(level, map, villagerLevel);
        if (MapPlan.showsBorders(villagerLevel)) paintBorder(map, village);
        if (MapPlan.showsPlaces(villagerLevel)) paintPlaces(level, map, village);
        MapId id = level.getFreeMapId();
        level.setMapData(id, map);
        ItemStack stack = new ItemStack(Items.FILLED_MAP);
        stack.set(DataComponents.MAP_ID, id);
        stack.set(DataComponents.ITEM_NAME, Component.translatable("item.minenow.village_map"));
        for (BlockPos bell : village.bells()) {
            MapItemSavedData.addTargetDecoration(stack, bell, "bell_" + bell.toShortString(), MapDecorationTypes.TARGET_POINT);
        }
        return stack;
    }

    private static BlockPos centre(Villages.Village village) {
        long x = 0, z = 0;
        for (BlockPos bell : village.bells()) {
            x += bell.getX();
            z += bell.getZ();
        }
        int bells = Math.max(1, village.bells().size());
        return new BlockPos((int) Math.floorDiv(x, bells), 0, (int) Math.floorDiv(z, bells));
    }

    private static void paintLand(ServerLevel level, MapItemSavedData map, int villagerLevel) {
        int size = 1 << map.scale;
        boolean roads = MapPlan.showsRoads(villagerLevel);
        int samples = Math.min(size, 2);
        for (int py = 0; py < MapPlan.SIZE; py++) {
            for (int px = 0; px < MapPlan.SIZE; px++) {
                int x = map.centerX + (px - MapPlan.SIZE / 2) * size;
                int z = map.centerZ + (py - MapPlan.SIZE / 2) * size;
                if (!level.hasChunkAt(new BlockPos(x, 0, z))) continue;
                byte color = terrain(level, x + size / 2, z + size / 2);
                if (roads && roadIn(level, x, z, size, samples)) color = MapPlan.ROAD_COLOR;
                map.colors[px + py * MapPlan.SIZE] = color;
            }
        }
    }

    private static final int SEE_THROUGH = 16;

    /** The first block from the top that has a map colour (vanilla's maps skip air, barriers and glass the same way), or null. */
    private static BlockPos visible(ServerLevel level, int x, int z) {
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, top, z);
        for (int y = top; y >= Math.max(level.getMinY(), top - SEE_THROUGH); y--) {
            pos.setY(y);
            if (level.getBlockState(pos).getMapColor(level, pos) != MapColor.NONE) return pos.immutable();
        }
        return null;
    }

    private static byte terrain(ServerLevel level, int x, int z) {
        BlockPos pos = visible(level, x, z);
        if (pos == null) return 0;
        BlockPos north = visible(level, x, z - 1);
        MapColor.Brightness brightness = north == null || pos.getY() == north.getY() ? MapColor.Brightness.NORMAL
                : pos.getY() > north.getY() ? MapColor.Brightness.HIGH : MapColor.Brightness.LOW;
        return level.getBlockState(pos).getMapColor(level, pos).getPackedId(brightness);
    }

    private static boolean roadIn(ServerLevel level, int x, int z, int size, int samples) {
        for (int i = 0; i < samples; i++) {
            for (int j = 0; j < samples; j++) {
                BlockPos top = visible(level, x + i * size / samples, z + j * size / samples);
                if (top != null && com.sheepfromheaven.minenow.villager.Roads.isRoad(level, top.above())) return true;
            }
        }
        return false;
    }

    private static void paintBorder(MapItemSavedData map, Villages.Village village) {
        int size = 1 << map.scale;
        boolean[][] inside = new boolean[MapPlan.SIZE][MapPlan.SIZE];
        for (int py = 0; py < MapPlan.SIZE; py++) {
            for (int px = 0; px < MapPlan.SIZE; px++) {
                inside[px][py] = village.contains(new BlockPos(map.centerX + (px - MapPlan.SIZE / 2) * size + size / 2, 0, map.centerZ + (py - MapPlan.SIZE / 2) * size + size / 2));
            }
        }
        for (int py = 1; py < MapPlan.SIZE - 1; py++) {
            for (int px = 1; px < MapPlan.SIZE - 1; px++) {
                if (inside[px][py] && !(inside[px - 1][py] && inside[px + 1][py] && inside[px][py - 1] && inside[px][py + 1])) {
                    map.colors[px + py * MapPlan.SIZE] = MapPlan.BORDER_COLOR;
                }
            }
        }
    }

    private static void paintPlaces(ServerLevel level, MapItemSavedData map, Villages.Village village) {
        Set<BlockPos> jobSites = new HashSet<>();
        Set<BlockPos> homes = new HashSet<>();
        for (Villager member : village.villagers(level)) {
            place(member, MemoryModuleType.JOB_SITE, jobSites);
            place(member, MemoryModuleType.HOME, homes);
        }
        homes.forEach(pos -> dot(map, pos, MapPlan.HOME_COLOR));
        jobSites.forEach(pos -> dot(map, pos, MapPlan.JOB_SITE_COLOR));
    }

    private static void place(Villager member, MemoryModuleType<GlobalPos> memory, Set<BlockPos> into) {
        Optional<GlobalPos> pos = member.getBrain().getMemory(memory);
        pos.filter(found -> found.dimension() == member.level().dimension()).ifPresent(found -> into.add(found.pos()));
    }

    private static void dot(MapItemSavedData map, BlockPos pos, byte color) {
        int index = MapPlan.index(map, pos);
        if (index < 0) return;
        int px = index % MapPlan.SIZE;
        int py = index / MapPlan.SIZE;
        for (int dx = 0; dx < DOT; dx++) {
            for (int dy = 0; dy < DOT; dy++) {
                if (MapPlan.onMap(px + dx) && MapPlan.onMap(py + dy)) map.colors[px + dx + (py + dy) * MapPlan.SIZE] = color;
            }
        }
    }
}
