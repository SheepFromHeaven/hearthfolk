package com.sheepfromheaven.minenow.cleric;

import com.google.common.collect.ImmutableMap;
import com.sheepfromheaven.minenow.villager.Economy;
import com.sheepfromheaven.minenow.villager.Unloading;
import com.sheepfromheaven.minenow.villager.Walking;
import com.sheepfromheaven.minenow.villager.WorkXp;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * The cleric at its real brewing stand: it collects what a finished brew left, then, with the stand idle, loads the first
 * brew of {@link Brews} that its level unlocks and that it holds fewer than {@code stock} of: as many bottles as
 * {@link ClericSkill#bottles} says (water bottles it fills at water within {@link #WATER_RANGE} blocks of the stand from
 * glass bottles, or the potions the step starts from), a reagent, and a blaze powder as fuel when the stand has none.
 * Inputs and goods come from its inventory and chests, through {@link Economy}. In CORE like SmeltOres.
 */
public class BrewPotions extends Behavior<Villager> {
    private static final int WATER_RANGE = 8;
    private static final float SPEED = 0.5F;
    private static final int GIVE_UP_AFTER_TICKS = 300;
    private static final int IDLE_RESCAN_DELAY = 60;
    private static final int SETTLE = 40;

    private @Nullable BlockPos target;
    private boolean unloading;
    private long since;
    private long nextStartAllowed;

    public BrewPotions() {
        super(ImmutableMap.of(
                MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT
        ), GIVE_UP_AFTER_TICKS + 20);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager body) {
        if (level.getGameTime() < nextStartAllowed || body.isTrading()) return false;
        if (!body.getVillagerData().profession().is(VillagerProfession.CLERIC) || !body.getBrain().isActive(Activity.WORK)) return false;
        if (pickTarget(level, body)) return true;
        nextStartAllowed = level.getGameTime() + IDLE_RESCAN_DELAY;
        return false;
    }

    @Override
    protected void start(ServerLevel level, Villager body, long timestamp) {
        since = timestamp;
        walk(body);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager body, long timestamp) {
        return target != null && !body.isTrading() && body.getBrain().isActive(Activity.WORK) && timestamp - since < GIVE_UP_AFTER_TICKS;
    }

    @Override
    protected void tick(ServerLevel level, Villager body, long timestamp) {
        if (target == null) return;
        if (body.distanceToSqr(Vec3.atBottomCenterOf(target)) > 4.0 || Math.abs(target.getY() - body.getY()) > 1.5) {
            if (!Walking.towards(body, target)) walk(body);
            return;
        }
        if (unloading) {
            Unloading.deposit(level, body, target);
        } else {
            tend(level, body, target);
            body.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
            nextStartAllowed = timestamp + SETTLE;
            target = null;
            return;
        }
        if (pickTarget(level, body)) {
            since = timestamp;
            walk(body);
        } else {
            target = null;
        }
    }

    @Override
    protected void stop(ServerLevel level, Villager body, long timestamp) {
        target = null;
        body.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        body.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        nextStartAllowed = Math.max(nextStartAllowed, timestamp + 20);
    }

    private boolean pickTarget(ServerLevel level, Villager body) {
        target = null;
        unloading = false;
        Optional<BlockPos> site = body.getBrain().getMemory(MemoryModuleType.JOB_SITE).filter(pos -> pos.dimension() == level.dimension()).map(GlobalPos::pos);
        if (site.isEmpty()) return false;
        if (Unloading.shouldUnload(body)) {
            Optional<BlockPos> home = Unloading.unloadTarget(level, body);
            if (home.isPresent()) {
                target = home.get();
                unloading = true;
                return true;
            }
        }
        if (!(level.getBlockEntity(site.get()) instanceof BrewingStandBlockEntity stand)) return false;
        if (finished(stand) || (idle(stand) && next(level, body, stand).isPresent())) {
            target = site.get();
            return true;
        }
        return false;
    }

    private static boolean finished(BrewingStandBlockEntity stand) {
        return stand.brewTime == 0 && (!stand.getItem(0).isEmpty() || !stand.getItem(1).isEmpty() || !stand.getItem(2).isEmpty());
    }

    private static boolean idle(BrewingStandBlockEntity stand) {
        return stand.brewTime == 0 && stand.getItem(0).isEmpty() && stand.getItem(1).isEmpty() && stand.getItem(2).isEmpty() && stand.getItem(3).isEmpty();
    }

