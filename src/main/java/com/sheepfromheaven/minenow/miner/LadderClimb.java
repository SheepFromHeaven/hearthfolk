package com.sheepfromheaven.minenow.miner;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;

import java.util.Optional;

/**
 * Vanilla pathfinding never plans a route up or down a ladder, so a miner uses its ladder the way a player would: it
 * walks to the column (from the surface, to the exit beside its top), climbs by holding jump against the wall or slides
 * down, and steps off at the level it wants.
 */
final class LadderClimb {
    private static final double CENTERED = 0.45;

    private LadderClimb() {}

    /** Whether the villager is down in its mine, deeper than it could step up out of. */
    static boolean inQuarry(ServerLevel level, Villager body) {
        return furnace(level, body).map(furnace -> Quarry.around(level, furnace).contains(body.blockPosition())).orElse(false);
    }

    /** One tick of climbing out; true once the villager stands on the surface beside the ladder. */
    static boolean climbOut(ServerLevel level, Villager body) {
        Optional<BlockPos> furnace = furnace(level, body);
        if (furnace.isEmpty()) return true;
        Quarry quarry = Quarry.around(level, furnace.get());
        if (!quarry.contains(body.blockPosition())) return true;
        return quarry.ladder(level, furnace.get()).map(ladder -> climbTo(body, quarry, ladder, quarry.top() + 1, exit(ladder))).orElse(true);
    }

    /**
     * One tick of using the ladder to reach {@code standY} (the feet level to work from); true once the villager is
     * there and off the ladder, stepping towards {@code stepTo}.
     */
    static boolean climbTo(Villager body, Quarry quarry, Quarry.Ladder ladder, int standY, BlockPos stepTo) {
        BlockPos column = ladder.top();
        double x = column.getX() + 0.5;
        double z = column.getZ() + 0.5;
        boolean onColumn = Math.abs(body.getX() - x) <= CENTERED && Math.abs(body.getZ() - z) <= CENTERED;
        if (!onColumn) {
            if (body.getBlockY() == standY && body.onGround()) return true;
            boolean onSurface = !quarry.contains(body.blockPosition());
            BlockPos approach = onSurface ? exit(ladder) : column.atY(body.getBlockY());
            if (onSurface && Math.abs(body.getX() - (approach.getX() + 0.5)) <= 0.6 && Math.abs(body.getZ() - (approach.getZ() + 0.5)) <= 0.6) {
                body.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                body.getNavigation().stop();
                body.getMoveControl().setWantedPosition(x, body.getY(), z, 0.5);
            } else if (!body.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
                body.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(approach, 0.5F, 0));
            }
            return false;
        }
        body.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        body.getNavigation().stop();
        if (body.getY() < standY - 0.05) {
            BlockPos wall = column.relative(ladder.facing().getOpposite()).atY(body.getBlockY());
            body.getMoveControl().setWantedPosition(wall.getX() + 0.5, standY, wall.getZ() + 0.5, 0.6);
            body.getJumpControl().jump();
        } else if (body.getBlockY() > standY) {
            body.getMoveControl().setWantedPosition(x, body.getY(), z, 0.3);
        } else {
            body.getMoveControl().setWantedPosition(stepTo.getX() + 0.5, standY, stepTo.getZ() + 0.5, 0.6);
        }
        return false;
    }

    /** The surface spot beside the ladder's top, on the wall it hangs on. */
    static BlockPos exit(Quarry.Ladder ladder) {
        return ladder.top().relative(ladder.facing().getOpposite()).above();
    }

    /** A route planned while hanging on the ladder is never re-planned once standing, so drop it after climbing. */
    static void forgetRoute(Villager body) {
        body.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        body.getBrain().eraseMemory(MemoryModuleType.PATH);
        body.getNavigation().stop();
    }

    private static Optional<BlockPos> furnace(ServerLevel level, Villager body) {
        return body.getBrain().getMemory(MemoryModuleType.JOB_SITE)
                .filter(site -> site.dimension() == level.dimension())
                .map(GlobalPos::pos);
    }
}
