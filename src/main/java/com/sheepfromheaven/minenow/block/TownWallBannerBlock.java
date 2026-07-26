package com.sheepfromheaven.minenow.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

public class TownWallBannerBlock extends WallBannerBlock {
    public static final MapCodec<TownWallBannerBlock> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(propertiesCodec()).apply(instance, TownWallBannerBlock::new)
    );

    public TownWallBannerBlock(Properties properties) {
        super(DyeColor.WHITE, properties);
    }

    @SuppressWarnings("unchecked")
    @Override
    public MapCodec<WallBannerBlock> codec() {
        return (MapCodec<WallBannerBlock>) (MapCodec<?>) CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TownBannerBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        TownBannerBehavior.onPlaced(level, pos, placer);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        return TownBannerBehavior.onUse(level, pos, player);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        TownBannerBehavior.onDestroyed(level, pos, player);
        return super.playerWillDestroy(level, pos, state, player);
    }
}
