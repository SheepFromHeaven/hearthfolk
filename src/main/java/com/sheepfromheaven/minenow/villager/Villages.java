package com.sheepfromheaven.minenow.villager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * A village grows out of its bells: every villager's claimed bell ({@link ChooseMeetingPoint}), bed, job site and
 * chests are its places, a villager's places belong together, and places within twice {@link #REACH} blocks of each
 * other (across, and {@link #HEIGHT} up or down) join up, so beds and workshops between two bells make them one
 * village however far apart the bells are. Each group of places with a bell in it is a village, its area everything
 * within {@link #REACH} of its places; a villager belongs to the village its places are in, or one with none to the
 * village it stands in. Rebuilt
 * every {@link #REFRESH} ticks from the villagers loaded in the level and never saved, so it follows what the player
 * builds.
 */
public final class Villages {
    public static final int REACH = 16;
    static final int HEIGHT = 48;
    static final int REFRESH = 100;

    private static final net.minecraft.world.level.block.Block MARKER_BASE = net.minecraft.world.level.block.Blocks.HAY_BLOCK;
    private static final Map<ServerLevel, Index> INDEX = new WeakHashMap<>();

    private Villages() {}

    public static final class Village {
        private final Set<BlockPos> bells;
        private final List<BlockPos> anchors;
        private final Set<UUID> members = new LinkedHashSet<>();

        private Village(Set<BlockPos> bells, List<BlockPos> anchors) {
            this.bells = Set.copyOf(bells);
            this.anchors = List.copyOf(anchors);
        }

        public Set<BlockPos> bells() {
            return bells;
        }

        /** The bells and the beds, job sites and chests of the villagers that claimed them. */
        public List<BlockPos> anchors() {
            return anchors;
        }

        public Set<UUID> members() {
            return Set.copyOf(members);
        }

        public boolean contains(BlockPos pos) {
            return anchors.stream().anyMatch(anchor -> within(anchor, pos, REACH));
        }

        /** The members loaded in the level. */
        public List<Villager> villagers(ServerLevel level) {
            List<Villager> found = new ArrayList<>();
            for (UUID id : members) {
                if (level.getEntity(id) instanceof Villager villager && villager.isAlive()) found.add(villager);
            }
            return found;
        }
    }

    private record Index(long computedAt, List<Village> villages, Map<UUID, Village> byMember) {}

    public static Optional<Village> of(ServerLevel level, Villager villager) {
        Index index = index(level);
        Village village = index.byMember().get(villager.getUUID());
        if (village == null && index.computedAt() != level.getGameTime() && claimedBell(level, villager).isPresent()) {
            village = rebuild(level).byMember().get(villager.getUUID());
        }
        return Optional.ofNullable(village);
    }

    public static Optional<Village> at(ServerLevel level, BlockPos pos) {
        return index(level).villages().stream().filter(village -> village.contains(pos)).findFirst();
    }

    public static List<Village> all(ServerLevel level) {
        return index(level).villages();
    }

    private static Index index(ServerLevel level) {
        Index index = INDEX.get(level);
        long now = level.getGameTime();
        if (index == null || now - index.computedAt() >= REFRESH || now < index.computedAt()) index = rebuild(level);
        return index;
    }

    private static Index rebuild(ServerLevel level) {
        List<? extends Villager> villagers = level.getEntities(EntityTypes.VILLAGER, Villager::isAlive);
        List<BlockPos> points = new ArrayList<>();
        Map<BlockPos, Integer> bells = new HashMap<>();
        List<List<Integer>> pointsOf = new ArrayList<>();
        for (Villager villager : villagers) {
            List<Integer> own = new ArrayList<>();
            claimedBell(level, villager).ifPresent(bell -> own.add(bells.computeIfAbsent(bell, b -> add(points, b))));
            place(level, villager, MemoryModuleType.HOME).ifPresent(pos -> own.add(add(points, pos)));
            place(level, villager, MemoryModuleType.JOB_SITE).ifPresent(pos -> own.add(add(points, pos)));
            for (BlockPos chest : Workshop.allChests(level, villager)) own.add(add(points, chest));
            pointsOf.add(own);
        }
        addMarkers(level, points);

        int[] group = new int[points.size()];
        for (int i = 0; i < group.length; i++) group[i] = i;
        for (List<Integer> own : pointsOf) {
            for (int k = 1; k < own.size(); k++) join(group, own.get(0), own.get(k));
        }
        Map<Long, List<Integer>> grid = new HashMap<>();
        for (int i = 0; i < points.size(); i++) grid.computeIfAbsent(cell(points.get(i).getX(), points.get(i).getZ()), c -> new ArrayList<>()).add(i);
        for (int i = 0; i < points.size(); i++) {
            BlockPos pos = points.get(i);
            int cx = Math.floorDiv(pos.getX(), 2 * REACH);
            int cz = Math.floorDiv(pos.getZ(), 2 * REACH);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int j : grid.getOrDefault(BlockPos.asLong(cx + dx, 0, cz + dz), List.of())) {
                        if (j > i && find(group, i) != find(group, j) && within(pos, points.get(j), 2 * REACH)) join(group, i, j);
                    }
                }
            }
        }

        Map<Integer, Set<BlockPos>> bellsOf = new HashMap<>();
        bells.forEach((bell, i) -> bellsOf.computeIfAbsent(find(group, i), g -> new LinkedHashSet<>()).add(bell));
        Map<Integer, List<BlockPos>> anchorsOf = new HashMap<>();
        for (int i = 0; i < points.size(); i++) {
            int root = find(group, i);
            if (bellsOf.containsKey(root)) anchorsOf.computeIfAbsent(root, g -> new ArrayList<>()).add(points.get(i));
        }
        Map<Integer, Village> byRoot = new HashMap<>();
        bellsOf.forEach((root, groupBells) -> byRoot.put(root, new Village(groupBells, anchorsOf.get(root))));
        List<Village> villages = List.copyOf(byRoot.values());

        Map<UUID, Village> byMember = new HashMap<>();
        for (int v = 0; v < villagers.size(); v++) {
            Villager villager = villagers.get(v);
            List<Integer> own = pointsOf.get(v);
            Optional<Village> village = own.isEmpty() ? containing(villages, villager.blockPosition()) : Optional.ofNullable(byRoot.get(find(group, own.get(0))));
            village.ifPresent(found -> {
                found.members.add(villager.getUUID());
                byMember.put(villager.getUUID(), found);
            });
        }
        Index index = new Index(level.getGameTime(), villages, Map.copyOf(byMember));
        INDEX.put(level, index);
        return index;
    }

    /**
     * A banner on a hay bale links the places within reach of it (2x the reach, like any place): found through the archer
     * post POI, which indexes every hay bale, one query per occupied cell of the grid rather than one per place.
     */
    private static void addMarkers(ServerLevel level, List<BlockPos> points) {
        Map<Long, BlockPos> centres = new HashMap<>();
        for (BlockPos point : points) centres.putIfAbsent(cell(point.getX(), point.getZ()), point);
        Set<BlockPos> found = new LinkedHashSet<>();
        for (BlockPos centre : centres.values()) {
            level.getPoiManager().getInRange(type -> type.is(ModVillagers.ARCHER_POST.getKey()), centre, 4 * REACH, net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY)
                    .map(record -> record.getPos())
                    .filter(pos -> level.getBlockState(pos).is(MARKER_BASE) && level.getBlockState(pos.above()).is(net.minecraft.tags.BlockTags.BANNERS))
                    .filter(pos -> points.stream().anyMatch(point -> within(point, pos, 2 * REACH)))
                    .forEach(found::add);
        }
        points.addAll(found);
    }

    private static int add(List<BlockPos> points, BlockPos pos) {
        points.add(pos);
        return points.size() - 1;
    }

    private static long cell(int x, int z) {
        return BlockPos.asLong(Math.floorDiv(x, 2 * REACH), 0, Math.floorDiv(z, 2 * REACH));
    }

    private static void join(int[] group, int a, int b) {
        group[find(group, a)] = find(group, b);
    }

    private static Optional<Village> containing(List<Village> villages, BlockPos pos) {
        return villages.stream().filter(village -> village.contains(pos)).findFirst();
    }

    private static Optional<BlockPos> claimedBell(ServerLevel level, Villager villager) {
        return place(level, villager, MemoryModuleType.MEETING_POINT)
                .filter(pos -> level.getPoiManager().existsAtPosition(PoiTypes.MEETING, pos));
    }

    private static Optional<BlockPos> place(ServerLevel level, Villager villager, MemoryModuleType<GlobalPos> memory) {
        return villager.getBrain().getMemory(memory).filter(pos -> pos.dimension() == level.dimension()).map(GlobalPos::pos);
    }

    private static boolean within(BlockPos a, BlockPos b, int distance) {
        int dx = a.getX() - b.getX();
        int dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz <= distance * distance && Math.abs(a.getY() - b.getY()) <= HEIGHT;
    }

    private static int find(int[] group, int i) {
        while (group[i] != i) {
            group[i] = group[group[i]];
            i = group[i];
        }
        return i;
    }
}
