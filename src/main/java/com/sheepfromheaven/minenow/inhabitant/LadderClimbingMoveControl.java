package com.sheepfromheaven.minenow.inhabitant;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;

/**
 * Vanilla's {@link MoveControl} only gives an entity a single upward nudge (one jump impulse) when
 * a path step's height exceeds its step height, which is enough to mount a single ladder rung but
 * not to climb a multi-block ladder shaft - the nudge expires after one tick and normal gravity
 * takes back over before the mob has left the ladder. As long as the mob is still trying to reach a
 * higher waypoint while standing on a climbable block, this keeps re-applying the climb velocity
 * every tick (matching the vertical speed the vanilla ladder-climbing code itself uses), rather than
 * only on the tick the jump was first requested.
 */
public class LadderClimbingMoveControl extends MoveControl {
    private static final double CLIMB_SPEED = 0.2;

    public LadderClimbingMoveControl(Mob mob) {
        super(mob);
    }

    @Override
    public void tick() {
        super.tick();
        if (hasWanted() && mob.onClimbable() && getWantedY() > mob.getY() + 0.1) {
            var delta = mob.getDeltaMovement();
            mob.setDeltaMovement(delta.x, CLIMB_SPEED, delta.z);
        }
    }
}
