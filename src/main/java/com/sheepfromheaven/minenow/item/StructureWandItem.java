package com.sheepfromheaven.minenow.item;

import com.sheepfromheaven.minenow.block.ModBlocks;
import com.sheepfromheaven.minenow.dominion.StructureType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.ArrayList;

/**
 * Right-click a block to instantly build a valid, enclosed 8 (width) x 10 (depth) x 6 (height)
 * stone-brick structure (with an oak door and a
 * {@link com.sheepfromheaven.minenow.block.StructureMarkerBlock} matching the wand's current mode
 * at its center) so villages can be stood up without hand-placing every wall. The clicked block
 * becomes the door's threshold, and the building recedes away from the player in whichever
 * direction they're facing - as if they're standing in front of it looking in. Shift-right-click
 * cycles which {@link StructureType} the wand will build next. The marker is placed exactly as if
 * the player had placed it themselves, so it registers into the nearby dominion the same way.
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

    public static StructureType getMode(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.WAND_MODE.get(), StructureType.HOUSE);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        var player = context.getPlayer();
        if (player != null && player.isShiftKeyDown()) {
            if (player instanceof ServerPlayer serverPlayer) {
                cycleMode(context.getItemInHand(), serverPlayer);
            }
            return InteractionResult.SUCCESS;
        }
        if (context.getLevel() instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            buildStructure(serverLevel, serverPlayer, context.getClickedPos(), getMode(context.getItemInHand()));
        }
        return InteractionResult.SUCCESS;
    }

    private static void cycleMode(ItemStack stack, ServerPlayer player) {
        var types = StructureType.values();
        var next = types[(getMode(stack).ordinal() + 1) % types.length];
        stack.set(ModDataComponents.WAND_MODE.get(), next);
        player.displayClientMessage(Component.literal("Structure Wand: " + next.getDisplayName()), true);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, net.minecraft.world.item.component.TooltipDisplay tooltipDisplay,
                                 java.util.function.Consumer<Component> tooltipAdder, TooltipFlag flag) {
        tooltipAdder.accept(Component.literal("Mode: " + getMode(stack).getDisplayName()));
    }

    /**
     * {@code doorPos} is the block the player clicked, and becomes the door's threshold - the
     * building recedes away from the player in their facing direction (as if they're standing in
     * front of it), centered on {@code doorPos} across the perpendicular (width) axis. {@code right}
     * and {@code forward} form a local coordinate frame: {@code dx} runs sideways (0..WIDTH-1,
     * centered on the door) and {@code dz} runs forward, away from the player, from the door
     * (dz = 0) to the back wall (dz = DEPTH - 1).
     */
    private static void buildStructure(ServerLevel level, ServerPlayer player, BlockPos doorPos, StructureType type) {
        var forward = player.getDirection();
        var right = forward.getClockWise();
        int floorY = doorPos.getY();
        int ceilingY = floorY + HEIGHT - 1;

        for (int dx = 0; dx < WIDTH; dx++) {
            for (int dz = 0; dz < DEPTH; dz++) {
                var floorPos = local(doorPos, right, forward, dx, dz, floorY);
                var ceilingPos = local(doorPos, right, forward, dx, dz, ceilingY);
                level.setBlockAndUpdate(floorPos, Blocks.STONE_BRICKS.defaultBlockState());
                level.setBlockAndUpdate(ceilingPos, Blocks.STONE_BRICKS.defaultBlockState());

                boolean isWallRing = dx == 0 || dx == WIDTH - 1 || dz == 0 || dz == DEPTH - 1;
                var wallState = isWallRing ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.AIR.defaultBlockState();
                for (int dy = 1; dy < HEIGHT - 1; dy++) {
                    level.setBlockAndUpdate(local(doorPos, right, forward, dx, dz, floorY + dy), wallState);
                }
            }
        }

        // Door sits at the clicked position (the near wall, dz = 0), facing back towards the player.
        var doorLower = doorPos.above();
        var doorUpper = doorPos.above(2);
        level.setBlockAndUpdate(doorLower, Blocks.OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.FACING, forward.getOpposite()).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        level.setBlockAndUpdate(doorUpper, Blocks.OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.FACING, forward.getOpposite()).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));

        var markerPos = local(doorPos, right, forward, WIDTH / 2, DEPTH / 2, floorY + 1);
        furnishStructure(level, type, doorPos, right, forward, floorY, markerPos);

        var markerBlock = ModBlocks.getMarkerBlock(type);
        var markerState = markerBlock.defaultBlockState();
        level.setBlockAndUpdate(markerPos, markerState);
        markerBlock.setPlacedBy(level, markerPos, markerState, player, ItemStack.EMPTY);
    }

    /** {@code dx} is centered on the door (0 = leftmost wall, {@code WIDTH / 2} = the door column). */
    private static BlockPos local(BlockPos doorPos, Direction right, Direction forward, int dx, int dz, int y) {
        var pos = doorPos.relative(right, dx - WIDTH / 2).relative(forward, dz);
        return new BlockPos(pos.getX(), y, pos.getZ());
    }

    /**
     * Places the structure type's required blocks (e.g. a barrel and cauldron for a fishery) on the
     * interior floor so the room satisfies {@link StructureType#getRequirements()} as soon as it's
     * built, rather than leaving the player to hunt down and place them by hand.
     */
    private static void furnishStructure(ServerLevel level, StructureType type, BlockPos doorPos, Direction right, Direction forward,
                                          int floorY, BlockPos markerPos) {
        var candidates = new ArrayList<BlockPos>();
        for (int dz = 1; dz < DEPTH - 1; dz++) {
            for (int dx = 1; dx < WIDTH - 1; dx++) {
                var pos = local(doorPos, right, forward, dx, dz, floorY + 1);
                if (!pos.equals(markerPos)) {
                    candidates.add(pos);
                }
            }
        }

        int index = 0;
        for (var requirement : type.getRequirements()) {
            var block = Block.byItem(requirement.icon().get().getItem());
            if (block == Blocks.AIR) {
                continue;
            }
            for (int i = 0; i < requirement.count() && index < candidates.size(); i++, index++) {
                level.setBlockAndUpdate(candidates.get(index), block.defaultBlockState());
            }
        }
    }
}
