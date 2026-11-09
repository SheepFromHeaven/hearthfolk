package com.sheepfromheaven.minenow.dev;

import com.mojang.brigadier.CommandDispatcher;
import com.sheepfromheaven.minenow.MineNow;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import com.sheepfromheaven.minenow.gametest.TestGroup;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.TestInstanceBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Dev tooling for the {@code gametestPlayground} run ({@code -Dminenow.gametestPlayground=true}): on every login, lays
 * out every {@code minenow:*} test instance in a district per {@link TestGroup} (coloured wool floor and sign), each far from
 * the others and reached through a portal in the hub where the player starts, idle, each with a floating label (readable name,
 * and the failure message once it fails; the block's beacon shows the status), plus an overview board at the start and,
 * behind the arrival point, a lineup of frozen villagers to inspect ({@link PlaygroundShowcase}). {@code /minenow playground runall} starts them all.
 */
public final class GametestPlayground {
    public static final String PROPERTY = "minenow.gametestPlayground";
    private static final int GRID_CELL_SIZE = 32;
    private static final int REFRESH_INTERVAL = 10;

    private record Plot(ServerLevel level, BlockPos block, String name, Display.TextDisplay label) {}

    private static final List<Plot> PLOTS = new ArrayList<>();
    private static final List<Display.TextDisplay> DISTRICT_SIGNS = new ArrayList<>();
    /** Districts sit this far apart (and from the hub) so only one group is ever in view; portals connect them. */
    private static final int DISTRICT_GAP = 1024;
    private static final int DISTRICT_MARGIN = 3;
    private static final int PORTAL_SPACING = 7;
    private static final int PORTAL_COOLDOWN = 40;

    private record Portal(AABB trigger, Vec3 destination, float yaw) {}
    private record HubPortal(TestGroup group, Display.TextDisplay label) {}

    private static final List<Portal> PORTALS = new ArrayList<>();
    private static final List<HubPortal> HUB_PORTALS = new ArrayList<>();
    private static final Map<java.util.UUID, Long> LAST_TELEPORT = new java.util.HashMap<>();
    private static Display.TextDisplay board;
    private static final Map<Display.TextDisplay, Component> SHOWN = new java.util.IdentityHashMap<>();

    private GametestPlayground() {}

    public static boolean enabled() {
        return Boolean.getBoolean(PROPERTY);
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!enabled() || !(event.getEntity() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return;
        var tests = level.registryAccess().lookupOrThrow(Registries.TEST_INSTANCE).listElements()
                .filter(test -> test.key().identifier().getNamespace().equals(MineNow.MOD_ID))
                .sorted(java.util.Comparator.comparing(test -> test.key().identifier().getPath()))
                .toList();
        if (tests.isEmpty()) return;
        level.getGameRules().set(net.minecraft.world.level.gamerules.GameRules.SPAWN_MOBS, false, level.getServer());

        PLOTS.forEach(plot -> plot.label().discard());
        PLOTS.clear();
        DISTRICT_SIGNS.forEach(Display.TextDisplay::discard);
        DISTRICT_SIGNS.clear();
        HUB_PORTALS.forEach(portal -> portal.label().discard());
        HUB_PORTALS.clear();
        PORTALS.clear();
        if (board != null) board.discard();
        SHOWN.clear();

        BlockPos at = player.blockPosition();
        BlockPos origin = new BlockPos(at.getX(), level.getHeight(Heightmap.Types.WORLD_SURFACE, at.getX(), at.getZ()), at.getZ() + 3);
        Map<String, List<String>> byGroup = new LinkedHashMap<>();
        for (TestGroup group : TestGroup.values()) byGroup.put(group.name(), new ArrayList<>());
        tests.forEach(test -> byGroup.get(TestGroup.of(test.key().identifier().getPath()).name()).add(test.key().identifier().getPath()));
        PlaygroundLayout.Plan plan = PlaygroundLayout.plan(byGroup, GRID_CELL_SIZE, DISTRICT_GAP);
        BlockPos districts = origin.offset(DISTRICT_GAP, 0, 0);
        Vec3 hubArrival = Vec3.atBottomCenterOf(origin).add(0, 0, -4);

        List<PlaygroundLayout.District> planned = plan.districts();
        for (int i = 0; i < planned.size(); i++) {
            PlaygroundLayout.District district = planned.get(i);
            TestGroup group = TestGroup.valueOf(district.group());
            forceLoad(level, districts, district);
            paintDistrict(level, districts, district, group);
            double centerX = (district.minX() + district.maxX()) / 2.0;
            Vec3 front = Vec3.atBottomCenterOf(districts).add(centerX, 14, district.minZ() - 4);
            DISTRICT_SIGNS.add(PlaygroundLabels.spawn(level, front, Component.literal(group.title).withStyle(ChatFormatting.BOLD), 6.0F));
            Vec3 entrance = Vec3.atBottomCenterOf(districts).add(centerX, 0, district.minZ() - 4);

            BlockPos hubPortal = origin.offset((int) Math.round((i - (planned.size() - 1) / 2.0) * PORTAL_SPACING), 0, -14);
            PORTALS.add(new Portal(buildPortal(level, hubPortal, group.wall), entrance, 0.0F));
            HUB_PORTALS.add(new HubPortal(group, PlaygroundLabels.spawn(level, Vec3.atBottomCenterOf(hubPortal).add(0.5, 5, 0.5),
                    Component.literal(group.title), 1.5F)));

            BlockPos returnPortal = BlockPos.containing(entrance).offset(0, 0, -6);
            PORTALS.add(new Portal(buildPortal(level, returnPortal, Blocks.WOOL.white()), hubArrival, 180.0F));
            DISTRICT_SIGNS.add(PlaygroundLabels.spawn(level, Vec3.atBottomCenterOf(returnPortal).add(0.5, 5, 0.5),
                    Component.literal("Back to the hub"), 1.5F));
        }
        for (PlaygroundLayout.Plot plannedPlot : plan.plots()) {
            var key = net.minecraft.resources.ResourceKey.create(Registries.TEST_INSTANCE, Identifier.fromNamespaceAndPath(MineNow.MOD_ID, plannedPlot.test()));
            BlockPos pos = districts.offset(plannedPlot.x(), 0, plannedPlot.z());
            level.setBlockAndUpdate(pos, Blocks.TEST_INSTANCE_BLOCK.defaultBlockState());
            if (!(level.getBlockEntity(pos) instanceof TestInstanceBlockEntity block)) continue;
            Vec3i size = TestInstanceBlockEntity.getStructureSize(level, key).orElse(new Vec3i(1, 1, 1));
            block.set(new TestInstanceBlockEntity.Data(Optional.of(key), size, Rotation.NONE, false,
                    TestInstanceBlockEntity.Status.CLEARED, Optional.empty()));
            block.placeStructure();
            Vec3 above = Vec3.atBottomCenterOf(block.getStructurePos()).add(size.getX() / 2.0, size.getY() + 3, size.getZ() / 2.0);
            PLOTS.add(new Plot(level, pos, plannedPlot.test(), PlaygroundLabels.spawn(level, above,
                    PlaygroundLabels.testLabel(plannedPlot.test(), PlaygroundLabels.State.NOT_RUN, Optional.empty()), 2.0F)));
        }
        board = PlaygroundLabels.spawn(level, Vec3.atBottomCenterOf(origin).add(0, 6, -6), Component.literal("MineNow gametests"), 2.5F);
        PlaygroundShowcase.build(level, origin);
        refresh();
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (!enabled() || PLOTS.isEmpty()) return;
        usePortals(PLOTS.getFirst().level());
        PlaygroundShowcase.tick(event.getServer());
        if (event.getServer().getTickCount() % REFRESH_INTERVAL == 0) refresh();
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        if (!enabled()) return;
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("minenow").then(Commands.literal("playground").then(Commands.literal("runall").executes(context -> {
            int started = 0;
            for (Plot plot : PLOTS) {
                if (plot.level().getBlockEntity(plot.block()) instanceof TestInstanceBlockEntity block
                        && PlaygroundLabels.stateOf(block) != PlaygroundLabels.State.RUNNING) {
                    block.runTest(message -> {});
                    started++;
                }
            }
            int count = started;
            context.getSource().sendSuccess(() -> Component.literal("Started " + count + " MineNow gametests"), false);
            return count;
        }))));
    }

    /** Wool floor in the group's colour, so each district is recognisable the moment you step out of its portal. */
    private static void paintDistrict(ServerLevel level, BlockPos origin, PlaygroundLayout.District district, TestGroup group) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = origin.getX() + district.minX() - DISTRICT_MARGIN; x <= origin.getX() + district.maxX() + DISTRICT_MARGIN; x++) {
            for (int z = origin.getZ() + district.minZ() - DISTRICT_MARGIN - 10; z <= origin.getZ() + district.maxZ() + DISTRICT_MARGIN; z++) {
                level.setBlock(pos.set(x, origin.getY() - 1, z), group.floor.defaultBlockState(), 2);
            }
        }
    }

    /** Far-off districts would otherwise sit in unloaded chunks, where tests don't tick and labels can't update. */
    private static void forceLoad(ServerLevel level, BlockPos origin, PlaygroundLayout.District district) {
        int margin = DISTRICT_MARGIN + 16;
        for (int cx = (origin.getX() + district.minX() - margin) >> 4; cx <= (origin.getX() + district.maxX() + margin) >> 4; cx++) {
            for (int cz = (origin.getZ() + district.minZ() - margin) >> 4; cz <= (origin.getZ() + district.maxZ() + margin) >> 4; cz++) {
                level.setChunkForced(cx, cz, true);
            }
        }
    }

    /** A doorway in {@code frame} blocks, two wide and three high; walking through it teleports. */
    private static AABB buildPortal(ServerLevel level, BlockPos base, net.minecraft.world.level.block.Block frame) {
        for (int dx = -1; dx <= 2; dx++) {
            for (int dy = 0; dy <= 3; dy++) {
                boolean edge = dx == -1 || dx == 2 || dy == 3;
                level.setBlockAndUpdate(base.offset(dx, dy, 0), edge ? frame.defaultBlockState() : Blocks.AIR.defaultBlockState());
            }
            level.setBlockAndUpdate(base.offset(dx, -1, 0), frame.defaultBlockState());
        }
        return new AABB(base.getX(), base.getY(), base.getZ() + 0.3, base.getX() + 2, base.getY() + 3, base.getZ() + 0.7);
    }

    private static void usePortals(ServerLevel level) {
        long now = level.getGameTime();
        for (ServerPlayer player : level.players()) {
            if (now - LAST_TELEPORT.getOrDefault(player.getUUID(), Long.MIN_VALUE / 2) < PORTAL_COOLDOWN) continue;
            for (Portal portal : PORTALS) {
                if (!portal.trigger().intersects(player.getBoundingBox())) continue;
                LAST_TELEPORT.put(player.getUUID(), now);
                player.teleportTo(level, portal.destination().x, portal.destination().y, portal.destination().z, java.util.Set.of(), portal.yaw(), 0.0F, true);
                break;
            }
        }
    }

    private static void refresh() {
        Map<PlaygroundLabels.State, Integer> counts = new EnumMap<>(PlaygroundLabels.State.class);
        Map<TestGroup, int[]> perGroup = new EnumMap<>(TestGroup.class);
        List<String> failing = new ArrayList<>();
        for (Plot plot : PLOTS) {
            if (!(plot.level().getBlockEntity(plot.block()) instanceof TestInstanceBlockEntity block)) continue;
            PlaygroundLabels.State state = PlaygroundLabels.stateOf(block);
            counts.merge(state, 1, Integer::sum);
            int[] group = perGroup.computeIfAbsent(TestGroup.of(plot.name()), g -> new int[2]);
            group[0]++;
            if (state == PlaygroundLabels.State.FAILED) group[1]++;
            if (state == PlaygroundLabels.State.FAILED) failing.add(PlaygroundLabels.humanize(plot.name()));
            show(plot.label(), PlaygroundLabels.testLabel(plot.name(), state, block.errorMessage()));
        }
        for (HubPortal portal : HUB_PORTALS) {
            int[] group = perGroup.getOrDefault(portal.group(), new int[2]);
            MutableComponent label = Component.literal(portal.group().title).withStyle(ChatFormatting.BOLD)
                    .append(Component.literal("\n" + group[0] + " tests").withStyle(ChatFormatting.WHITE));
            if (group[1] > 0) label.append(Component.literal(" · " + group[1] + " failed").withStyle(ChatFormatting.RED));
            show(portal.label(), label);
        }
        if (board == null) return;
        MutableComponent text = Component.literal("MineNow gametests\n").withStyle(ChatFormatting.BOLD)
                .append(Component.literal(PlaygroundLabels.summary(counts.getOrDefault(PlaygroundLabels.State.PASSED, 0),
                        counts.getOrDefault(PlaygroundLabels.State.FAILED, 0), counts.getOrDefault(PlaygroundLabels.State.NOT_RUN, 0),
                        counts.getOrDefault(PlaygroundLabels.State.RUNNING, 0))).withStyle(ChatFormatting.WHITE));
        for (String name : failing) text.append(Component.literal("\n✘ " + name).withStyle(ChatFormatting.RED));
        text.append(Component.literal("\n/minenow playground runall").withStyle(ChatFormatting.GRAY));
        show(board, text);
    }

    private static void show(Display.TextDisplay label, Component text) {
        if (text.equals(SHOWN.get(label))) return;
        SHOWN.put(label, text);
        label.setText(text);
    }
}
