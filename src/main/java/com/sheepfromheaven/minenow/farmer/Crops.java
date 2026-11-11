package com.sheepfromheaven.minenow.farmer;

import com.sheepfromheaven.minenow.MineNow;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.neoforge.common.SpecialPlantable;

/**
 * What a farmer treats as a field crop: any {@link CropBlock} or anything in {@code #minecraft:crops} (where crop
 * mods register theirs), minus stems and {@code #minenow:farmer_ignores}. Ripe means maximum age, read from the
 * block's {@code age} property when it isn't a CropBlock. Seeds are items that plant such a crop.
 * Stem crops are separate: from journeyman a farmer plants pumpkin stems (expert: melons) where the fruit keeps a free cell beside them, and
 * harvests the fruit that grew from an attached stem ({@code #minenow:farmer_fruits}).
 */
public final class Crops {
    public static final TagKey<Block> IGNORED = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "farmer_ignores"));
    public static final TagKey<Block> FRUITS = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "farmer_fruits"));
    private static final int PUMPKIN_LEVEL = 3;
    private static final int OTHER_FRUIT_LEVEL = 4;
    /** Another stem within this many blocks (either way) keeps a farmer from planting one, so stems never take a field over. */
    static final int STEM_SPACING = 2;

    private Crops() {}

    public static boolean isCrop(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof StemBlock || block instanceof AttachedStemBlock || state.is(IGNORED)) return false;
        return block instanceof CropBlock || state.is(BlockTags.CROPS);
    }

    public static boolean isRipe(BlockState state) {
        if (!isCrop(state)) return false;
        return state.getBlock() instanceof CropBlock crop ? crop.isMaxAge(state) : atMaxAge(state);
    }

    public static boolean isGrowing(BlockState state) {
        return isCrop(state) && !isRipe(state);
    }

    public static boolean isSeed(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem item) return isCrop(item.getBlock().defaultBlockState());
        return stack.getItem() instanceof SpecialPlantable;
    }

    public static boolean isStem(BlockState state) {
        return state.getBlock() instanceof StemBlock || state.getBlock() instanceof AttachedStemBlock;
    }

    /** An item that plants a stem: pumpkin and melon seeds, and the seeds of modded stems. */
    public static boolean isStemSeed(ItemStack stack) {
        return stack.getItem() instanceof BlockItem item && item.getBlock() instanceof StemBlock;
    }

    public static boolean isFruit(BlockState state) {
        return state.is(FRUITS);
    }

    /** The villager level a farmer needs to grow and harvest this fruit or the stem that bears it: pumpkins journeyman, the rest expert. */
    public static int fruitLevel(Block block) {
        return block == Blocks.PUMPKIN || block == Blocks.PUMPKIN_STEM || block == Blocks.ATTACHED_PUMPKIN_STEM ? PUMPKIN_LEVEL : OTHER_FRUIT_LEVEL;
    }

    /** A fruit with an attached stem pointing at it, of a kind a farmer of this level harvests. Pumpkins the player placed have no stem and are never taken. */
    public static boolean isHarvestableFruit(BlockGetter level, BlockPos pos, int villagerLevel) {
        BlockState state = level.getBlockState(pos);
        if (!isFruit(state) || villagerLevel < fruitLevel(state.getBlock())) return false;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockState beside = level.getBlockState(pos.relative(side));
            if (beside.getBlock() instanceof AttachedStemBlock && beside.getValue(AttachedStemBlock.FACING) == side.getOpposite()) return true;
        }
        return false;
    }

    /** Whether a stem could bear fruit in this cell: nothing in it, and the soil a stem's fruit needs below. */
    public static boolean isFruitCell(BlockGetter level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        return level.getBlockState(pos).isAir() && (below.is(BlockTags.DIRT) || Field.isFarmland(below));
    }

    /**
     * Whether this cell is the last one a stem beside it has for its fruit: filling it would leave that stem barren.
     * A fruit already standing beside the stem counts as room.
     */
    public static boolean isLastFruitCell(BlockGetter level, BlockPos pos) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos stem = pos.relative(side);
            if (!isStem(level.getBlockState(stem))) continue;
            if (!hasFruitRoom(level, stem, pos)) return true;
        }
        return false;
    }

    private static boolean hasFruitRoom(BlockGetter level, BlockPos stem, BlockPos except) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos beside = stem.relative(side);
            if (beside.equals(except)) continue;
            if (isFruit(level.getBlockState(beside)) || isFruitCell(level, beside)) return true;
        }
        return false;
    }

    /** A stem may go here when a farmer of this level grows it, its fruit has a cell that stays free, and no other stem is close. */
    static boolean canPlantStem(BlockGetter level, BlockPos pos, ItemStack seeds, int villagerLevel) {
        if (!isStemSeed(seeds) || !(seeds.getItem() instanceof BlockItem item) || villagerLevel < fruitLevel(item.getBlock())) return false;
        if (!hasFruitRoom(level, pos, pos)) return false;
        for (BlockPos near : BlockPos.betweenClosed(pos.offset(-STEM_SPACING, 0, -STEM_SPACING), pos.offset(STEM_SPACING, 0, STEM_SPACING))) {
            if (!near.equals(pos) && isStem(level.getBlockState(near))) return false;
        }
        return true;
    }

    private static boolean atMaxAge(BlockState state) {
        for (Property<?> property : state.getProperties()) {
            if (property instanceof IntegerProperty age && property.getName().equals("age")) {
                int max = age.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(0);
                return state.getValue(age) == max;
            }
        }
        return false;
    }
}
