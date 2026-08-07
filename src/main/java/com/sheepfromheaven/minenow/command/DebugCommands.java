package com.sheepfromheaven.minenow.command;

import com.mojang.brigadier.CommandDispatcher;
import com.sheepfromheaven.minenow.core.Config;
import com.sheepfromheaven.minenow.core.ModBlocks;
import com.sheepfromheaven.minenow.structure.block.StructureMarkerBlockEntity;
import com.sheepfromheaven.minenow.dominion.Dominion;
import com.sheepfromheaven.minenow.dominion.DominionInhabitant;
import com.sheepfromheaven.minenow.dominion.DominionSavedData;
import com.sheepfromheaven.minenow.structure.DominionStructure;
import com.sheepfromheaven.minenow.structure.RoomDetector;
import com.sheepfromheaven.minenow.structure.StructureType;
import com.sheepfromheaven.minenow.inhabitant.ModEntities;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.UUID;

/**
 * Dev-only shortcut for manual playtesting: builds an actual enclosed room for each structure
 * type (walls, floor, ceiling, door) with its requirements already inside, runs the same
 * RoomDetector flood-fill a player-placed marker would use, and spawns free inhabitants - so a
 * whole testable village can be stood up in one command instead of building every structure and
 * recruiting by hand.
 */
public class DebugCommands {
    private static final String[] DEBUG_NAMES = {
            "Test Ada", "Test Bjorn", "Test Clara", "Test Daren", "Test Elena", "Test Finn"
    };
    private static final int STRUCTURE_SPACING = 16;
    /**
     * Interior is a 5x5 floor (offsets -2..2) x 2 layers tall, well above HOUSE's 32-block
     * minSize once furniture and the marker are excluded; the wall ring sits 3 blocks out from
     * the center.
     */
    private static final int WALL_RING = 3;

