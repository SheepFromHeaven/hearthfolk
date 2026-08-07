package com.sheepfromheaven.minenow.jobs;

import com.sheepfromheaven.minenow.dominion.DominionSavedData;
import com.sheepfromheaven.minenow.structure.DominionStructure;
import com.sheepfromheaven.minenow.inhabitant.InhabitantEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;

import javax.annotation.Nullable;
import java.util.EnumSet;

/**
 * At nightfall, walks the inhabitant back to their workplace and stows the currently equipped job
 * tool (fishing rod, axe, ...) into storage there, so it's waiting to be picked back up the next
 * morning instead of staying equipped - and out in the field - overnight.
 */
public class FinishWorkGoal extends Goal {
    private static final int ARRIVE_DISTANCE_SQ = 4;
    private static final int REPATH_INTERVAL = 60;
    private static final double WALK_SPEED = 0.6;
    private static final long NIGHT_START = 12542;
    private static final long NIGHT_END = 23460;

    private final InhabitantEntity inhabitant;
    private int repathCooldown;

    public FinishWorkGoal(InhabitantEntity inhabitant) {
        this.inhabitant = inhabitant;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private boolean isNight() {
        long dayTime = inhabitant.level().getDayTime() % 24000;
        return dayTime >= NIGHT_START && dayTime < NIGHT_END;
    }

    private boolean hasTool() {
        return !inhabitant.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty();
    }

    @Nullable
    private DominionStructure resolveWorkplace() {
        var structureId = inhabitant.getWorkStructureId();
        var dominionId = inhabitant.getDominionId();
        if (structureId == null || dominionId == null || !(inhabitant.level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        var dominion = DominionSavedData.get(serverLevel).getDominionById(dominionId);
        return dominion != null ? dominion.getStructureById(structureId) : null;
    }

    @Override
    public boolean canUse() {
        return isNight() && hasTool() && resolveWorkplace() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return hasTool() && resolveWorkplace() != null;
    }

    @Override
    public void start() {
        inhabitant.playGoalSwitchSound();
        repathCooldown = 0;
    }

    @Override
    public void stop() {
        inhabitant.getNavigation().stop();
    }

    @Override
    public void tick() {
        var workplace = resolveWorkplace();
        if (workplace == null || !(inhabitant.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        var target = workplace.findApproachPos(serverLevel);
        boolean arrived = inhabitant.distanceToSqr(target.getX() + 0.5, target.getY(), target.getZ() + 0.5) <= ARRIVE_DISTANCE_SQ;
        if (!arrived) {
            if (inhabitant.getNavigation().isDone() || --repathCooldown <= 0) {
                inhabitant.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, WALK_SPEED);
                repathCooldown = REPATH_INTERVAL;
            }
            return;
        }
        inhabitant.getNavigation().stop();
        storeTool(serverLevel, workplace);
    }

    private void storeTool(ServerLevel level, DominionStructure workplace) {
        var tool = inhabitant.getItemBySlot(EquipmentSlot.MAINHAND);
        if (tool.isEmpty()) {
            return;
        }
        for (var pos : workplace.findStorageContainers(level)) {
            if (!(level.getBlockEntity(pos) instanceof Container container)) {
                continue;
            }
            tool = ContainerUtil.insertItem(container, inhabitant, tool);
            if (tool.isEmpty()) {
                break;
            }
        }
        inhabitant.setItemSlot(EquipmentSlot.MAINHAND, tool);
    }
}
