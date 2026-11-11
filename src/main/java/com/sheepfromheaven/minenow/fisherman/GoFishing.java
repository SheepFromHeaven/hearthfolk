package com.sheepfromheaven.minenow.fisherman;

import com.sheepfromheaven.minenow.villager.WorkXp;
import com.google.common.collect.ImmutableMap;
import com.sheepfromheaven.minenow.villager.HomeStorage;
import com.sheepfromheaven.minenow.villager.ToolCare;
import com.sheepfromheaven.minenow.villager.Tools;
import com.sheepfromheaven.minenow.villager.Unloading;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The fisherman walks to the shore of the water touching its barrel ({@link FishingWater}), casts a
 * {@link VillagerBobber}, waits for a fish to bite and reels in a catch from vanilla's fishing loot table, wearing the rod.
 * When the catch piles up it carries it to its home chest. Lives in CORE after MoveToTargetSink, like TendFields.
 */
public class GoFishing extends Behavior<Villager> {
    private static final float SPEED = 0.5F;
    private static final int RECAST_DELAY = 20;
    private static final int GIVE_UP_AFTER_TICKS = 200;
    private static final int UNREACHABLE_COOLDOWN = 600;
    private static final int IDLE_RESCAN_DELAY = 100;

    private enum Task { FISH, DEPOSIT }

    private final Map<BlockPos, Long> unreachable = new HashMap<>();
    private @Nullable BlockPos target;
    private @Nullable BlockPos water;
    private @Nullable Task task;
    private long targetSince;
    private long nextStartAllowed;
    private @Nullable VillagerBobber bobber;
    private long recastAt;
    private int reactAfter;

    public GoFishing() {
        super(ImmutableMap.of(
                MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, MemoryStatus.REGISTERED
        ), 2400);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager body) {
        if (level.getGameTime() < nextStartAllowed || body.isTrading()) return false;
        if (!body.getVillagerData().profession().is(VillagerProfession.FISHERMAN) || !body.getBrain().isActive(Activity.WORK)) return false;
        if (pickTarget(level, body)) return true;
        nextStartAllowed = level.getGameTime() + IDLE_RESCAN_DELAY;
        return false;
    }

    @Override
    protected void start(ServerLevel level, Villager body, long timestamp) {
        targetSince = timestamp;
        Tools.showInHand(body, true);
        walkTo(body);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager body, long timestamp) {
        return target != null && !body.isTrading() && body.getBrain().isActive(Activity.WORK) && jobSite(level, body).isPresent();
    }

    @Override
    protected void tick(ServerLevel level, Villager body, long timestamp) {
        if (target == null || task == null) return;
        body.getBrain().eraseMemory(MemoryModuleType.INTERACTION_TARGET);
        if (!Unloading.isTool(body, body.getMainHandItem())) Tools.showInHand(body, true);
        if (inReach(body)) {
            if (task == Task.DEPOSIT) {
                Unloading.deposit(level, body, target);
                advance(level, body, timestamp);
            } else {
                fish(level, body, timestamp);
            }
        } else if (task == Task.FISH && pathCannotReachTarget(body)) {
            unreachable.put(target, timestamp + UNREACHABLE_COOLDOWN);
            advance(level, body, timestamp);
        } else if (timestamp - targetSince > GIVE_UP_AFTER_TICKS) {
            unreachable.put(target, timestamp + IDLE_RESCAN_DELAY);
            advance(level, body, timestamp);
        } else if (!isWalkingToTarget(body)) {
            walkTo(body);
        }
    }

    @Override
    protected void stop(ServerLevel level, Villager body, long timestamp) {
        target = null;
        water = null;
        task = null;
        reelOut();
        body.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        body.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        Tools.showInHand(body, false);
        nextStartAllowed = timestamp + 40;
    }

