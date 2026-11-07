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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.TestInstanceBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Dev tooling for the {@code gametestPlayground} run ({@code -Dminenow.gametestPlayground=true}): on every login, lays
 * out every {@code minenow:*} test instance in a grid near the player, idle, each with a floating label (readable name,
 * and the failure message once it fails; the block's beacon shows the status), plus an overview board at the start. {@code /minenow playground runall} starts them all.
 */
public final class GametestPlayground {
    public static final String PROPERTY = "minenow.gametestPlayground";
    private static final int GRID_CELL_SIZE = 32;
    private static final int REFRESH_INTERVAL = 10;

    private record Plot(ServerLevel level, BlockPos block, String name, Display.TextDisplay label) {}

    private static final List<Plot> PLOTS = new ArrayList<>();
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

        PLOTS.forEach(plot -> plot.label().discard());
        PLOTS.clear();
        if (board != null) board.discard();
        SHOWN.clear();

        BlockPos at = player.blockPosition();
        BlockPos origin = new BlockPos(at.getX(), level.getHeight(Heightmap.Types.WORLD_SURFACE, at.getX(), at.getZ()), at.getZ() + 3);
        int perRow = (int) Math.ceil(Math.sqrt(tests.size()));
        for (int i = 0; i < tests.size(); i++) {
            var key = tests.get(i).key();
            BlockPos pos = origin.offset((i % perRow) * GRID_CELL_SIZE, 0, (i / perRow) * GRID_CELL_SIZE);
            level.setBlockAndUpdate(pos, Blocks.TEST_INSTANCE_BLOCK.defaultBlockState());
            if (!(level.getBlockEntity(pos) instanceof TestInstanceBlockEntity block)) continue;
            Vec3i size = TestInstanceBlockEntity.getStructureSize(level, key).orElse(new Vec3i(1, 1, 1));
            block.set(new TestInstanceBlockEntity.Data(Optional.of(key), size, Rotation.NONE, false,
                    TestInstanceBlockEntity.Status.CLEARED, Optional.empty()));
            block.placeStructure();
            Vec3 above = Vec3.atBottomCenterOf(block.getStructurePos()).add(size.getX() / 2.0, size.getY() + 3, size.getZ() / 2.0);
            String name = key.identifier().getPath();
            PLOTS.add(new Plot(level, pos, name, PlaygroundLabels.spawn(level, above,
                    PlaygroundLabels.testLabel(name, PlaygroundLabels.State.NOT_RUN, Optional.empty()), 2.0F)));
        }
        board = PlaygroundLabels.spawn(level, Vec3.atBottomCenterOf(origin).add(0, 6, -6), Component.literal("MineNow gametests"), 2.5F);
        refresh();
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (!enabled() || PLOTS.isEmpty() || event.getServer().getTickCount() % REFRESH_INTERVAL != 0) return;
        refresh();
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

    private static void refresh() {
        Map<PlaygroundLabels.State, Integer> counts = new EnumMap<>(PlaygroundLabels.State.class);
        List<String> failing = new ArrayList<>();
        for (Plot plot : PLOTS) {
            if (!(plot.level().getBlockEntity(plot.block()) instanceof TestInstanceBlockEntity block)) continue;
            PlaygroundLabels.State state = PlaygroundLabels.stateOf(block);
            counts.merge(state, 1, Integer::sum);
            if (state == PlaygroundLabels.State.FAILED) failing.add(PlaygroundLabels.humanize(plot.name()));
            show(plot.label(), PlaygroundLabels.testLabel(plot.name(), state, block.errorMessage()));
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
