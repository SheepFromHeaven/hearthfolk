package com.sheepfromheaven.minenow.structure.field;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/**
 * The vanilla crops a field's allowance list can name. {@link CropBlock#getBaseSeedId()} would give
 * the seed item directly but is protected, so the handful of vanilla crops are mapped explicitly -
 * carrots and potatoes are their own seed item, wheat and beetroot use a dedicated seed item.
 */
public enum CropKind {
    WHEAT("Wheat", Blocks.WHEAT, Items.WHEAT_SEEDS),
    CARROTS("Carrots", Blocks.CARROTS, Items.CARROT),
    POTATOES("Potatoes", Blocks.POTATOES, Items.POTATO),
    BEETROOT("Beetroot", Blocks.BEETROOTS, Items.BEETROOT_SEEDS);

    private final String displayName;
    private final Block cropBlock;
    private final Item seedItem;

    CropKind(String displayName, Block cropBlock, Item seedItem) {
        this.displayName = displayName;
        this.cropBlock = cropBlock;
        this.seedItem = seedItem;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Block getCropBlock() {
        return cropBlock;
    }

    public Item getSeedItem() {
        return seedItem;
    }

    public static Optional<CropKind> fromState(BlockState state) {
        for (var kind : values()) {
            if (state.is(kind.cropBlock)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }

    public static Optional<CropKind> fromSeedItem(Item item) {
        for (var kind : values()) {
            if (kind.seedItem == item) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }

    public boolean isMature(BlockState state) {
        return state.getBlock() instanceof CropBlock cropBlock && cropBlock.isMaxAge(state);
    }
}
