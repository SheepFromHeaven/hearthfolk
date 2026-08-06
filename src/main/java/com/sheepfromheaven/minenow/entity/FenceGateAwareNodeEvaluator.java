package com.sheepfromheaven.minenow.entity;

import com.sheepfromheaven.minenow.dominion.DominionPathNode;
import com.sheepfromheaven.minenow.dominion.DominionSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

import java.util.List;

/**
 * Vanilla's {@link WalkNodeEvaluator} classifies closed fence gates as {@link PathType#FENCE},
 * the same bucket as a plain, never-openable fence or wall — there's no per-mob malus that can
 * tell those apart, since {@code getPathTypeOfMob} returns {@code FENCE} for that whole bucket
 * before any malus lookup happens. Reporting closed gates as {@code DOOR_WOOD_CLOSED} instead
 * reuses the existing (already-working) door traversal logic: it's walkable when the mob's
 * navigation has {@code canOpenDoors} set, and {@link OpenFenceGateGoal} does the actual opening.
 *
 * <p>Also applies the {@link DominionPathNode.Kind#STREET} soft-hint bias here, at the pathfinding-cost
 * level, rather than only in an idle-wander goal: a malus penalty that scales with distance to the
 * nearest street node means every path search (going home, going to work, wandering) is nudged
 * toward routing near/through nodes when a reasonably comparable alternative exists. The penalty is
 * a linear ramp from {@code 0} right at a node up to {@link #MAX_MALUS_PENALTY} at
 * {@link #NODE_INFLUENCE_RADIUS} away (and flat beyond that) rather than a flat in-radius/out-of-radius
 * step - a step function makes every tile inside the radius equally cheap, so the path just clips the
 * edge of the zone instead of actually bending toward the node; the ramp keeps pulling it closer for
 * as long as getting closer is cheaper. The pathfinder's cost is additive per step
 * ({@code g = distance + costMalus}, see {@code PathFinder.findPath}), so a detour only wins when the
 * extra distance it costs is less than the malus it avoids - this is still a genuine soft hint (a
 * direct route with no nearby node is never blocked), but the penalty needs to be large relative to
 * typical detour lengths to actually show up rather than getting lost in the noise of a route that's
 * only marginally shorter.
 */
public class FenceGateAwareNodeEvaluator extends WalkNodeEvaluator {
    private static final float MAX_MALUS_PENALTY = 2.5F;
    private static final double NODE_INFLUENCE_RADIUS = 6.0;

    private List<BlockPos> streetNodePositions = List.of();

    @Override
    public void prepare(PathNavigationRegion region, Mob mob) {
        super.prepare(region, mob);
        streetNodePositions = List.of();
        if (mob instanceof InhabitantEntity inhabitant && mob.level() instanceof ServerLevel serverLevel) {
            var dominionId = inhabitant.getDominionId();
            if (dominionId != null) {
                var dominion = DominionSavedData.get(serverLevel).getDominionById(dominionId);
                if (dominion != null) {
                    streetNodePositions = dominion.getPathNodes().stream()
                            .filter(n -> n.getKind() == DominionPathNode.Kind.STREET)
                            .map(DominionPathNode::getPos)
                            .toList();
                }
            }
        }
    }

    @Override
    public void done() {
        streetNodePositions = List.of();
        super.done();
    }

    @Override
    public PathType getPathType(PathfindingContext context, int x, int y, int z) {
        var state = context.getBlockState(new BlockPos(x, y, z));
        if (state.getBlock() instanceof FenceGateBlock) {
            return state.getValue(FenceGateBlock.OPEN) ? PathType.DOOR_OPEN : PathType.DOOR_WOOD_CLOSED;
        }
        return super.getPathType(context, x, y, z);
    }

    @Override
    public int getNeighbors(Node[] neighbors, Node node) {
        int count = super.getNeighbors(neighbors, node);
        if (!streetNodePositions.isEmpty()) {
            for (int i = 0; i < count; i++) {
                var neighbor = neighbors[i];
                if (neighbor != null && neighbor.costMalus >= 0.0F) {
                    neighbor.costMalus += nodeDistancePenalty(neighbor);
                }
            }
        }
        return count;
    }

    /**
     * {@code 0} right at the nearest street node, ramping linearly up to {@link #MAX_MALUS_PENALTY}
     * at {@link #NODE_INFLUENCE_RADIUS} blocks away, flat beyond that - a continuous gradient rather
     * than a single in/out-of-radius step, so the pathfinder keeps preferring tiles closer to the
     * node instead of treating the whole radius as equally good.
     */
    private float nodeDistancePenalty(Node node) {
        double nearestDistSq = Double.MAX_VALUE;
        for (var pos : streetNodePositions) {
            double dx = pos.getX() - node.x;
            double dy = pos.getY() - node.y;
            double dz = pos.getZ() - node.z;
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq < nearestDistSq) {
                nearestDistSq = distSq;
            }
        }
        double t = Math.min(1.0, Math.sqrt(nearestDistSq) / NODE_INFLUENCE_RADIUS);
        return (float) (MAX_MALUS_PENALTY * t);
    }
}
