package com.sheepfromheaven.minenow.item;

import com.sheepfromheaven.minenow.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Right-click a block to instantly build a valid, enclosed 8 (width, x) x 10 (depth, z) x 6
 * (height, y) stone-brick structure (with an oak door and a
 * {@link com.sheepfromheaven.minenow.block.StructureMarkerBlock} at its center) so villages can be
 * stood up without hand-placing every wall. The marker is placed exactly as if the player had
 * placed it themselves, so it registers into the nearby dominion the same way.
 */
public class StructureWandItem extends Item {
    /** Building footprint along x (east/west). */
    private static final int WIDTH = 8;
    /** Building footprint along z (north/south). */
    private static final int DEPTH = 10;
    /** Building footprint along y, including floor and ceiling. */
    private static final int HEIGHT = 6;

    public StructureWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (context.getLevel() instanceof ServerLevel serverLevel && context.getPlayer() instanceof ServerPlayer serverPlayer) {
            buildStructure(serverLevel, serverPlayer, context.getClickedPos());
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * {@code floorCorner} is treated as the building's minimum (west/north/bottom) corner, with the
     * structure extending +x (width), +z (depth), +y (height) from there - so blocks span exactly
     * {@code WIDTH x DEPTH x HEIGHT}, not accidentally one-off due to centering math.
     */
    private static void buildStructure(ServerLevel level, ServerPlayer player, BlockPos floorCorner) {
        int floorY = floorCorner.getY();
        int ceilingY = floorY + HEIGHT - 1;

        for (int dx = 0; dx < WIDTH; dx++) {
            for (int dz = 0; dz < DEPTH; dz++) {
                int x = floorCorner.getX() + dx;
                int z = floorCorner.getZ() + dz;
                level.setBlockAndUpdate(new BlockPos(x, floorY, z), Blocks.STONE_BRICKS.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(x, ceilingY, z), Blocks.STONE_BRICKS.defaultBlockState());

                boolean isWallRing = dx == 0 || dx == WIDTH - 1 || dz == 0 || dz == DEPTH - 1;
                var wallState = isWallRing ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.AIR.defaultBlockState();
                for (int dy = 1; dy < HEIGHT - 1; dy++) {
                    level.setBlockAndUpdate(new BlockPos(x, floorY + dy, z), wallState);
                }
            }
        }

        // Door sits on the wall the player is facing (defaulting to the south wall, center-x, when
        // facing south - the wand's original fixed orientation), rotated to match their direction.
        var facing = player.getDirection();
        int doorX;
        int doorZ;
        switch (facing) {
            case NORTH -> {
                doorX = floorCorner.getX() + WIDTH / 2;
                doorZ = floorCorner.getZ();
            }
            case EAST -> {
                doorX = floorCorner.getX() + WIDTH - 1;
                doorZ = floorCorner.getZ() + DEPTH / 2;
            }
            case WEST -> {
                doorX = floorCorner.getX();
                doorZ = floorCorner.getZ() + DEPTH / 2;
            }
            default -> {
                doorX = floorCorner.getX() + WIDTH / 2;
                doorZ = floorCorner.getZ() + DEPTH - 1;
            }
        }
        var doorLower = new BlockPos(doorX, floorY + 1, doorZ);
        var doorUpper = new BlockPos(doorX, floorY + 2, doorZ);
        level.setBlockAndUpdate(doorLower, Blocks.OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.FACING, facing.getOpposite()).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        level.setBlockAndUpdate(doorUpper, Blocks.OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.FACING, facing.getOpposite()).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));

        var markerPos = new BlockPos(floorCorner.getX() + WIDTH / 2, floorY + 1, floorCorner.getZ() + DEPTH / 2);
        var markerBlock = ModBlocks.STRUCTURE_MARKER.get();
        var markerState = markerBlock.defaultBlockState();
        level.setBlockAndUpdate(markerPos, markerState);
        markerBlock.setPlacedBy(level, markerPos, markerState, player, ItemStack.EMPTY);
    }
}
