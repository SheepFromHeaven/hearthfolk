package com.sheepfromheaven.minenow.forester;

import com.sheepfromheaven.minenow.villager.WorkXp;
import com.google.common.collect.ImmutableMap;
import com.sheepfromheaven.minenow.miner.Mining;
import com.sheepfromheaven.minenow.villager.HomeStorage;
import com.sheepfromheaven.minenow.villager.Tools;
import com.sheepfromheaven.minenow.villager.Unloading;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The forester fells the trees of its {@link Grove} within {@link ForesterSkill#groveRange} of its nursery: it walks
 * up to the trunk and cuts the tree log by log from the top down with its axe, each log taking a player's time for that
 * axe, the logs going straight into its inventory; trees on more than one stump (2x2) only from expert on ({@link ForesterSkill#fellsBigTrees}). On each stump it plants its nursery's sapling, or remembers the spot
 * until it has one ({@link Forester#REPLANT}). It gathers the saplings, apples and sticks lying in its grove ({@link Forester#GATHERS}). When its goods pile up it carries them to its home chest. Lives in CORE
 * after MoveToTargetSink, like TendFields.
 */
public class FellTrees extends Behavior<Villager> implements com.sheepfromheaven.minenow.villager.FieldWork {
    private static final float SPEED = 0.5F;
    private static final double REACH = 3.0;
    private static final int SWING_EVERY = 5;
    private static final int GIVE_UP_AFTER_TICKS = 300;
    private static final int UNREACHABLE_COOLDOWN = 1200;
    private static final int IDLE_RESCAN_DELAY = 100;

    private final Map<BlockPos, Long> unreachable = new HashMap<>();
    private Grove.@Nullable Tree tree;
    private @Nullable BlockPos plantAt;
    private @Nullable ItemEntity gather;
    private @Nullable BlockPos chest;
    private long targetSince;
    private long nextStartAllowed;
    private int progress;

    public FellTrees() {
        super(ImmutableMap.of(
                MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT
        ), 2400);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager body) {
        if (level.getGameTime() < nextStartAllowed || body.isTrading()) return false;
        if (!body.getVillagerData().profession().is(Forester.PROFESSION) || !body.getBrain().isActive(Activity.WORK)) return false;
        if (pickTarget(level, body)) return true;
        nextStartAllowed = level.getGameTime() + IDLE_RESCAN_DELAY;
        return false;
    }

    @Override
    protected void start(ServerLevel level, Villager body, long timestamp) {
        targetSince = timestamp;
        Tools.showInHand(body, true);
        walk(body);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager body, long timestamp) {
        return (tree != null || plantAt != null || gather != null || chest != null) && !body.isTrading() && body.getBrain().isActive(Activity.WORK) && nursery(level, body).isPresent();
    }

    @Override
    protected void tick(ServerLevel level, Villager body, long timestamp) {
        if (!Unloading.isTool(body, body.getMainHandItem())) Tools.showInHand(body, true);
        if (chest != null) {
            if (body.distanceToSqr(Vec3.atBottomCenterOf(chest)) <= 4.0 && Math.abs(chest.getY() - body.getY()) <= 1.5) {
                Unloading.deposit(level, body, chest);
                advance(level, body, timestamp);
            } else if (timestamp - targetSince > GIVE_UP_AFTER_TICKS) {
                advance(level, body, timestamp);
            } else if (!walkingTo(body, chest)) {
                walk(body);
            }
            return;
        }
        if (gather != null) {
            if (!gather.isAlive() || timestamp - targetSince > GIVE_UP_AFTER_TICKS) {
                if (gather.isAlive()) unreachable.put(gather.blockPosition(), timestamp + UNREACHABLE_COOLDOWN);
                advance(level, body, timestamp);
            } else if (!walkingTo(body, gather.blockPosition())) {
                walk(body);
            }
            return;
        }
        if (plantAt != null) {
            if (body.distanceToSqr(Vec3.atBottomCenterOf(plantAt)) <= 6.0) {
                plant(level, body, plantAt);
                advance(level, body, timestamp);
            } else if (timestamp - targetSince > GIVE_UP_AFTER_TICKS) {
                unreachable.put(plantAt, timestamp + UNREACHABLE_COOLDOWN);
                advance(level, body, timestamp);
            } else if (!walkingTo(body, plantAt)) {
                walk(body);
            }
            return;
        }
        if (tree == null) return;
        BlockPos log = tree.logs().stream().filter(pos -> level.getBlockState(pos).is(BlockTags.LOGS)).findFirst().orElse(null);
        if (log == null) {
            replant(level, body, tree);
            advance(level, body, timestamp);
            return;
        }
        ItemStack axe = axe(body);
        if (axe.isEmpty()) {
            advance(level, body, timestamp);
            return;
        }
        double horizontal = Math.sqrt(Math.pow(body.getX() - (log.getX() + 0.5), 2) + Math.pow(body.getZ() - (log.getZ() + 0.5), 2));
        if (horizontal <= REACH && log.getY() >= body.getBlockY() - 1) {
            chop(level, body, axe, log, timestamp);
        } else if (timestamp - targetSince > GIVE_UP_AFTER_TICKS) {
            unreachable.put(tree.base(), timestamp + UNREACHABLE_COOLDOWN);
            advance(level, body, timestamp);
        } else if (!walkingTo(body, tree.base())) {
            walk(body);
        }
    }

    @Override
    protected void stop(ServerLevel level, Villager body, long timestamp) {
        tree = null;
        plantAt = null;
        gather = null;
        chest = null;
        progress = 0;
        body.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        body.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        Tools.showInHand(body, false);
        nextStartAllowed = timestamp + 40;
    }

    private void chop(ServerLevel level, Villager body, ItemStack axe, BlockPos log, long timestamp) {
        body.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        body.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(log));
        BlockState state = level.getBlockState(log);
        int ticks = Mining.ticks(state.getDestroySpeed(level, log), axe.getDestroySpeed(state), axe.isCorrectToolForDrops(state));
        if (progress % SWING_EVERY == 0) body.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
        level.destroyBlockProgress(body.getId(), log, Math.min(9, progress * 10 / ticks));
        if (++progress < ticks) return;
        progress = 0;
        targetSince = timestamp;
        List<ItemStack> drops = Block.getDrops(state, level, log, null, body, axe);
        level.destroyBlockProgress(body.getId(), log, -1);
        level.destroyBlock(log, false, body);
        for (ItemStack drop : drops) {
            ItemStack rest = body.getInventory().addItem(drop);
            if (!rest.isEmpty()) Block.popResource(level, body.blockPosition(), rest);
        }
        Tools.wear(level, body);
        WorkXp.award(body, WorkXp.FELLED);
    }

    /** A sapling on every stump it has one for; the rest it remembers for later. */
    private static void replant(ServerLevel level, Villager body, Grove.Tree tree) {
        List<BlockPos> pending = new ArrayList<>(body.getData(Forester.REPLANT));
        for (BlockPos stump : tree.stumps()) {
            if (!plant(level, body, stump) && !pending.contains(stump)) pending.add(stump);
        }
        body.setData(Forester.REPLANT, List.copyOf(pending));
    }

    private static boolean plant(ServerLevel level, Villager body, BlockPos pos) {
        Optional<NurseryTree> kind = nurseryTree(level, body);
        if (kind.isEmpty()) return false;
        BlockState sapling = kind.get().plant().defaultBlockState();
        boolean planted = false;
        int slot = slotOf(body, kind.get());
        if (slot >= 0 && level.getBlockState(pos).isAir() && sapling.canSurvive(level, pos)) {
            level.setBlock(pos, sapling, 3);
            level.playSound(null, pos, SoundEvents.GRASS_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
            body.getInventory().getItem(slot).shrink(1);
            planted = true;
        }
        if (planted || !level.getBlockState(pos).isAir()) {
            List<BlockPos> pending = new ArrayList<>(body.getData(Forester.REPLANT));
            if (pending.remove(pos)) body.setData(Forester.REPLANT, List.copyOf(pending));
        }
        return planted;
    }

    private void advance(ServerLevel level, Villager body, long timestamp) {
        progress = 0;
        if (pickTarget(level, body)) {
            targetSince = timestamp;
            walk(body);
        }
    }

    private boolean pickTarget(ServerLevel level, Villager body) {
        tree = null;
        plantAt = null;
        gather = null;
        chest = null;
        Optional<BlockPos> nursery = nursery(level, body);
        if (nursery.isEmpty()) return false;
        long now = level.getGameTime();
        unreachable.values().removeIf(until -> until < now);
        if (Unloading.shouldUnload(body) || (!Tools.has(body) && Unloading.depositableCount(body) > 0)) {
            Optional<BlockPos> home = Unloading.unloadTarget(level, body);
            if (home.isPresent()) {
                chest = home.get();
                return true;
            }
        }
        Optional<NurseryTree> kind = nurseryTree(level, body);
        if (kind.isPresent() && slotOf(body, kind.get()) >= 0) {
            plantAt = body.getData(Forester.REPLANT).stream()
                    .filter(pos -> !unreachable.containsKey(pos))
                    .min(Comparator.comparingDouble(pos -> pos.distToCenterSqr(body.position())))
                    .orElse(null);
            if (plantAt != null) return true;
        }
        int range = ForesterSkill.groveRange(body.getVillagerData().level());
        gather = level.getEntitiesOfClass(ItemEntity.class, new AABB(nursery.get()).inflate(range, Grove.HEIGHT, range),
                        item -> Forester.GATHERS.contains(item.getItem().getItem()) && body.getInventory().canAddItem(item.getItem())
                                && !unreachable.containsKey(item.blockPosition())).stream()
                .min(Comparator.comparingDouble(body::distanceToSqr))
                .orElse(null);
        if (gather != null) return true;
        if (!Tools.has(body)) return false;
        boolean bigTrees = ForesterSkill.fellsBigTrees(body.getVillagerData().level());
        tree = Grove.trees(level, nursery.get(), range).stream()
                .filter(candidate -> !unreachable.containsKey(candidate.base()) && (bigTrees || !candidate.big()))
                .min(Comparator.comparingDouble(candidate -> candidate.base().distToCenterSqr(body.position())))
                .orElse(null);
        return tree != null;
    }

    private static int slotOf(Villager body, NurseryTree kind) {
        for (int i = 0; i < body.getInventory().getContainerSize(); i++) {
            if (body.getInventory().getItem(i).is(kind.sapling())) return i;
        }
        return -1;
    }

    private static Optional<NurseryTree> nurseryTree(ServerLevel level, Villager body) {
        return nursery(level, body).map(level::getBlockState).filter(state -> state.is(Forester.NURSERY.get())).map(Forester::tree);
    }

    private static ItemStack axe(Villager body) {
        int slot = Tools.slot(body);
        return slot < 0 ? ItemStack.EMPTY : body.getInventory().getItem(slot);
    }

    /** Vanilla's work package keeps pulling a villager back near its job site, so the walk target is re-pinned every tick. */
    private static boolean walkingTo(Villager body, BlockPos pos) {
        return body.getBrain().getMemory(MemoryModuleType.WALK_TARGET).filter(walk -> walk.getTarget().currentBlockPosition().equals(pos)).isPresent();
    }

    private void walk(Villager body) {
        BlockPos to = chest != null ? chest : gather != null ? gather.blockPosition() : plantAt != null ? plantAt : tree != null ? tree.base() : null;
        if (to == null) return;
        body.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(to, SPEED, 1));
        body.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(to));
    }

    private static Optional<BlockPos> nursery(ServerLevel level, Villager body) {
        return body.getBrain().getMemory(MemoryModuleType.JOB_SITE)
                .filter(site -> site.dimension() == level.dimension())
                .map(GlobalPos::pos);
    }
}
