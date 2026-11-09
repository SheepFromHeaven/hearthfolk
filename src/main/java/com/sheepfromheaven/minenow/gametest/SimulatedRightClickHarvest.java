package com.sheepfromheaven.minenow.gametest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Stands in for mods like Right Click Harvest inside chosen test areas: a right-click on a ripe crop drops its yield and resets it. */
public final class SimulatedRightClickHarvest {
    private static final Set<AABB> AREAS = ConcurrentHashMap.newKeySet();
    private static final java.util.Map<AABB, Integer> HARVESTS = new ConcurrentHashMap<>();
    private static boolean registered;

    private SimulatedRightClickHarvest() {}

    public static void enableIn(GameTestHelper helper) {
        if (!registered) {
            registered = true;
            NeoForge.EVENT_BUS.addListener(SimulatedRightClickHarvest::onRightClick);
        }
        AREAS.add(helper.getBounds());
    }

    public static int harvestsIn(GameTestHelper helper) {
        return HARVESTS.getOrDefault(helper.getBounds(), 0);
    }

    private static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        AABB area = AREAS.stream().filter(bounds -> bounds.contains(Vec3.atCenterOf(event.getPos()))).findFirst().orElse(null);
        if (area == null) return;
        BlockState state = level.getBlockState(event.getPos());
        if (!(state.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(state)) return;
        for (ItemStack drop : Block.getDrops(state, level, event.getPos(), null)) Block.popResource(level, event.getPos(), drop);
        level.setBlockAndUpdate(event.getPos(), crop.getStateForAge(0));
        HARVESTS.merge(area, 1, Integer::sum);
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }
}
