package com.sheepfromheaven.minenow.miner;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;

import java.util.Optional;

/**
 * Vanilla pathfinding never plans a route up a ladder, so a miner climbs out of its quarry the way a player would: it
 * walks to the ladder column, then holds jump while pushing towards the wall, and steps out onto the wall's top.
 */
final class LadderClimb {
    private LadderClimb() {}

    /** Whether the villager is down in its quarry, deeper than it could step up out of. */
    static boolean inQuarry(ServerLevel level, Villager body) {
        return furnace(level, body).map(furnace -> Quarry.around(level, furnace).contains(body.blockPosition())).orElse(false);
    }

    /** One tick of climbing out; true once the villager stands on the surface beside the ladder. */
    static boolean climbOut(ServerLevel level, Villager body) {
        Optional<BlockPos> furnace = furnace(level, body);
        if (furnace.isEmpty()) return true;
        Quarry quarry = Quarry.around(level, furnace.get());
        Optional<Quarry.Ladder> ladder = quarry.ladder(level, furnace.get());
        if (ladder.isEmpty() || !quarry.contains(body.blockPosition())) return true;
        BlockPos column = ladder.get().top();
        BlockPos exit = column.relative(ladder.get().facing().getOpposite()).above();
        double x = column.getX() + 0.5;
        double z = column.getZ() + 0.5;
        if (Math.abs(body.getX() - x) > 0.45 || Math.abs(body.getZ() - z) > 0.45) {
            if (!body.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
                body.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(column.atY(body.getBlockY()), 0.5F, 0));
            }
            return false;
        }
        body.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        body.getNavigation().stop();
        body.getMoveControl().setWantedPosition(exit.getX() + 0.5, exit.getY(), exit.getZ() + 0.5, 0.6);
        body.getJumpControl().jump();
        return false;
    }

    /** A route planned while hanging on the ladder is never re-planned once standing, so drop it after climbing out. */
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