    private static boolean fueled(BrewingStandBlockEntity stand) {
        return stand.fuel > 0 || !stand.getItem(4).isEmpty();
    }

    /** The first unlocked brew it is short of and can load now: it has the input, the reagent and a way to fuel the stand. */
    private static Optional<Brews.Brew> next(ServerLevel level, Villager body, BrewingStandBlockEntity stand) {
        boolean water = waterNear(level, stand.getBlockPos()) != null;
        for (Brews.Brew brew : Brews.upTo(body.getVillagerData().level())) {
            if (PotionGoods.count(level, body, brew.output().form(), brew.output().potion()) >= brew.stock()) continue;
            int fuelNeeded = fueled(stand) ? 0 : 1;
            if (Economy.count(level, body, brew.reagent()) < 1 + (brew.reagent() == Items.BLAZE_POWDER ? fuelNeeded : 0)) continue;
            if (Economy.count(level, body, Items.BLAZE_POWDER) < fuelNeeded) continue;
            boolean fromWater = isWater(brew.input());
            if (fromWater ? !(water && Economy.count(level, body, Items.GLASS_BOTTLE) > 0)
                    : PotionGoods.count(level, body, brew.input().form(), brew.input().potion()) < 1) continue;
            return Optional.of(brew);
        }
        return Optional.empty();
    }

    private static boolean isWater(Brews.PotionRef ref) {
        return ref.potion().is(Potions.WATER);
    }

    private static void tend(ServerLevel level, Villager body, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof BrewingStandBlockEntity stand)) return;
        if (finished(stand)) {
            for (int slot = 0; slot < 3; slot++) {
                if (stand.getItem(slot).isEmpty()) continue;
                Economy.give(level, body, stand.getItem(slot).copy());
                stand.setItem(slot, ItemStack.EMPTY);
            }
            WorkXp.award(body, WorkXp.CRAFTED);
        }
        if (!idle(stand)) {
            stand.setChanged();
            return;
        }
        Optional<Brews.Brew> chosen = next(level, body, stand);
        if (chosen.isEmpty()) return;
        Brews.Brew brew = chosen.get();
        if (!fueled(stand)) stand.setItem(4, new ItemStack(Items.BLAZE_POWDER, Economy.take(level, body, Items.BLAZE_POWDER, 1)));
        for (int slot = 0; slot < ClericSkill.bottles(body.getVillagerData().level()); slot++) {
            ItemStack bottle = isWater(brew.input()) ? fill(level, body, pos) : PotionGoods.takeOne(level, body, brew.input().form(), brew.input().potion());
            if (bottle.isEmpty()) break;
            stand.setItem(slot, bottle);
        }
        stand.setItem(3, new ItemStack(brew.reagent(), Economy.take(level, body, brew.reagent(), 1)));
        stand.setChanged();
    }

    /** A water bottle for a glass bottle, from the water beside the stand (a cauldron loses a layer); empty when it has no bottle or no water. */
    private static ItemStack fill(ServerLevel level, Villager body, BlockPos stand) {
        BlockPos water = waterNear(level, stand);
        if (water == null || Economy.take(level, body, Items.GLASS_BOTTLE, 1) < 1) return ItemStack.EMPTY;
        if (level.getBlockState(water).is(Blocks.WATER_CAULDRON)) LayeredCauldronBlock.lowerFillLevel(level.getBlockState(water), level, water);
        return PotionContents.createItemStack(Items.POTION, Potions.WATER);
    }

    /** A water source or a cauldron with water in it within {@link #WATER_RANGE} blocks of the stand, 3 up or down. */
    static @Nullable BlockPos waterNear(ServerLevel level, BlockPos stand) {
        for (BlockPos pos : BlockPos.betweenClosed(stand.offset(-WATER_RANGE, -3, -WATER_RANGE), stand.offset(WATER_RANGE, 3, WATER_RANGE))) {
            var state = level.getBlockState(pos);
            if (state.is(Blocks.WATER_CAULDRON) || (level.getFluidState(pos).is(FluidTags.WATER) && level.getFluidState(pos).isSource())) return pos.immutable();
        }
        return null;
    }

    private void walk(Villager body) {
        if (target == null) return;
        body.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(target, SPEED, 1));
        body.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(target));
    }
}
