package com.sheepfromheaven.minenow.block;

import com.sheepfromheaven.minenow.dominion.DominionTier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class TownBannerBlockEntity extends BannerBlockEntity {
    private String dominionName = "";
    private DominionTier tier = DominionTier.SETTLEMENT;

    public TownBannerBlockEntity(BlockPos pos, BlockState state) {
        this(pos, state, ((AbstractBannerBlock) state.getBlock()).getColor());
    }

    public TownBannerBlockEntity(BlockPos pos, BlockState state, DyeColor color) {
        super(pos, state, color);
    }

    @Override
    public BlockEntityType<?> getType() {
        return ModBlocks.TOWN_BANNER_BE.get();
    }

    public String getDominionName() {
        return dominionName;
    }

    public DominionTier getDominionTier() {
        return tier;
    }

    public void setDominionData(String name, DominionTier tier) {
        this.dominionName = name;
        this.tier = tier;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("DominionName", dominionName);
        output.putString("DominionTier", tier.name());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        dominionName = input.getStringOr("DominionName", "");
        tier = DominionTier.valueOf(input.getStringOr("DominionTier", "SETTLEMENT"));
    }
}
