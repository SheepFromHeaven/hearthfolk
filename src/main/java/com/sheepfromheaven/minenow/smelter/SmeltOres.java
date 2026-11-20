package com.sheepfromheaven.minenow.smelter;

import com.google.common.collect.ImmutableMap;
import com.sheepfromheaven.minenow.villager.FurnaceTending;
import com.sheepfromheaven.minenow.villager.HomeStorage;
import com.sheepfromheaven.minenow.villager.Unloading;
import com.sheepfromheaven.minenow.MineNowConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.BlastFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The smelter keeps its blast furnace, and the furnaces and blast furnaces within {@link #RANGE} blocks of it that its
 * level allows ({@link SmelterSkill#extraFurnaces}), going: raw ore spread in equal shares over the blast furnaces (and
 * the plain furnaces when it has nothing else for them), logs into the plain furnaces for charcoal or else cobblestone
 * into stone, with coal or charcoal it holds,
 * through {@link FurnaceTending}. When its goods pile up it carries them to its home chest. In CORE like TendFields.
 */
public class SmeltOres extends Behavior<Villager> {
    private static final int RANGE = 4;
    private static final float SPEED = 0.5F;
    private static final int GIVE_UP_AFTER_TICKS = 300;
    private static final int IDLE_RESCAN_DELAY = 60;

    private @Nullable BlockPos target;
    private RecipeType<? extends AbstractCookingRecipe> type = RecipeType.BLASTING;
    private Predicate<ItemStack> cooks = Smelter::smelts;
    private int share = Integer.MAX_VALUE;
    private boolean unloading;
    private long since;
    private long nextStartAllowed;

    public SmeltOres() {
        super(ImmutableMap.of(
                MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT
        ), GIVE_UP_AFTER_TICKS + 20);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager body) {
        if (!MineNowConfig.professionEnabled(MineNowConfig.Profession.SMELTER) || level.getGameTime() < nextStartAllowed || body.isTrading()) return false;
        if (!body.getVillagerData().profession().is(Smelter.PROFESSION) || !body.getBrain().isActive(Activity.WORK)) return false;
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
            if (!com.sheepfromheaven.minenow.villager.Walking.towards(body, target)) walk(body);
            return;
        }
        if (unloading) {
            Unloading.deposit(level, body, target);
        } else {
            FurnaceTending.tend(level, body, target, type, cooks, share);
            body.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
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
        nextStartAllowed = timestamp + 20;
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
        List<BlockPos> furnaces = furnaces(level, body, site.get());
        List<BlockPos> blast = furnaces.stream().filter(pos -> level.getBlockState(pos).getBlock() instanceof BlastFurnaceBlock).toList();
        List<BlockPos> plain = furnaces.stream().filter(pos -> !blast.contains(pos)).toList();
        Predicate<ItemStack> plainWork = plainWork(level, body, plain);
        boolean hasLogs = plainWork != null;
        List<BlockPos> oreFurnaces = new ArrayList<>(blast);
        if (!hasLogs) oreFurnaces.addAll(plain);
        int raw = body.getInventory().getItems().stream().filter(Smelter::smelts).mapToInt(ItemStack::getCount).sum();
        for (BlockPos pos : oreFurnaces) {
            if (level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace && Smelter.smelts(furnace.getItem(0))) raw += furnace.getItem(0).getCount();
        }
        int oreShare = Math.max(1, (raw + oreFurnaces.size() - 1) / Math.max(1, oreFurnaces.size()));
        for (BlockPos pos : oreFurnaces) {
            RecipeType<? extends AbstractCookingRecipe> recipe = blast.contains(pos) ? RecipeType.BLASTING : RecipeType.SMELTING;
            if (FurnaceTending.needsTending(level, body, pos, recipe, Smelter::smelts, oreShare)) return aim(pos, recipe, Smelter::smelts, oreShare);
        }
        if (hasLogs) {
            for (BlockPos pos : plain) {
                if (FurnaceTending.needsTending(level, body, pos, RecipeType.SMELTING, plainWork)) return aim(pos, RecipeType.SMELTING, plainWork, Integer.MAX_VALUE);
            }
        }
        return false;
    }

    /** What the plain furnaces are for: logs into charcoal first, then cobblestone into stone (for the mason); null leaves them to ore. */
    private static @Nullable Predicate<ItemStack> plainWork(ServerLevel level, Villager body, List<BlockPos> plain) {
        for (Predicate<ItemStack> work : List.<Predicate<ItemStack>>of(Smelter::burns, Smelter::bakes)) {
            if (body.getInventory().hasAnyMatching(work) || plain.stream().anyMatch(pos -> holds(level, pos, work))) return work;
        }
        return null;
    }

    private boolean aim(BlockPos pos, RecipeType<? extends AbstractCookingRecipe> recipe, Predicate<ItemStack> what, int amount) {
        target = pos;
        type = recipe;
        cooks = what;
        share = amount;
        return true;
    }

    private static boolean holds(ServerLevel level, BlockPos pos, Predicate<ItemStack> what) {
        return level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace && what.test(furnace.getItem(0));
    }

    /** Its blast furnace, then the nearest furnaces and blast furnaces its level lets it use. */
    private static List<BlockPos> furnaces(ServerLevel level, Villager body, BlockPos site) {
        List<BlockPos> furnaces = new ArrayList<>();
        furnaces.add(site);
        BlockPos.betweenClosedStream(site.offset(-RANGE, -1, -RANGE), site.offset(RANGE, 1, RANGE))
                .filter(pos -> !pos.equals(site) && (level.getBlockState(pos).is(Blocks.FURNACE) || level.getBlockState(pos).is(Blocks.BLAST_FURNACE)))
                .map(BlockPos::immutable)
                .sorted(Comparator.comparingDouble(pos -> pos.distSqr(site)))
                .limit(SmelterSkill.extraFurnaces(body.getVillagerData().level()))
                .forEach(furnaces::add);
        return furnaces;
    }

    private void walk(Villager body) {
        if (target == null) return;
        body.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(target, SPEED, 1));
        body.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(target));
    }
}
