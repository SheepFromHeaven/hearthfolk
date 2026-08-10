package com.sheepfromheaven.minenow.dominion.townbanner;

import com.sheepfromheaven.minenow.dominion.townbanner.TownBannerBlocks;
import com.sheepfromheaven.minenow.dominion.DominionTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
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
        return TownBannerBlocks.TOWN_BANNER_BE.get();
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

    /** Applies the dominion's designed banner pattern to this placed banner, reusing vanilla's item-component plumbing. */
    public void setBannerPattern(BannerPatternLayers pattern) {
        var stack = new ItemStack(BannerBlock.byColor(getBaseColor()));
        stack.set(DataComponents.BANNER_PATTERNS, pattern);
        applyImplicitComponents(stack);
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
