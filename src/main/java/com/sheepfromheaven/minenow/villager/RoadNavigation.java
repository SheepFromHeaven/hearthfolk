package com.sheepfromheaven.minenow.villager;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.core.BlockPos;

/**
 * Vanilla ground navigation where each step costs extra by road tier ({@link Roads#stepCost}), so villagers keep to the
 * streets players build, and landing from a jump costs more the higher the fall ({@link #dropCost}), so villagers
 * take stairs, ramps and ladders down when there are any. Nobody but its own miner steps down into the open shaft of a mine from
 * outside it, where it couldn't climb out (one already down there may move about in it to reach the ladder) ({@link com.sheepfromheaven.minenow.miner.Miner#shaftClosedTo}). The cost sits on the landing node (a node can't tell which
 * neighbour it was entered from), taken once rather than added up per neighbour. Vanilla's PathFinder weights its distance estimate by 1.5, so the open-ground cost must stay
 * well above 0.5 or that greed cancels it out and detours are never explored.
 */
public class RoadNavigation extends GroundPathNavigation {
    /** Extra costs make the search look further, so it gets more room than vanilla's follow range allows. */
    private static final int MIN_VISITED_NODES = 4096;

    public RoadNavigation(Mob mob, Level level) {
        super(mob, level);
    }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        this.nodeEvaluator = new RoadNodeEvaluator();
        this.nodeEvaluator.setCanPassDoors(true);
        return new PathFinder(this.nodeEvaluator, Math.max(maxVisitedNodes, MIN_VISITED_NODES));
    }

    /** How many nodes the last path search expanded. */
    public int lastSearchSize() {
        return ((RoadNodeEvaluator) this.nodeEvaluator).visited;
    }

    /** Jumping down costs more the higher the fall, so villagers take stairs, ramps and ladders down when there are any. */
    static float dropCost(int drop) {
        return drop < 2 ? 0.0F : 8.0F * (float) Math.pow(4.0, drop - 2);
    }

    /**
     * Vanilla plans straight through these and the villager then stands in front of them for good: an open trapdoor is a solid
     * panel across its cell, and one in the top half of its cell leaves 1.81 blocks of headroom for a villager of 1.95. A closed
     * trapdoor in the bottom half is a floor a villager walks over, so that one stays. An open one lies flat against one edge of
     * its cell, so it only stops a move across that edge (along its facing axis): a ledge running along a wall of open
     * trapdoors stays walkable.
     */
    static boolean blockedByTrapdoor(BlockState state, int dx, int dz) {
        if (!(state.getBlock() instanceof TrapDoorBlock)) return false;
        if (!state.getValue(TrapDoorBlock.OPEN)) return state.getValue(TrapDoorBlock.HALF) == Half.TOP;
        return state.getValue(TrapDoorBlock.FACING).getAxis() == net.minecraft.core.Direction.Axis.X ? dx != 0 : dz != 0;
    }

    /** Nobody stands on an open trapdoor: it has no top, so vanilla's hop onto it ends in a fall into the cell. */
    static boolean isOpenTrapdoor(BlockState state) {
        return state.getBlock() instanceof TrapDoorBlock && state.getValue(TrapDoorBlock.OPEN);
    }

    private static class RoadNodeEvaluator extends WalkNodeEvaluator {
        int visited;

        @Override
        public void prepare(net.minecraft.world.level.PathNavigationRegion region, Mob mob) {
            super.prepare(region, mob);
            visited = 0;
        }

        @Override
        public PathType getPathType(PathfindingContext context, int x, int y, int z) {
            if (OpenFenceGates.canOpen(this.mob) && OpenFenceGates.isClosedGate(context.getBlockState(new BlockPos(x, y, z)))) {
                return PathType.WALKABLE_DOOR;
            }
            return super.getPathType(context, x, y, z);
        }

        @Override
        public int getNeighbors(Node[] neighbors, Node pos) {
            visited++;
            int count = super.getNeighbors(neighbors, pos);
            int kept = 0;
            for (int i = 0; i < count; i++) {
                Node node = neighbors[i];
                if (!com.sheepfromheaven.minenow.miner.Miner.shaftClosedTo(this.mob, node.x, node.y, node.z)
                        && !blockedByTrapdoor(this.currentContext.level().getBlockState(node.asBlockPos()), node.x - pos.x, node.z - pos.z)
                        && !isOpenTrapdoor(this.currentContext.level().getBlockState(node.asBlockPos().below()))) neighbors[kept++] = node;
            }
            count = kept;
            boolean preferRoads = com.sheepfromheaven.minenow.MineNowConfig.enabled(com.sheepfromheaven.minenow.MineNowConfig.Feature.VILLAGERS_PREFER_ROADS);
            for (int i = 0; i < count; i++) {
                Node node = neighbors[i];
                if (node.costMalus >= 0.0F) {
                    float step = preferRoads ? Roads.stepCost(this.currentContext.level(), node.asBlockPos()) : Roads.OFF_ROAD_COST;
                    node.costMalus = Math.max(node.costMalus, step + dropCost(pos.y - node.y));
                }
            }
            return count;
        }
    }
}
