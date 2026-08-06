package com.sheepfromheaven.minenow.entity;

import com.sheepfromheaven.minenow.dominion.DominionPathNode;
import com.sheepfromheaven.minenow.dominion.DominionSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Idle-wander alternative to {@link net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal}:
 * every so often, heads for the nearest {@link DominionPathNode.Kind#STREET} node instead of picking a
 * random direction. A soft hint, not a hard constraint - with no nodes placed this goal never triggers
 * and the vanilla stroll goal (lower priority) handles wandering as usual.
 *
 * <p>The node's {@link DominionPathNode.Impact} controls how close the inhabitant actually walks:
 * HIGH = almost to the exact block; LOW = diverts toward the node but stops well short.
 */
public class StrollTowardPathNodeGoal extends Goal {
    private static final int TRY_INTERVAL = 120;
    private static final int REPATH_INTERVAL = 60;
    private static final double WALK_SPEED = 0.35;
    private static final double MAX_DISTANCE_SQ = 48.0 * 48.0;

    private final InhabitantEntity inhabitant;
    private BlockPos targetPos;
    private double stopRadiusSq;
    private int repathCooldown;

    public StrollTowardPathNodeGoal(InhabitantEntity inhabitant) {
        this.inhabitant = inhabitant;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (inhabitant.getRandom().nextInt(TRY_INTERVAL) != 0) return false;
        var dominionId = inhabitant.getDominionId();
        if (dominionId == null || !(inhabitant.level() instanceof ServerLevel serverLevel)) return false;
        var dominion = DominionSavedData.get(serverLevel).getDominionById(dominionId);
        if (dominion == null) return false;
        var nearest = dominion.findNearestPathNode(DominionPathNode.Kind.STREET, inhabitant.blockPosition());
        if (nearest.isEmpty()) return false;
        var node = nearest.get();
        var pos = node.getPos();
        double distSq = inhabitant.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        // Only activate if the inhabitant is not already within the node's stop radius and not too far.
        if (distSq <= node.getImpact().stopRadiusSq() || distSq > MAX_DISTANCE_SQ) return false;
        targetPos = pos;
        stopRadiusSq = node.getImpact().stopRadiusSq();
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (targetPos == null || inhabitant.getNavigation().isDone()) return false;
        double distSq = inhabitant.distanceToSqr(
                targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5);
        return distSq > stopRadiusSq;
    }

    @Override
    public void start() {
        moveToTarget();
    }

    private void moveToTarget() {
        inhabitant.getNavigation().moveTo(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5, WALK_SPEED);
        repathCooldown = REPATH_INTERVAL;
    }

    @Override
    public void tick() {
        if (--repathCooldown <= 0) {
            moveToTarget();
        }
    }

    @Override
    public void stop() {
        targetPos = null;
        inhabitant.getNavigation().stop();
    }
}
