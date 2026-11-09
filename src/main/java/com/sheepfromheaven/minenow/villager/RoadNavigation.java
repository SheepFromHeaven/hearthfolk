package com.sheepfromheaven.minenow.villager;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.core.BlockPos;

/**
 * Vanilla ground navigation where each step costs extra by road tier ({@link Roads#stepCost}), so villagers keep to the
 * streets players build, and landing from a jump costs more the higher the fall ({@link #dropCost}), so villagers
 * take stairs, ramps and ladders down when there are any. Nobody but a miner steps down into the open shaft of a mine from
 * outside it, where it couldn't climb out (one already down there may move about in it to reach the ladder) ({@link com.sheepfromheaven.minenow.miner.Miner#inShaft}). The cost sits on the landing node (a node can't tell which
 * neighbour it was entered from), taken once rather than added up per neighbour. Vanilla's PathFinder weights its distance estimate by 1.5, so the open-ground cost must stay
 * well above 0.5 or that greed cancels it out and detours are never explored.
 */
public class RoadNavigation extends GroundPathNavigation {
    /** Extra costs make the search look further, so it gets more room than vanilla's follow range allows. */
    private static final int MIN_VISITED_NODES = 1024;

    public RoadNavigation(Mob mob, Level level) {
        super(mob, level);
    }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        this.nodeEvaluator = new RoadNodeEvaluator();
        this.nodeEvaluator.setCanPassDoors(true);
        return new PathFinder(this.nodeEvaluator, Math.max(maxVisitedNodes, MIN_VISITED_NODES));
    }

    /** Jumping down costs more the higher the fall, so villagers take stairs, ramps and ladders down when there are any. */
    static float dropCost(int drop) {
        return drop < 2 ? 0.0F : 8.0F * (float) Math.pow(4.0, drop - 2);
    }

    private static class RoadNodeEvaluator extends WalkNodeEvaluator {
        @Override
        public PathType getPathType(PathfindingContext context, int x, int y, int z) {
            if (OpenFenceGates.canOpen(this.mob) && OpenFenceGates.isClosedGate(context.getBlockState(new BlockPos(x, y, z)))) {
                return PathType.WALKABLE_DOOR;
            }
            return super.getPathType(context, x, y, z);
        }

        @Override
        public int getNeighbors(Node[] neighbors, Node pos) {
            int count = super.getNeighbors(neighbors, pos);
            if (!(this.mob instanceof net.minecraft.world.entity.npc.villager.Villager villager)
                    || !villager.getVillagerData().profession().is(com.sheepfromheaven.minenow.miner.Miner.PROFESSION)
                    && !com.sheepfromheaven.minenow.miner.Miner.inShaft(this.mob.level(), this.mob.getBlockX(), this.mob.getBlockY(), this.mob.getBlockZ())) {
                int kept = 0;
                for (int i = 0; i < count; i++) {
                    Node node = neighbors[i];
                    if (!com.sheepfromheaven.minenow.miner.Miner.inShaft(this.mob.level(), node.x, node.y, node.z)) neighbors[kept++] = node;
                }
                count = kept;
            }
            for (int i = 0; i < count; i++) {
                Node node = neighbors[i];
                if (node.costMalus >= 0.0F) {
                    node.costMalus = Math.max(node.costMalus, Roads.stepCost(this.currentContext.level(), node.asBlockPos()) + dropCost(pos.y - node.y));
                }
            }
            return count;
        }
    }
}
