package com.sheepfromheaven.minenow.farmer;

import com.google.common.collect.ImmutableMap;
import com.sheepfromheaven.minenow.villager.Commissions;
import com.sheepfromheaven.minenow.villager.Economy;
import com.sheepfromheaven.minenow.villager.FieldWork;
import com.sheepfromheaven.minenow.villager.Price;
import com.sheepfromheaven.minenow.villager.TradeTable;
import com.sheepfromheaven.minenow.villager.Unloading;
import com.sheepfromheaven.minenow.villager.WorkXp;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * In its free time a farmer composts the seeds it can neither plant nor sell into bone meal for its field, one at a time
 * into its composter, and takes the bone meal out once it's ready. Field work comes first ({@link FieldWork}); crops are
 * food and never go in; it stops once it holds as much bone meal as it would buy.
 */
public final class Compost extends Behavior<Villager> {
    private static final double NEAR = 24.0;
    private static final double REACH = 2.0;
    private static final float SPEED = 0.5F;
    private static final int EVERY = 10;
    private static final int GIVE_UP_AFTER_TICKS = 300;
    private static final int RESCAN_DELAY = 100;
    private static final int DEFAULT_BONE_MEAL = 32;

    private long startedAt;
    private long nextStartAllowed;
    private boolean reached;

    public Compost() {
        super(ImmutableMap.of(
                MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED
        ), 2400);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager farmer) {
        if (level.getGameTime() < nextStartAllowed || !free(farmer)) return false;
        if (farmer.getBrain().getRunningBehaviors().stream().anyMatch(behavior -> behavior instanceof FieldWork)) return false;
        nextStartAllowed = level.getGameTime() + RESCAN_DELAY;
        Optional<BlockPos> composter = composter(level, farmer);
        if (composter.isEmpty() || composter.get().distToCenterSqr(farmer.position()) > NEAR * NEAR) return false;
        return ready(level, composter.get()) || (wantsBoneMeal(level, farmer) && nextSeed(level, farmer).isPresent());
    }

    @Override
    protected void start(ServerLevel level, Villager farmer, long timestamp) {
        startedAt = timestamp;
        reached = false;
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager farmer, long timestamp) {
        if (!free(farmer) || farmer.getBrain().getRunningBehaviors().stream().anyMatch(behavior -> behavior instanceof FieldWork)) return false;
        Optional<BlockPos> composter = composter(level, farmer);
        if (composter.isEmpty() || (!reached && timestamp - startedAt > GIVE_UP_AFTER_TICKS)) return false;
        return ready(level, composter.get()) || filling(level, composter.get()) || (wantsBoneMeal(level, farmer) && nextSeed(level, farmer).isPresent());
    }

    @Override
    protected void tick(ServerLevel level, Villager farmer, long timestamp) {
        BlockPos composter = composter(level, farmer).orElse(null);
        if (composter == null) return;
        if (farmer.distanceToSqr(Vec3.atBottomCenterOf(composter)) > REACH * REACH) {
            farmer.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(composter, SPEED, 1));
            return;
        }
        reached = true;
        farmer.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        farmer.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(composter));
        if (timestamp % EVERY != 0) return;
        BlockState state = level.getBlockState(composter);
        if (state.getValue(ComposterBlock.LEVEL) == ComposterBlock.READY) {
            level.setBlockAndUpdate(composter, state.setValue(ComposterBlock.LEVEL, 0));
            level.playSound(null, composter, SoundEvents.COMPOSTER_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
            Economy.give(level, farmer, new ItemStack(Items.BONE_MEAL));
            WorkXp.award(farmer, WorkXp.COMPOSTED);
            return;
        }
        if (state.getValue(ComposterBlock.LEVEL) >= ComposterBlock.MAX_LEVEL || !wantsBoneMeal(level, farmer)) return;
        Optional<Item> seed = nextSeed(level, farmer);
        if (seed.isEmpty() || Economy.take(level, farmer, seed.get(), 1) == 0) return;
        BlockState after = ComposterBlock.insertItem(farmer, state, level, new ItemStack(seed.get()), composter);
        level.levelEvent(LevelEvent.COMPOSTER_FILL, composter, state != after ? 1 : 0);
        farmer.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
    }

    @Override
    protected void stop(ServerLevel level, Villager farmer, long timestamp) {
        farmer.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        farmer.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        nextStartAllowed = Math.min(nextStartAllowed, timestamp + 20);
    }

    /**
     * Seeds beyond what it keeps to plant and one batch to sell, that a composter takes. Crops that are food (potatoes,
     * carrots) and stem seeds, which only players supply, never go in.
     */
    static int surplus(ServerLevel level, Villager farmer, Item item) {
        ItemStack stack = new ItemStack(item);
        if (!Crops.isSeed(stack) || Crops.isStemSeed(stack) || stack.has(DataComponents.FOOD) || !stack.has(DataComponents.COMPOSTABLE)) return 0;
        int batch = TradeTable.forVillager(farmer).flatMap(table -> table.sellPrice(item, farmer.getVillagerData().level())).map(Price::count).orElse(0);
        return Math.max(0, Economy.count(level, farmer, item) - Unloading.keepLimit(farmer, stack) - batch - Commissions.reserved(farmer, item));
    }

    private static Optional<Item> nextSeed(ServerLevel level, Villager farmer) {
        return Economy.items(level, farmer).stream().filter(item -> surplus(level, farmer, item) > 0).findFirst();
    }

    private static boolean wantsBoneMeal(ServerLevel level, Villager farmer) {
        int enough = TradeTable.forVillager(farmer).flatMap(table -> table.supply(Items.BONE_MEAL)).map(TradeTable.Supply::max).orElse(DEFAULT_BONE_MEAL);
        return Economy.count(level, farmer, Items.BONE_MEAL) < enough;
    }

    private static boolean free(Villager farmer) {
        return FarmerRules.hearthfolkFarmer(farmer) && !farmer.isBaby() && !farmer.isTrading() && !farmer.isSleeping() && farmer.getBrain().isActive(Activity.WORK);
    }

    private static boolean ready(ServerLevel level, BlockPos composter) {
        return level.getBlockState(composter).getValue(ComposterBlock.LEVEL) == ComposterBlock.READY;
    }

    /** Full and about to turn to bone meal (vanilla's 20 tick wait), worth waiting for. */
    private static boolean filling(ServerLevel level, BlockPos composter) {
        return level.getBlockState(composter).getValue(ComposterBlock.LEVEL) == ComposterBlock.MAX_LEVEL;
    }

    private static Optional<BlockPos> composter(ServerLevel level, Villager farmer) {
        return farmer.getBrain().getMemory(MemoryModuleType.JOB_SITE)
                .filter(site -> site.dimension() == level.dimension())
                .map(GlobalPos::pos)
                .filter(pos -> level.getBlockState(pos).is(Blocks.COMPOSTER));
    }
}
