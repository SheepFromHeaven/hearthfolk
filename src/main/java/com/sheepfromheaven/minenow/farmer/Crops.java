package com.sheepfromheaven.minenow.farmer;

import com.sheepfromheaven.minenow.MineNow;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
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
 */
public final class Crops {
    public static final TagKey<Block> IGNORED = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "farmer_ignores"));

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

    static boolean hasSeeds(Villager villager) {
        return villager.getInventory().hasAnyMatching(Crops::isSeed);
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
