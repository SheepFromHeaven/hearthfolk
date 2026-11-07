package com.sheepfromheaven.minenow.villager;

import com.sheepfromheaven.minenow.MineNow;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Roads are blocks in {@code #minenow:villager_roads}, split into three tiers by how much better they are than open
 * ground: natural (dirt path), rough (gravel, cobble) and manufactured (bricks, planks, slabs, stairs). A villager
 * stands on a road when the block under its feet is one, or when its feet are inside a low one (dirt path, slab).
 */
public final class Roads {
    public static final TagKey<Block> TAG = tag("villager_roads");
    private static final TagKey<Block> NATURAL = tag("villager_roads/natural");
    private static final TagKey<Block> ROUGH = tag("villager_roads/rough");
    private static final TagKey<Block> MANUFACTURED = tag("villager_roads/manufactured");

    static final float OFF_ROAD_COST = 1.0F;
    static final float NATURAL_COST = 0.75F;
    static final float ROUGH_COST = 0.4F;
    static final float MANUFACTURED_COST = 0.0F;

    private static TagKey<Block> tag(String path) {
        return TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(MineNow.MOD_ID, path));
    }

    /** Extra pathfinding cost of a step whose feet are at {@code feet}: the best road tier there, or open ground. */
    static float stepCost(BlockGetter level, BlockPos feet) {
        return Math.min(tierCost(level.getBlockState(feet.below())), tierCost(level.getBlockState(feet)));
    }

    private static float tierCost(BlockState state) {
        if (state.is(MANUFACTURED)) return MANUFACTURED_COST;
        if (state.is(ROUGH)) return ROUGH_COST;
        if (state.is(NATURAL)) return NATURAL_COST;
        return OFF_ROAD_COST;
    }

    private Roads() {}

    public static boolean isRoad(BlockGetter level, BlockPos feet) {
        return level.getBlockState(feet.below()).is(TAG) || level.getBlockState(feet).is(TAG);
    }

    /** A spot a villager can stand on: on top of a road block, or inside a low one, with room for its head. */
    static boolean isStandableRoad(BlockGetter level, BlockPos feet) {
        BlockState atFeet = level.getBlockState(feet);
        boolean feetFree = atFeet.getCollisionShape(level, feet).isEmpty();
        boolean headFree = level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty();
        if (!headFree) return false;
        if (feetFree) return level.getBlockState(feet.below()).is(TAG);
        return atFeet.is(TAG) && atFeet.getCollisionShape(level, feet).max(net.minecraft.core.Direction.Axis.Y) < 1.0;
    }

    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof Villager villager)) return;
        if (villager.getNavigation() instanceof RoadNavigation) return;
        RoadNavigation navigation = new RoadNavigation(villager, level);
        navigation.setCanOpenDoors(true);
        navigation.setCanFloat(true);
        navigation.setRequiredPathLength(48.0F);
        villager.navigation = navigation;
    }
}
