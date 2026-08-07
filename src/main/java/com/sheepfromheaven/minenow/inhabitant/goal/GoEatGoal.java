package com.sheepfromheaven.minenow.inhabitant.goal;

import com.sheepfromheaven.minenow.dominion.DominionSavedData;
import com.sheepfromheaven.minenow.structure.DominionStructure;
import com.sheepfromheaven.minenow.dominion.NeedType;
import com.sheepfromheaven.minenow.structure.StructureType;
import com.sheepfromheaven.minenow.jobs.ContainerUtil;
import com.sheepfromheaven.minenow.jobs.cook.CookingRecipes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;
import java.util.UUID;
import com.sheepfromheaven.minenow.inhabitant.InhabitantEntity;

/**
 * Walks a hungry inhabitant to the nearest tavern with a ready-to-eat meal in storage, eats one,
 * and restores the FOOD need. Job-agnostic - any inhabitant can use this regardless of whether
 * they hold a job, mirroring {@link GoHomeGoal}.
 */
public class GoEatGoal extends Goal {
    private static final float HUNGRY_THRESHOLD = 0.4f;
    private static final int REPATH_INTERVAL = 60;
    private static final int EAT_TICKS = 40;
    private static final double ARRIVE_DISTANCE_SQ = 4.0;
    private static final double WALK_SPEED = 0.6;

    private final InhabitantEntity inhabitant;
    private UUID targetStructureId;
    private BlockPos targetPos;
    private int repathCooldown;
    private int eatTicksRemaining = -1;

    public GoEatGoal(InhabitantEntity inhabitant) {
        this.inhabitant = inhabitant;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (inhabitant.getNeed(NeedType.FOOD) >= HUNGRY_THRESHOLD) {
            return false;
        }
        return findTavernWithFood() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return targetPos != null;
    }

    @Override
    public void start() {
        inhabitant.playGoalSwitchSound();
        eatTicksRemaining = -1;
        var structure = findTavernWithFood();
        if (structure == null) {
            targetPos = null;
            return;
        }
        targetStructureId = structure.getId();
        targetPos = inhabitant.level() instanceof ServerLevel level ? structure.findApproachPos(level) : structure.getMarkerPos();
        moveToTarget();
    }

    @Override
    public void stop() {
        eatTicksRemaining = -1;
        targetPos = null;
        targetStructureId = null;
        inhabitant.getNavigation().stop();
    }

    private void moveToTarget() {
        inhabitant.getNavigation().moveTo(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5, WALK_SPEED);
        repathCooldown = REPATH_INTERVAL;
    }

    @Override
    public void tick() {
        if (targetPos == null) {
            return;
        }

        boolean arrived = inhabitant.distanceToSqr(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5) <= ARRIVE_DISTANCE_SQ;

        if (eatTicksRemaining < 0) {
            if (arrived) {
                inhabitant.getNavigation().stop();
                eatTicksRemaining = EAT_TICKS;
            } else if (--repathCooldown <= 0) {
                moveToTarget();
            }
        } else if (--eatTicksRemaining <= 0) {
            eatMeal();
            targetPos = null;
        }
    }

    private void eatMeal() {
        if (!(inhabitant.level() instanceof ServerLevel level) || inhabitant.getDominionId() == null || targetStructureId == null) {
            return;
        }
        var dominion = DominionSavedData.get(level).getDominionById(inhabitant.getDominionId());
        if (dominion == null) {
            return;
        }
        var structure = dominion.getStructureById(targetStructureId);
        if (structure == null) {
            return;
        }

        for (var pos : structure.findStorageContainers(level)) {
            if (level.getBlockEntity(pos) instanceof Container container) {
                var meal = ContainerUtil.extractOne(container, inhabitant, stack -> CookingRecipes.isReadyToEat(level, stack));
                if (!meal.isEmpty()) {
                    inhabitant.setNeed(NeedType.FOOD, 1.0f);
                    return;
                }
            }
        }
    }

    private DominionStructure findTavernWithFood() {
        if (!(inhabitant.level() instanceof ServerLevel level) || inhabitant.getDominionId() == null) {
            return null;
        }
        var dominion = DominionSavedData.get(level).getDominionById(inhabitant.getDominionId());
        if (dominion == null) {
            return null;
        }
        return dominion.findNearestStructureOfType(StructureType.TAVERN, inhabitant.blockPosition(),
                s -> hasReadyToEatFood(level, s)).orElse(null);
    }

    private boolean hasReadyToEatFood(ServerLevel level, DominionStructure structure) {
        for (var pos : structure.findStorageContainers(level)) {
            if (level.getBlockEntity(pos) instanceof Container container) {
                for (int i = 0; i < container.getContainerSize(); i++) {
                    if (CookingRecipes.isReadyToEat(level, container.getItem(i))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
