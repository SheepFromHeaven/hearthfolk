package com.sheepfromheaven.minenow.villager;

import com.sheepfromheaven.minenow.MineNow;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.codec.RegistryCodecs;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import java.util.List;
import java.util.Map;

/**
 * Road tiers come from {@code data/<namespace>/villager_road_tiers/*.json}: a block list (usually a tag under
 * {@code #minenow:villager_roads/}) and the extra pathfinding cost of stepping on it. Open ground costs
 * {@link #OFF_ROAD_COST}; a tier is a road when it costs less. Adding a tier needs only a tag and a tier file.
 * A villager stands on a block when it is under its feet, or when its feet are inside a low one (dirt path, slab).
 */
public final class Roads {
    static final float OFF_ROAD_COST = 1.0F;

    private record Tier(HolderSet<Block> blocks, float cost) {
        static final Codec<Tier> CODEC = RecordCodecBuilder.create(i -> i.group(
                RegistryCodecs.holderSet(Registries.BLOCK).fieldOf("blocks").forGetter(Tier::blocks),
                Codec.floatRange(0.0F, OFF_ROAD_COST).fieldOf("cost").forGetter(Tier::cost)
        ).apply(i, Tier::new));
    }

    private static volatile List<Tier> tiers = List.of();

    private Roads() {}

    /** Extra pathfinding cost of a step whose feet are at {@code feet}: the cheapest tier there, or open ground. */
    static float stepCost(BlockGetter level, BlockPos feet) {
        return Math.min(tierCost(level.getBlockState(feet.below())), tierCost(level.getBlockState(feet)));
    }

    private static float tierCost(BlockState state) {
        float cost = OFF_ROAD_COST;
        for (Tier tier : tiers) {
            if (tier.cost() < cost && state.is(tier.blocks())) cost = tier.cost();
        }
        return cost;
    }

    private static boolean isRoadBlock(BlockState state) {
        return tierCost(state) < OFF_ROAD_COST;
    }

    public static boolean isRoad(BlockGetter level, BlockPos feet) {
        return stepCost(level, feet) < OFF_ROAD_COST;
    }

    /** A spot a villager can stand on: on top of a road block, or inside a low one, with room for its head. */
    static boolean isStandableRoad(BlockGetter level, BlockPos feet) {
        BlockState atFeet = level.getBlockState(feet);
        boolean feetFree = atFeet.getCollisionShape(level, feet).isEmpty();
        boolean headFree = level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty();
        if (!headFree) return false;
        if (feetFree) return isRoadBlock(level.getBlockState(feet.below()));
        return isRoadBlock(atFeet) && atFeet.getCollisionShape(level, feet).max(Direction.Axis.Y) < 1.0;
    }

    public static void onAddReloadListeners(AddServerReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "villager_road_tiers"), new Loader());
    }

    private static final class Loader extends SimpleJsonResourceReloadListener<Tier> {
        Loader() {
            super(Tier.CODEC, FileToIdConverter.json("villager_road_tiers"));
        }

        @Override
        protected void apply(Map<Identifier, Tier> loaded, ResourceManager manager, ProfilerFiller profiler) {
            tiers = List.copyOf(loaded.values());
        }
    }

    /** Villagers, and wandering traders so they keep out of mine shafts too (a travelling merchant stuck down one keeps any other from coming). */
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getEntity() instanceof Villager) && !(event.getEntity() instanceof net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader)) return;
        net.minecraft.world.entity.Mob mob = (net.minecraft.world.entity.Mob) event.getEntity();
        if (mob.getNavigation() instanceof RoadNavigation) return;
        RoadNavigation navigation = new RoadNavigation(mob, level);
        navigation.setCanOpenDoors(mob instanceof Villager);
        navigation.setCanFloat(true);
        navigation.setRequiredPathLength(160.0F);
        mob.navigation = navigation;
    }
}
