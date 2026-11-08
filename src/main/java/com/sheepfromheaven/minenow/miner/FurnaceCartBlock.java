package com.sheepfromheaven.minenow.miner;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A furnace minecart parked off the rails: a working furnace, and the miner's job site. Breaks back into the minecart.
 * Drawn by {@code client.FurnaceCartRenderer} with vanilla's minecart model; the block model only supplies particles.
 */
public class FurnaceCartBlock extends FurnaceBlock {
    private static final VoxelShape SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 15.0, 15.0);

    public FurnaceCartBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    @SuppressWarnings("deprecation")
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return new ItemStack(Items.FURNACE_MINECART);
    }
}
