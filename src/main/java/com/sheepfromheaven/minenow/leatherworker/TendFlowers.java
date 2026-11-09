package com.sheepfromheaven.minenow.leatherworker;

import com.sheepfromheaven.minenow.villager.WorkXp;
import com.google.common.collect.ImmutableMap;
import com.sheepfromheaven.minenow.villager.Walking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.TallFlowerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.Optional;

/**
 * The leatherworker's dye garden: tall flowers (rose bush, lilac, peony, sunflower) within {@link #RANGE} blocks of its
 * cauldron. Bone meal on one drops a copy and the plant stays, so it's renewable: it bone-meals them while it holds
 * bone meal and fewer than {@link #ENOUGH} of that flower, and takes the copy. Its crafts turn them into dye.
 */
public class TendFlowers extends Behavior<Villager> {
    private static final int RANGE = 8;
    private static final int HEIGHT = 3;
    private static final int ENOUGH = 8;
    private static final double REACH = 2.5;
    private static final float SPEED = 0.5F;
    private static final int RESCAN = 100;
    private static final int GIVE_UP_AFTER_TICKS = 300;

    private @Nullable BlockPos flower;
    private long since;
    private long nextStartAllowed;

    public TendFlowers() {
        super(ImmutableMap.of(
                MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT
        ), GIVE_UP_AFTER_TICKS + 20);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager body) {
        if (level.getGameTime() < nextStartAllowed || body.isTrading()) return false;
        if (!body.getVillagerData().profession().is(VillagerProfession.LEATHERWORKER) || !body.getBrain().isActive(Activity.WORK)) return false;
        nextStartAllowed = level.getGameTime() + RESCAN;
        if (body.getInventory().countItem(Items.BONE_MEAL) == 0) return false;
        Optional<BlockPos> cauldron = body.getBrain().getMemory(MemoryModuleType.JOB_SITE).filter(pos -> pos.dimension() == level.dimension()).map(GlobalPos::pos);
        if (cauldron.isEmpty()) return false;
        flower = BlockPos.betweenClosedStream(cauldron.get().offset(-RANGE, -HEIGHT, -RANGE), cauldron.get().offset(RANGE, HEIGHT, RANGE))
                .filter(pos -> wanted(level, body, pos))
                .map(BlockPos::immutable)
                .min(Comparator.comparingDouble(pos -> pos.distToCenterSqr(body.position())))
                .orElse(null);
        return flower != null;
    }

    @Override
    protected void start(ServerLevel level, Villager body, long timestamp) {
        since = timestamp;
        walk(body);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager body, long timestamp) {
        return flower != null && body.getBrain().isActive(Activity.WORK) && !body.isTrading() && timestamp - since < GIVE_UP_AFTER_TICKS;
    }

    @Override
    protected void tick(ServerLevel level, Villager body, long timestamp) {
        if (flower == null) return;
        if (body.position().distanceTo(Vec3.atBottomCenterOf(flower)) > REACH) {
            if (!Walking.towards(body, flower)) walk(body);
            return;
        }
        BlockState state = level.getBlockState(flower);
        ItemStack boneMeal = body.getInventory().getItems().stream().filter(stack -> stack.is(Items.BONE_MEAL)).findFirst().orElse(ItemStack.EMPTY);
        if (state.getBlock() instanceof TallFlowerBlock && !boneMeal.isEmpty() && BoneMealItem.growCrop(boneMeal, level, flower)) {
            level.levelEvent(1505, flower, 15);
            body.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
            WorkXp.award(body, WorkXp.BONE_MEALED);
            for (ItemEntity copy : level.getEntitiesOfClass(ItemEntity.class, new AABB(flower).inflate(2.0), item -> item.getItem().is(state.getBlock().asItem()))) {
                ItemStack rest = body.getInventory().addItem(copy.getItem().copy());
                if (rest.isEmpty()) copy.discard();
                else copy.setItem(rest);
            }
        }
        flower = null;
    }

    @Override
    protected void stop(ServerLevel level, Villager body, long timestamp) {
        flower = null;
        body.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        body.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
    }

    private static boolean wanted(ServerLevel level, Villager body, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof TallFlowerBlock) || state.getValue(DoublePlantBlock.HALF) != DoubleBlockHalf.LOWER) return false;
        return body.getInventory().countItem(state.getBlock().asItem()) < ENOUGH;
    }

    private void walk(Villager body) {
        if (flower == null) return;
        body.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(flower, SPEED, 1));
        body.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(flower));
    }
}
