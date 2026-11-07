package com.sheepfromheaven.minenow.villager;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

/**
 * Vanilla ground navigation where each step costs extra by road tier ({@link Roads#stepCost}), so villagers keep to the
 * streets players build. Vanilla's PathFinder weights its distance estimate by 1.5, so the open-ground cost must stay
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

    private static class RoadNodeEvaluator extends WalkNodeEvaluator {
        @Override
        public int getNeighbors(Node[] neighbors, Node pos) {
            int count = super.getNeighbors(neighbors, pos);
            for (int i = 0; i < count; i++) {
                Node node = neighbors[i];
                if (node.costMalus >= 0.0F) {
                    node.costMalus = Math.max(node.costMalus, Roads.stepCost(this.currentContext.level(), node.asBlockPos()));
                }
            }
            return count;
        }
    }
}