    private void fish(ServerLevel level, Villager body, long timestamp) {
        if (!isWalkingToTarget(body)) walkTo(body);
        body.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(water));
        if (!Tools.has(body) || !FishingWater.isOpenWater(level, water)) {
            advance(level, body, timestamp);
            return;
        }
        if (bobber == null || bobber.isRemoved()) {
            bobber = null;
            if (timestamp >= recastAt) cast(level, body, timestamp);
            return;
        }
        bobber.tend();
        body.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(bobber, false));
        if (bobber.bitingFor() < reactAfter) return;
        reelIn(level, body, bobber.position());
        reelOut();
        recastAt = timestamp + RECAST_DELAY / 2;
        if (Unloading.shouldUnload(body) && Unloading.unloadTarget(level, body).isPresent()) advance(level, body, timestamp);
    }

    private void cast(ServerLevel level, Villager body, long timestamp) {
        body.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
        level.playSound(null, body.blockPosition(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.NEUTRAL, 0.5F, 0.4F);
        ItemStack rod = body.getInventory().getItem(Tools.slot(body));
        int lure = Math.round(EnchantmentHelper.getFishingTimeReduction(level, rod, body) * 20.0F);
        bobber = VillagerBobber.cast(level, body, castTarget(level, body), lure);
        int villagerLevel = body.getVillagerData().level();
        int fastest = FishermanSkill.fastestReaction(villagerLevel);
        reactAfter = fastest + body.getRandom().nextInt(FishermanSkill.slowestReaction(villagerLevel) - fastest + 1);
        recastAt = timestamp + RECAST_DELAY;
    }

    private Vec3 castTarget(ServerLevel level, Villager body) {
        int dx = Integer.signum(water.getX() - target.getX());
        int dz = Integer.signum(water.getZ() - target.getZ());
        BlockPos landing = water;
        for (int out = 1 + body.getRandom().nextInt(3); out > 0; out--) {
            BlockPos candidate = water.offset(dx * out, 0, dz * out);
            if (FishingWater.isOpenWater(level, candidate)) {
                landing = candidate;
                break;
            }
        }
        return new Vec3(landing.getX() + 0.3 + body.getRandom().nextDouble() * 0.4, landing.getY() + 0.9, landing.getZ() + 0.3 + body.getRandom().nextDouble() * 0.4);
    }

    private void reelOut() {
        if (bobber != null) bobber.discard();
        bobber = null;
    }

    private void reelIn(ServerLevel level, Villager body, Vec3 at) {
        ItemStack rod = body.getInventory().getItem(Tools.slot(body));
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, at)
                .withParameter(LootContextParams.TOOL, rod)
                .withParameter(LootContextParams.THIS_ENTITY, body)
                .withLuck(EnchantmentHelper.getFishingLuckBonus(level, rod, body))
                .create(LootContextParamSets.FISHING);
        for (ItemStack caught : level.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.FISHING).getRandomItems(params)) {
            ItemStack rest = body.getInventory().addItem(caught);
            if (!rest.isEmpty()) Block.popResource(level, body.blockPosition(), rest);
        }
        body.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
        level.playSound(null, BlockPos.containing(at), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.NEUTRAL, 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.FISHING, at.x, at.y + 0.2, at.z, 6, 0.2, 0.0, 0.2, 0.05);
        if (body.getRandom().nextFloat() < ToolCare.wearChance(body.getVillagerData().level())) Tools.wear(level, body);
        WorkXp.award(body, WorkXp.CAUGHT);
    }

    private void advance(ServerLevel level, Villager body, long timestamp) {
        reelOut();
        if (pickTarget(level, body)) {
            targetSince = timestamp;
            walkTo(body);
        }
    }

    private boolean pickTarget(ServerLevel level, Villager body) {
        target = null;
        water = null;
        task = null;
        Optional<BlockPos> barrel = jobSite(level, body);
        if (barrel.isEmpty()) return false;
        long now = level.getGameTime();
        unreachable.values().removeIf(until -> until < now);
        if ((Unloading.shouldUnload(body) || (!Tools.has(body) && Unloading.depositableCount(body) > 0)) && targetHomeChest(level, body)) return true;
        if (!Tools.has(body)) return false;
        Optional<FishingWater.Spot> spot = FishingWater.shore(level, FishingWater.water(level, barrel.get())).stream()
                .filter(candidate -> !unreachable.containsKey(candidate.feet()))
                .min(Comparator.comparingDouble(candidate -> candidate.feet().distToCenterSqr(body.position())));
        if (spot.isEmpty()) return false;
        target = spot.get().feet();
        water = spot.get().water();
        task = Task.FISH;
        return true;
    }

    private boolean targetHomeChest(ServerLevel level, Villager body) {
        Optional<BlockPos> chest = Unloading.unloadTarget(level, body).filter(pos -> !unreachable.containsKey(pos));
        if (chest.isEmpty()) return false;
        target = chest.get();
        task = Task.DEPOSIT;
        return true;
    }

    private boolean inReach(Villager body) {
        Vec3 center = Vec3.atBottomCenterOf(target);
        double dx = center.x - body.getX();
        double dz = center.z - body.getZ();
        double reach = task == Task.DEPOSIT ? 2.0 : 1.0;
        return dx * dx + dz * dz <= reach * reach && Math.abs(center.y - body.getY()) <= 1.5;
    }

    private boolean pathCannotReachTarget(Villager body) {
        return body.getBrain().getMemory(MemoryModuleType.PATH)
                .filter(path -> target.equals(path.getTarget()) && !path.canReach())
                .isPresent();
    }

    private boolean isWalkingToTarget(Villager body) {
        return body.getBrain().getMemory(MemoryModuleType.WALK_TARGET)
                .map(walk -> walk.getTarget().currentBlockPosition().equals(target))
                .orElse(false);
    }

    private void walkTo(Villager body) {
        body.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(target, SPEED, task == Task.FISH ? 0 : 1));
        body.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(water != null ? water : target));
    }

    /** No water with a shore to cast from within reach of its barrel. */
    public static boolean hasNoWater(ServerLevel level, Villager body) {
        return jobSite(level, body).map(barrel -> FishingWater.shore(level, FishingWater.water(level, barrel)).isEmpty()).orElse(false);
    }

    private static Optional<BlockPos> jobSite(ServerLevel level, Villager body) {
        return body.getBrain().getMemory(MemoryModuleType.JOB_SITE)
                .filter(site -> site.dimension() == level.dimension())
                .map(GlobalPos::pos);
    }
}
