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
        if (path == null || path.notStarted()) {
            return false;
        }
        for (int i = 0; i < Math.min(path.getNextNodeIndex() + 2, path.getNodeCount()); i++) {
            var node = path.getNode(i);
            gatePos = new BlockPos(node.x, node.y + 1, node.z);
            if (mob.distanceToSqr(gatePos.getX(), mob.getY(), gatePos.getZ()) <= 2.25) {
                hasGate = isClosedFenceGate(gatePos);
                if (hasGate) {
                    return true;
                }
            }
        }
        gatePos = mob.blockPosition().above();
        hasGate = isClosedFenceGate(gatePos);
        return hasGate;
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
