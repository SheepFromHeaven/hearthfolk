package com.sheepfromheaven.minenow.inhabitant.goal;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.GoalUtils;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;

/**
 * Mirrors vanilla's {@code OpenDoorGoal}/{@code DoorInteractGoal}, which only ever look at
 * {@code DoorBlock}. Fence gates get their own path type ({@code PathType.FENCE}) with no
 * built-in open/close behavior for any mob, so inhabitants need this goal plus a pathfinding
 * malus override (see {@link InhabitantEntity#createNavigation}) to treat closed gates as
 * passable and actually swing them open.
 */
public class OpenFenceGateGoal extends Goal {
    private final Mob mob;
    private BlockPos gatePos = BlockPos.ZERO;
    private boolean hasGate;
    private boolean passedGate;
    private float openDirX;
    private float openDirZ;
    private int forgetTime;

    public OpenFenceGateGoal(Mob mob) {
        this.mob = mob;
        if (!GoalUtils.hasGroundPathNavigation(mob)) {
            throw new IllegalArgumentException("Unsupported mob type for OpenFenceGateGoal");
        }
    }

    @Override
    public boolean canUse() {
        if (!GoalUtils.hasGroundPathNavigation(mob) || !mob.horizontalCollision) {
            return false;
        }
        Path path = mob.getNavigation().getPath();
        if (path != null && !path.notStarted()) {
            for (int i = 0; i < Math.min(path.getNextNodeIndex() + 2, path.getNodeCount()); i++) {
                var node = path.getNode(i);
                if (findGateAboveNode(node.x, node.y, node.z)) {
                    return true;
                }
            }
        }
        // Fall back to whatever the mob is physically bumping into, independent of the path - covers
        // both the tile directly ahead of it (in whichever direction it's facing) and its own tile,
        // for cases where the path doesn't have a node lined up with the gate at all (no path could be
        // computed, or the "next" node has already advanced past the collision point).
        var aheadPos = mob.blockPosition().relative(mob.getDirection());
        if (findGateAboveNode(aheadPos.getX(), aheadPos.getY() - 1, aheadPos.getZ())) {
            return true;
        }
        return findGateAboveNode(mob.getBlockX(), mob.getBlockY() - 1, mob.getBlockZ());
    }

    /**
     * Checks for a closed gate one block above the given ground position (the usual case, matching
     * vanilla door placement) or two blocks above it - real terrain often has the fence/gate row
     * standing a block taller than the tile the mob is currently walking on (a step up right at the
     * wall), which a plain "ground + 1" check misses entirely.
     */
    private boolean findGateAboveNode(int x, int groundY, int z) {
        for (int dy = 1; dy <= 2; dy++) {
            var candidate = new BlockPos(x, groundY + dy, z);
            if (mob.distanceToSqr(candidate.getX(), mob.getY(), candidate.getZ()) <= 2.25 && isClosedFenceGate(candidate)) {
                gatePos = candidate;
                hasGate = true;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        return forgetTime > 0 && !passedGate && isClosedFenceGateOrOpenedByUs();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        forgetTime = 20;
        passedGate = false;
        openDirX = (float) (gatePos.getX() + 0.5 - mob.getX());
        openDirZ = (float) (gatePos.getZ() + 0.5 - mob.getZ());
        setOpen(true);
    }

    @Override
    public void stop() {
        setOpen(false);
    }

    @Override
    public void tick() {
        forgetTime--;
        float dirX = (float) (gatePos.getX() + 0.5 - mob.getX());
        float dirZ = (float) (gatePos.getZ() + 0.5 - mob.getZ());
        if (openDirX * dirX + openDirZ * dirZ < 0.0F) {
            passedGate = true;
        }
    }

    private boolean isClosedFenceGateOrOpenedByUs() {
        BlockState state = mob.level().getBlockState(gatePos);
        return state.getBlock() instanceof FenceGateBlock;
    }

    private boolean isClosedFenceGate(BlockPos pos) {
        BlockState state = mob.level().getBlockState(pos);
        return state.getBlock() instanceof FenceGateBlock && !state.getValue(FenceGateBlock.OPEN);
    }

    private void setOpen(boolean open) {
        BlockState state = mob.level().getBlockState(gatePos);
        if (!(state.getBlock() instanceof FenceGateBlock) || state.getValue(FenceGateBlock.OPEN) == open) {
            return;
        }
        mob.level().setBlock(gatePos, state.setValue(FenceGateBlock.OPEN, open), 10);
        mob.level().playSound(null, gatePos, open ? SoundEvents.FENCE_GATE_OPEN : SoundEvents.FENCE_GATE_CLOSE,
                mob.getSoundSource(), 1.0F, mob.level().getRandom().nextFloat() * 0.1F + 0.9F);
    }
}