    private DebugCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("minenow")
                .then(Commands.literal("debugvillage")
                        .then(Commands.argument("inhabitants", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 32))
                                .executes(ctx -> run(ctx.getSource(), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "inhabitants"))))
                        .executes(ctx -> run(ctx.getSource(), 4))));
    }

    private static int run(CommandSourceStack source, int inhabitantCount) {
        if (!(source.getLevel() instanceof ServerLevel level)) {
            return 0;
        }
        var origin = BlockPos.containing(source.getPosition());
        var data = DominionSavedData.get(level);
        var ownerUuid = source.getPlayer() != null ? source.getPlayer().getUUID() : UUID.randomUUID();

        var dominion = data.findDominionContaining(origin);
        if (dominion == null) {
            var bannerPos = origin.north(WALL_RING + 3);
            level.setBlockAndUpdate(bannerPos, ModBlocks.TOWN_BANNER.get().defaultBlockState());
            dominion = data.createDominion(ownerUuid, "Debug Village", bannerPos);
        }

        boolean ok = true;
        ok &= buildHouse(level, dominion, origin);
        ok &= buildFishery(level, dominion, origin.east(STRUCTURE_SPACING));
        ok &= buildLumberyard(level, dominion, origin.east(STRUCTURE_SPACING * 2));
        ok &= buildTavern(level, dominion, origin.east(STRUCTURE_SPACING * 3));

        for (int i = 0; i < inhabitantCount; i++) {
            spawnInhabitant(level, dominion, origin.south(WALL_RING + 2 + i), DEBUG_NAMES[i % DEBUG_NAMES.length] + " " + (i + 1));
        }

        data.setDirty();
        if (ok) {
            source.sendSuccess(() -> Component.literal("Debug village set up: 4 structures, " + inhabitantCount + " inhabitants."), false);
        } else {
            source.sendFailure(Component.literal("Debug village set up, but at least one room failed to enclose - check logs."));
        }
        return ok ? 1 : 0;
    }

    private static boolean buildHouse(ServerLevel level, Dominion dominion, BlockPos center) {
        buildRoom(level, center);
        var bedHead = center.offset(-1, 0, 0);
        var bedFoot = bedHead.north();
        level.setBlockAndUpdate(bedFoot, Blocks.RED_BED.defaultBlockState()
                .setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT));
        level.setBlockAndUpdate(bedHead, Blocks.RED_BED.defaultBlockState()
                .setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD));

        return registerStructure(level, dominion, "Debug House", StructureType.HOUSE, center);
    }

    private static boolean buildFishery(ServerLevel level, Dominion dominion, BlockPos center) {
        buildRoom(level, center);
        var barrelPos = center.offset(1, 0, -1);
        level.setBlockAndUpdate(barrelPos, Blocks.BARREL.defaultBlockState());
        putInBarrel(level, barrelPos, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.FISHING_ROD));
        level.setBlockAndUpdate(center.offset(1, 0, 0), Blocks.CRAFTING_TABLE.defaultBlockState());
        level.setBlockAndUpdate(center.offset(1, 0, 1), Blocks.SMOKER.defaultBlockState());

        var waterPos = center.south(WALL_RING + 3).below();
        level.setBlockAndUpdate(waterPos.north(), Blocks.DIRT.defaultBlockState());
        level.setBlockAndUpdate(waterPos.south(), Blocks.DIRT.defaultBlockState());
        level.setBlockAndUpdate(waterPos.east(), Blocks.DIRT.defaultBlockState());
        level.setBlockAndUpdate(waterPos.west(), Blocks.DIRT.defaultBlockState());
        level.setBlockAndUpdate(waterPos.below(), Blocks.DIRT.defaultBlockState());
        level.setBlockAndUpdate(waterPos, Blocks.WATER.defaultBlockState());
        level.setBlockAndUpdate(waterPos.above(), Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(waterPos.above(2), Blocks.AIR.defaultBlockState());
        // Clear the rim above each dirt wall so ShoreScanner has a standable tile to fish from -
        // otherwise the surrounding (unexcavated) terrain may leave every rim tile blocked too.
        for (var dir : Direction.Plane.HORIZONTAL) {
            var rimPos = waterPos.above().relative(dir);
            level.setBlockAndUpdate(rimPos, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(rimPos.above(), Blocks.AIR.defaultBlockState());
        }

        return registerStructure(level, dominion, "Debug Fishery", StructureType.FISHERY, center);
    }

    private static boolean buildLumberyard(ServerLevel level, Dominion dominion, BlockPos center) {
        buildRoom(level, center);
        var barrelPos = center.offset(1, 0, 0);
        level.setBlockAndUpdate(barrelPos, Blocks.BARREL.defaultBlockState());
        putInBarrel(level, barrelPos, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_AXE));

        buildTree(level, center.south(WALL_RING + 3));

        return registerStructure(level, dominion, "Debug Lumberyard", StructureType.LUMBERYARD, center);
    }

    private static boolean buildTavern(ServerLevel level, Dominion dominion, BlockPos center) {
        buildRoom(level, center);
        var barrelPos = center.offset(1, 0, -1);
        level.setBlockAndUpdate(barrelPos, Blocks.BARREL.defaultBlockState());
        putInBarrel(level, barrelPos, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BEEF, 4));
        putInBarrel(level, barrelPos, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COAL, 8));
        level.setBlockAndUpdate(center.offset(1, 0, 1), Blocks.SMOKER.defaultBlockState());

        return registerStructure(level, dominion, "Debug Tavern", StructureType.TAVERN, center);
    }

    /** Drops the required tool/ingredient stack straight into the freshly-placed barrel's inventory. */
    private static void putInBarrel(ServerLevel level, BlockPos barrelPos, net.minecraft.world.item.ItemStack stack) {
        if (level.getBlockEntity(barrelPos) instanceof net.minecraft.world.Container container) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                if (container.getItem(i).isEmpty()) {
                    container.setItem(i, stack);
                    return;
                }
            }
        }
    }

    /**
     * Builds a real naturally-grown tree {@link com.sheepfromheaven.minenow.jobs.lumberjack.TreeScanner}
     * will recognize: soil below the trunk, and a non-persistent leaf horizontally adjacent to a
     * trunk log (a leaf directly above the trunk doesn't count - {@link TreeScanner} only checks
     * each log's horizontal neighbors).
     */
    private static void buildTree(ServerLevel level, BlockPos base) {
        int trunkHeight = 3;
        level.setBlockAndUpdate(base.below(), Blocks.GRASS_BLOCK.defaultBlockState());
        for (int i = 0; i < trunkHeight; i++) {
            level.setBlockAndUpdate(base.above(i), Blocks.OAK_LOG.defaultBlockState());
        }
        var topLog = base.above(trunkHeight - 1);
        for (var dir : Direction.Plane.HORIZONTAL) {
            level.setBlockAndUpdate(topLog.relative(dir), Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false));
        }
    }

    /**
     * Builds a real enclosed 5x5 room (stone-brick floor/walls/ceiling, oak door on the south
     * wall) around {@code center}, which becomes the interior floor tile the marker sits on.
     * Matches what {@link com.sheepfromheaven.minenow.structure.block.StructureMarkerBlock} expects from a
     * player-built house, so the same {@link RoomDetector} flood-fill recognizes it.
     */
    private static void buildRoom(ServerLevel level, BlockPos center) {
        int floorY = center.getY() - 1;
        int wallY1 = center.getY();
        int wallY2 = center.getY() + 1;
        int ceilingY = center.getY() + 2;

        for (int dx = -WALL_RING; dx <= WALL_RING; dx++) {
            for (int dz = -WALL_RING; dz <= WALL_RING; dz++) {
                level.setBlockAndUpdate(new BlockPos(center.getX() + dx, floorY, center.getZ() + dz), Blocks.STONE_BRICKS.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(center.getX() + dx, ceilingY, center.getZ() + dz), Blocks.STONE_BRICKS.defaultBlockState());

                boolean isWallRing = Math.abs(dx) == WALL_RING || Math.abs(dz) == WALL_RING;
                var wallState = isWallRing ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.AIR.defaultBlockState();
                level.setBlockAndUpdate(new BlockPos(center.getX() + dx, wallY1, center.getZ() + dz), wallState);
                level.setBlockAndUpdate(new BlockPos(center.getX() + dx, wallY2, center.getZ() + dz), wallState);
            }
        }

        var doorLower = new BlockPos(center.getX(), wallY1, center.getZ() + WALL_RING);
        var doorUpper = new BlockPos(center.getX(), wallY2, center.getZ() + WALL_RING);
        level.setBlockAndUpdate(doorLower, Blocks.OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.FACING, Direction.NORTH).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        level.setBlockAndUpdate(doorUpper, Blocks.OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.FACING, Direction.NORTH).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));

        level.setBlockAndUpdate(center, ModBlocks.STRUCTURE_MARKER.get().defaultBlockState());
    }

    private static boolean registerStructure(ServerLevel level, Dominion dominion, String name, StructureType type,
                                              BlockPos markerPos) {
        var result = RoomDetector.detect(level, markerPos, Config.maxRoomSize);
        if (!(result instanceof RoomDetector.Result.Success(var blocks))) {
            return false;
        }

        var structure = new DominionStructure(name, type, markerPos, new java.util.HashSet<>(blocks));
        dominion.addStructure(structure);
        if (level.getBlockEntity(markerPos) instanceof StructureMarkerBlockEntity be) {
            be.setStructureData(structure.getName(), structure.getType(), dominion.getId(), structure.getId());
            be.refreshStatuses(structure, level);
        }
        return true;
    }

    private static void spawnInhabitant(ServerLevel level, Dominion dominion, BlockPos pos, String name) {
        var entity = ModEntities.INHABITANT.get().create(level, EntitySpawnReason.COMMAND);
        if (entity == null) {
            return;
        }
        entity.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        entity.setCustomName(Component.literal(name));
        entity.setCustomNameVisible(true);
        entity.setDominionId(dominion.getId());
        level.addFreshEntity(entity);
        if (!dominion.addInhabitant(new DominionInhabitant(entity.getUUID(), name))) {
            // Debug-only spawn helper; if the dominion is already at capacity just discard the extra
            // entity rather than leaving it in the world with no dominion record.
            entity.discard();
        }
    }
}
