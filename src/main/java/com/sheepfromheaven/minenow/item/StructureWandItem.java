package com.sheepfromheaven.minenow.item;

import com.sheepfromheaven.minenow.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
 * Right-click a block to instantly build a valid, enclosed 5x5x5 stone-brick structure (with an
 * oak door and a {@link com.sheepfromheaven.minenow.block.StructureMarkerBlock} at its center) so
 * villages can be stood up without hand-placing every wall. The marker is placed exactly as if
 * the player had placed it themselves, so it registers into the nearby dominion the same way.
 */
public class StructureWandItem extends Item {
    /** Offsets run -RING..RING on x/z and 1..2*RING-1 on y, giving a 5x5x5 outer shell. */
    private static final int RING = 2;

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

    private static void buildStructure(ServerLevel level, ServerPlayer player, BlockPos floorCenter) {
        int floorY = floorCenter.getY();
        int ceilingY = floorY + 2 * RING;

        for (int dx = -RING; dx <= RING; dx++) {
            for (int dz = -RING; dz <= RING; dz++) {
                int x = floorCenter.getX() + dx;
                int z = floorCenter.getZ() + dz;
                level.setBlockAndUpdate(new BlockPos(x, floorY, z), Blocks.STONE_BRICKS.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(x, ceilingY, z), Blocks.STONE_BRICKS.defaultBlockState());

                boolean isWallRing = Math.abs(dx) == RING || Math.abs(dz) == RING;
                var wallState = isWallRing ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.AIR.defaultBlockState();
                for (int dy = 1; dy < 2 * RING; dy++) {
                    level.setBlockAndUpdate(new BlockPos(x, floorY + dy, z), wallState);
                }
            }
        }

        var doorLower = new BlockPos(floorCenter.getX(), floorY + 1, floorCenter.getZ() + RING);
        var doorUpper = new BlockPos(floorCenter.getX(), floorY + 2, floorCenter.getZ() + RING);
        level.setBlockAndUpdate(doorLower, Blocks.OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.FACING, Direction.NORTH).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        level.setBlockAndUpdate(doorUpper, Blocks.OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.FACING, Direction.NORTH).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));

        var markerPos = new BlockPos(floorCenter.getX(), floorY + 1, floorCenter.getZ());
        var markerBlock = ModBlocks.STRUCTURE_MARKER.get();
        var markerState = markerBlock.defaultBlockState();
        level.setBlockAndUpdate(markerPos, markerState);
        markerBlock.setPlacedBy(level, markerPos, markerState, player, ItemStack.EMPTY);
    }
}
