package com.sheepfromheaven.minenow.fletcher;

import com.sheepfromheaven.minenow.villager.WorkXp;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Between crafts a fletcher with a bow and arrows of its own practises at a target block near its fletching table: it
 * shoots a round of {@link #ROUND} arrows from where it can see the target ({@link #MIN_DISTANCE} to
 * {@link #MAX_DISTANCE} blocks away), its aim getting steadier with its level, then walks over and collects them.
 */
public class PracticeArchery extends Behavior<Villager> {
    static final int ROUND = 3;
    private static final int SEARCH = 16;
    private static final double MIN_DISTANCE = 5.0;
    private static final double MAX_DISTANCE = 15.0;
    private static final int SHOT_EVERY = 30;
    private static final int SETTLE_TICKS = 40;
    private static final int GIVE_UP_AFTER_TICKS = 400;
    private static final int ROUND_COOLDOWN = 600;
    private static final float SPEED = 0.5F;
    private static final float VELOCITY = 1.6F;
    /** An arrow falls 0.05 a tick for the flat distance / velocity ticks it flies, so aim that much higher. */
    private static final double DROP = 0.5 * 0.05 / (VELOCITY * VELOCITY);
    private static final float[] INACCURACY = {6.0F, 4.5F, 3.0F, 2.0F, 1.0F};

    private final List<AbstractArrow> shot = new ArrayList<>();
    private @Nullable BlockPos target;
    private @Nullable BlockPos stand;
    private boolean collecting;
    private long since;
    private long nextStartAllowed;
    private int ticks;

    public PracticeArchery() {
        super(ImmutableMap.of(
                MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT
        ), 1200);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager body) {
        if (level.getGameTime() < nextStartAllowed || body.isTrading()) return false;
        if (!body.getVillagerData().profession().is(VillagerProfession.FLETCHER) || !body.getBrain().isActive(Activity.WORK)) return false;
        nextStartAllowed = level.getGameTime() + 100;
        if (body.getInventory().countItem(Items.BOW) == 0 || body.getInventory().countItem(Items.ARROW) < ROUND) return false;
        Optional<BlockPos> table = table(level, body);
        if (table.isEmpty()) return false;
        target = BlockPos.betweenClosedStream(table.get().offset(-SEARCH, -4, -SEARCH), table.get().offset(SEARCH, 4, SEARCH))
                .filter(pos -> level.getBlockState(pos).is(Blocks.TARGET))
                .map(BlockPos::immutable)
                .min(java.util.Comparator.comparingDouble(pos -> pos.distSqr(table.get())))
                .orElse(null);
        if (target == null) return false;
        stand = standFor(level, body, target);
        return stand != null;
    }

    @Override
    protected void start(ServerLevel level, Villager body, long timestamp) {
        shot.clear();
        collecting = false;
        since = timestamp;
        ticks = 0;
        body.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        body.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        walkTo(body, stand);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager body, long timestamp) {
        return target != null && body.getBrain().isActive(Activity.WORK) && !body.isTrading() && timestamp - since < GIVE_UP_AFTER_TICKS * 2;
    }

    @Override
    protected void tick(ServerLevel level, Villager body, long timestamp) {
        if (target == null || stand == null) return;
        if (collecting) {
            if (body.distanceToSqr(Vec3.atBottomCenterOf(target)) <= 6.0 || timestamp - since > GIVE_UP_AFTER_TICKS) {
                collect(body);
                target = null;
            } else if (!walkingTo(body, target)) {
                walkTo(body, target);
            }
            return;
        }
        if (body.distanceToSqr(Vec3.atBottomCenterOf(stand)) > 2.25) {
            if (!walkingTo(body, stand)) walkTo(body, stand);
            return;
        }
        body.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        body.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(target));
        ticks++;
        if (shot.size() < ROUND) {
            if (ticks % SHOT_EVERY == 0) shoot(level, body, target);
            return;
        }
        if (ticks > ROUND * SHOT_EVERY + SETTLE_TICKS) {
            collecting = true;
            since = timestamp;
            walkTo(body, target);
        }
    }

    @Override
    protected void stop(ServerLevel level, Villager body, long timestamp) {
        collect(body);
        target = null;
        stand = null;
        if (body.getMainHandItem().is(Items.BOW)) body.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        body.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        body.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        nextStartAllowed = timestamp + ROUND_COOLDOWN;
    }

    private void shoot(ServerLevel level, Villager body, BlockPos at) {
        ItemStack arrows = body.getInventory().getItems().stream().filter(stack -> stack.is(Items.ARROW)).findFirst().orElse(ItemStack.EMPTY);
        if (arrows.isEmpty()) return;
        Arrow arrow = new Arrow(level, body, arrows.copyWithCount(1), new ItemStack(Items.BOW));
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        Vec3 aim = Vec3.atCenterOf(at).subtract(arrow.position());
        double flat = Math.sqrt(aim.x * aim.x + aim.z * aim.z);
        int skill = Math.clamp(body.getVillagerData().level(), 1, INACCURACY.length) - 1;
        arrow.shoot(aim.x, aim.y + DROP * flat * flat, aim.z, VELOCITY, INACCURACY[skill]);
        level.addFreshEntity(arrow);
        level.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.ARROW_SHOOT, SoundSource.NEUTRAL, 1.0F, 1.0F / (body.getRandom().nextFloat() * 0.4F + 1.2F) + 0.5F);
        arrows.shrink(1);
        shot.add(arrow);
    }

    /** Its arrows come back to it wherever they landed, as a fletcher walking the range would pick them all up. */
    private void collect(Villager body) {
        if (!shot.isEmpty()) WorkXp.award(body, WorkXp.ARCHERY_ROUND);
        for (AbstractArrow arrow : shot) {
            if (arrow.isAlive()) arrow.discard();
            ItemStack rest = body.getInventory().addItem(new ItemStack(Items.ARROW));
            if (!rest.isEmpty()) body.spawnAtLocation((ServerLevel) body.level(), rest);
        }
        shot.clear();
    }

    /** Where it can shoot from: where it stands if that's in range with a clear view, else a spot in front of the target towards its table. */
    private static @Nullable BlockPos standFor(ServerLevel level, Villager body, BlockPos target) {
        if (inRange(body.blockPosition(), target) && seesTarget(level, body.getEyePosition(), target)) return body.blockPosition();
        Optional<BlockPos> table = table(level, body);
        if (table.isEmpty()) return null;
        Vec3 towards = Vec3.atCenterOf(table.get()).subtract(Vec3.atCenterOf(target)).multiply(1, 0, 1).normalize();
        for (double distance = 8.0; distance >= MIN_DISTANCE; distance -= 1.0) {
            BlockPos ground = BlockPos.containing(Vec3.atCenterOf(target).add(towards.scale(distance)));
            for (int dy = 3; dy >= -3; dy--) {
                BlockPos feet = ground.offset(0, dy, 0).atY(target.getY() + dy);
                if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty() || !level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) continue;
                if (!level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), net.minecraft.core.Direction.UP)) continue;
                if (seesTarget(level, Vec3.atBottomCenterOf(feet).add(0, body.getEyeHeight(), 0), target)) return feet;
            }
        }
        return null;
    }

    private static boolean inRange(BlockPos from, BlockPos target) {
        double distance = Math.sqrt(from.distSqr(target));
        return distance >= MIN_DISTANCE && distance <= MAX_DISTANCE;
    }

    private static boolean seesTarget(ServerLevel level, Vec3 eye, BlockPos target) {
        BlockHitResult hit = level.clip(new ClipContext(eye, Vec3.atCenterOf(target), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(target);
    }

    private static boolean walkingTo(Villager body, BlockPos pos) {
        return body.getBrain().getMemory(MemoryModuleType.WALK_TARGET).filter(walk -> walk.getTarget().currentBlockPosition().equals(pos)).isPresent();
    }

    private static void walkTo(Villager body, @Nullable BlockPos pos) {
        if (pos == null) return;
        body.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(pos, SPEED, 1));
    }

    private static Optional<BlockPos> table(ServerLevel level, Villager body) {
        return body.getBrain().getMemory(MemoryModuleType.JOB_SITE)
                .filter(site -> site.dimension() == level.dimension())
                .map(GlobalPos::pos);
    }
}
