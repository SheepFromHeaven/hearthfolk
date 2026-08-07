package com.sheepfromheaven.minenow.inhabitant;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathFinder;

/** Ground navigation that swaps in {@link InhabitantNodeEvaluator} so closed fence gates are
 * treated like closed doors instead of an unopenable fence, and ladders/vines are climbable. */
public class InhabitantPathNavigation extends GroundPathNavigation {
    public InhabitantPathNavigation(Mob mob, Level level) {
        super(mob, level);
    }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        nodeEvaluator = new InhabitantNodeEvaluator();
        return new PathFinder(nodeEvaluator, maxVisitedNodes);
    }
}
