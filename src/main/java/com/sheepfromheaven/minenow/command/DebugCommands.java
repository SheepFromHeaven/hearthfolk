package com.sheepfromheaven.minenow.command;

import com.mojang.brigadier.CommandDispatcher;
import com.sheepfromheaven.minenow.Config;
import com.sheepfromheaven.minenow.block.ModBlocks;
import com.sheepfromheaven.minenow.block.StructureMarkerBlockEntity;
import com.sheepfromheaven.minenow.dominion.Dominion;
import com.sheepfromheaven.minenow.dominion.DominionInhabitant;
import com.sheepfromheaven.minenow.dominion.DominionSavedData;
import com.sheepfromheaven.minenow.dominion.DominionStructure;
import com.sheepfromheaven.minenow.dominion.RoomDetector;
import com.sheepfromheaven.minenow.dominion.StructureType;
import com.sheepfromheaven.minenow.entity.ModEntities;
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
        level.setBlockAndUpdate(center.offset(1, 0, -1), Blocks.BARREL.defaultBlockState());
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

        return registerStructure(level, dominion, "Debug Fishery", StructureType.FISHERY, center);
    }

    private static boolean buildLumberyard(ServerLevel level, Dominion dominion, BlockPos center) {
        buildRoom(level, center);
        level.setBlockAndUpdate(center.offset(1, 0, 0), Blocks.BARREL.defaultBlockState());

        var logPos = center.south(WALL_RING + 3);
        level.setBlockAndUpdate(logPos, Blocks.OAK_LOG.defaultBlockState());
        level.setBlockAndUpdate(logPos.above(), Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false));

        return registerStructure(level, dominion, "Debug Lumberyard", StructureType.LUMBERYARD, center);
    }

    private static boolean buildTavern(ServerLevel level, Dominion dominion, BlockPos center) {
        buildRoom(level, center);
        level.setBlockAndUpdate(center.offset(1, 0, -1), Blocks.BARREL.defaultBlockState());
        level.setBlockAndUpdate(center.offset(1, 0, 1), Blocks.SMOKER.defaultBlockState());

        return registerStructure(level, dominion, "Debug Tavern", StructureType.TAVERN, center);
    }

    /**
     * Builds a real enclosed 5x5 room (stone-brick floor/walls/ceiling, oak door on the south
     * wall) around {@code center}, which becomes the interior floor tile the marker sits on.
     * Matches what {@link com.sheepfromheaven.minenow.block.StructureMarkerBlock} expects from a
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
        dominion.addInhabitant(new DominionInhabitant(entity.getUUID(), name));
    }
}
