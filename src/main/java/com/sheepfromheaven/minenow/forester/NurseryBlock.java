package com.sheepfromheaven.minenow.forester;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A sapling potted on a log: the forester's job site. Looks like vanilla's potted sapling (its models reuse vanilla's)
 * and turns back into one when the log under it goes. Taking the sapling out leaves an empty flower pot, as in vanilla.
 */
public class NurseryBlock extends Block {
    public static final EnumProperty<NurseryTree> TREE = EnumProperty.create("tree", NurseryTree.class);
    private static final VoxelShape SHAPE = Block.box(5.0, 0.0, 5.0, 11.0, 6.0, 11.0);

    public NurseryBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(TREE, NurseryTree.OAK));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TREE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
                                     BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
        if (direction == Direction.DOWN && !neighbour.is(BlockTags.LOGS)) return state.getValue(TREE).potted().defaultBlockState();
        return super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbour, random);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        ItemStack sapling = new ItemStack(state.getValue(TREE).sapling());
        if (!player.addItem(sapling)) Block.popResource(level, pos, sapling);
        level.setBlock(pos, Blocks.FLOWER_POT.defaultBlockState(), 3);
        return InteractionResult.SUCCESS;
    }

    @Override
    @SuppressWarnings("deprecation")
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return new ItemStack(state.getValue(TREE).sapling());
    }
}
