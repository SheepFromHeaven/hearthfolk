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
 * walks to the column (from the surface, to the exit beside its top) and steps onto it itself, since a ladder cell
 * over a hole is no place a path can end; then climbs by holding jump against the wall or slides down, and steps off at
 * the level it wants. On the column it holds itself there and moves at ladder speed, since vanilla only lifts a mob
 * that keeps pushing into the wall while jumping, and a villager drifts off the ladder doing that.
 */
final class LadderClimb {
    private static final double CENTERED = 0.45;
    private static final double CLIMB_SPEED = 0.2;
    private static final double STEP_SPEED = 0.15;
    /** A quarry's cells lie within {@link Quarry#RANGE} of its cart, its tunnels further out. */
    private static final int CART_SEARCH = 32;

    private LadderClimb() {}

    /** Whether the villager is down in a mine (a miner: its own), deeper than it could step up out of. */
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
            BlockPos approach = onSurface ? exit(ladder) : besideLadder(body, column);
            double dx = Math.abs(body.getX() - x);
            double dz = Math.abs(body.getZ() - z);
            // Only straight beside it: pushed diagonally at the column, a villager wedges into the corner of undug rock.
            boolean besideColumn = !onSurface && ((dx <= 1.6 && dz <= CENTERED) || (dz <= 1.6 && dx <= CENTERED));
            if (besideColumn || (onSurface && Math.abs(body.getX() - (approach.getX() + 0.5)) <= 0.6 && Math.abs(body.getZ() - (approach.getZ() + 0.5)) <= 0.6)) {
                body.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                body.getNavigation().stop();
                body.getMoveControl().setWantedPosition(x, body.getY(), z, 0.5);
            } else if (!body.getBrain().getMemory(MemoryModuleType.WALK_TARGET).map(walk -> walk.getTarget().currentBlockPosition().equals(approach)).orElse(false)) {
                body.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(approach, 0.5F, 0));
            }
            return false;
        }
        body.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        body.getNavigation().stop();
        if (body.getY() < standY - 0.05) {
            hold(body, x, z, CLIMB_SPEED);
        } else if (body.getY() > standY + 0.05) {
            hold(body, x, z, -Math.min(CLIMB_SPEED, body.getY() - standY));
        } else {
            BlockPos off = stepOff(body, column.atY(standY), stepTo);
            body.getMoveControl().setWantedPosition(off.getX() + 0.5, standY, off.getZ() + 0.5, 0.6);
            double dx = off.getX() + 0.5 - body.getX();
            double dz = off.getZ() + 0.5 - body.getZ();
            double length = Math.max(1.0E-4, Math.sqrt(dx * dx + dz * dz));
            double lift = body.getY() < standY - 0.01 ? 0.1 : 0.0;
            body.setDeltaMovement(dx / length * STEP_SPEED, Math.max(body.getDeltaMovement().y, lift), dz / length * STEP_SPEED);
            body.resetFallDistance();
        }
        return false;
    }

    /**
     * Where to step onto the ladder from inside the mine: the nearest floored, two-high cell straight beside the column,
     * at the villager's level or one up, since a path to the ladder cell itself can end diagonally against undug rock.
     */
    private static BlockPos besideLadder(Villager body, BlockPos column) {
        BlockPos best = column.atY(body.getBlockY());
        double bestDistance = Double.MAX_VALUE;
        for (int dy = 0; dy <= 1; dy++) {
            for (net.minecraft.core.Direction side : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                BlockPos cell = column.atY(body.getBlockY() + dy).relative(side);
                if (!standable(body.level(), cell)) continue;
                double distance = cell.distToCenterSqr(body.position());
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = cell;
                }
            }
        }
        return best;
    }

    private static boolean standable(net.minecraft.world.level.Level level, BlockPos cell) {
        return level.getBlockState(cell).getCollisionShape(level, cell).isEmpty()
                && level.getBlockState(cell.above()).getCollisionShape(level, cell.above()).isEmpty()
                && level.getBlockState(cell.below()).isFaceSturdy(level, cell.below(), net.minecraft.core.Direction.UP);
    }

    /** The floored, two-high cell beside the ladder at this level that is closest to where the villager is headed; it walks on from there. */
    private static BlockPos stepOff(Villager body, BlockPos ladderCell, BlockPos headedFor) {
        BlockPos best = headedFor;
        double bestDistance = Double.MAX_VALUE;
        for (net.minecraft.core.Direction side : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            BlockPos next = ladderCell.relative(side);
            var level = body.level();
            boolean standable = level.getBlockState(next).getCollisionShape(level, next).isEmpty()
                    && level.getBlockState(next.above()).getCollisionShape(level, next.above()).isEmpty()
                    && level.getBlockState(next.below()).isFaceSturdy(level, next.below(), net.minecraft.core.Direction.UP);
            if (!standable) continue;
            double distance = next.distSqr(headedFor);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = next;
            }
        }
        return best;
    }

    /** Keeps the villager centred on the ladder and moves it up or down at ladder speed, instead of trusting vanilla's push-and-jump climbing. */
    private static void hold(Villager body, double x, double z, double vertical) {
        body.getMoveControl().setWantedPosition(x, body.getY(), z, 0.0);
        body.setDeltaMovement((x - body.getX()) * 0.5, vertical, (z - body.getZ()) * 0.5);
        body.resetFallDistance();
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

    /** A miner's own cart; for anyone else the cart of the mine it stands in, since a buyer or a stroller can end up down someone's mine. */
    private static Optional<BlockPos> furnace(ServerLevel level, Villager body) {
        if (body.getVillagerData().profession().is(Miner.PROFESSION)) {
            return body.getBrain().getMemory(MemoryModuleType.JOB_SITE)
                    .filter(site -> site.dimension() == level.dimension())
                    .map(GlobalPos::pos);
        }
        return level.getPoiManager().findAll(poi -> poi.is(Miner.POI), pos -> pos.getY() > body.getBlockY(), body.blockPosition(), CART_SEARCH,
                        net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY)
                .filter(cart -> Quarry.around(level, cart).contains(body.blockPosition()))
                .findFirst();
    }
}
