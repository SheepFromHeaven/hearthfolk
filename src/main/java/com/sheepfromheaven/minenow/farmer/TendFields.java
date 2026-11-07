package com.sheepfromheaven.minenow.farmer;

import com.google.common.collect.ImmutableMap;
import com.sheepfromheaven.minenow.villager.HomeStorage;
import com.sheepfromheaven.minenow.villager.StorageTheft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class TendFields extends Behavior<Villager> {
    static final int FIELD_RADIUS = 24;
    static final int FIELD_HEIGHT = 3;
    static final int KEEP_PER_ITEM = 16;
    static final int UNLOAD_AT = 64;
    private static final float SPEED = 0.5F;
    private static final int GIVE_UP_AFTER_TICKS = 200;
    private static final int STUCK_COOLDOWN = 100;
    private static final int UNREACHABLE_COOLDOWN = 600;
    private static final int IDLE_RESCAN_DELAY = 100;
    private static final int SWING_INTERVAL = 10;

    private enum Task { HARVEST, PLANT, DEPOSIT, FETCH_HOE }

    private final List<BlockPos> candidates = new ArrayList<>();
    private final Map<BlockPos, Long> unreachable = new HashMap<>();
    private @Nullable BlockPos target;
    private @Nullable Task task;
    private long targetSince;
    private long nextStartAllowed;
    private int workLeft = -1;

    public TendFields() {
        super(ImmutableMap.of(
                MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, MemoryStatus.REGISTERED
        ), 2400);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager body) {
        if (level.getGameTime() < nextStartAllowed) return false;
        if (!FarmerRules.isFarmer(body) || !FarmerRules.griefingAllowed(level) || body.isTrading()) return false;
        if (!body.getBrain().isActive(Activity.WORK)) return false;
        candidates.clear();
        if (pickNextTarget(level, body)) return true;
        nextStartAllowed = level.getGameTime() + IDLE_RESCAN_DELAY;
        return false;
    }

    @Override
    protected void start(ServerLevel level, Villager body, long timestamp) {
        targetSince = timestamp;
        Hoes.showInHand(body, true);
        walkTo(body);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager body, long timestamp) {
        return target != null && !body.isTrading() && body.getBrain().isActive(Activity.WORK) && jobSite(level, body).isPresent();
    }

    @Override
    protected void tick(ServerLevel level, Villager body, long timestamp) {
        if (target == null || task == null) return;
        keepHoeInHand(body);
        if (inReach(body)) {
            if (task == Task.HARVEST && isRipeCrop(level.getBlockState(target)) && !finishedReaping(level, body)) return;
            act(level, body);
            workLeft = -1;
            advance(level, body, timestamp);
        } else if (pathCannotReachTarget(body) || couldNotReach(body)) {
            unreachable.put(target, timestamp + UNREACHABLE_COOLDOWN);
            advance(level, body, timestamp);
        } else if (timestamp - targetSince > GIVE_UP_AFTER_TICKS) {
            unreachable.put(target, timestamp + STUCK_COOLDOWN);
            advance(level, body, timestamp);
        } else if (!isWalkingToTarget(body)) {
            walkTo(body);
        }
    }

    @Override
    protected void stop(ServerLevel level, Villager body, long timestamp) {
        target = null;
        task = null;
        workLeft = -1;
        candidates.clear();
        body.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        body.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        Hoes.showInHand(body, false);
        nextStartAllowed = timestamp + 40;
    }

    /** Reaping takes as long as the hoe is slow; the farmer stands at the crop and swings while it works. */
    private boolean finishedReaping(ServerLevel level, Villager body) {
        if (workLeft < 0) workLeft = Hoes.harvestTicks(level, body);
        if (workLeft % SWING_INTERVAL == 0) body.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
        body.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(target));
        if (!isWalkingToTarget(body)) walkTo(body);
        return workLeft-- <= 0;
    }

    /** Vanilla's ShowTradesToPlayer empties the main hand whenever a player is close; keep it from starting while working. */
    private static void keepHoeInHand(Villager body) {
        body.getBrain().eraseMemory(MemoryModuleType.INTERACTION_TARGET);
        if (!Hoes.isHoe(body.getMainHandItem())) Hoes.showInHand(body, true);
    }

    private void advance(ServerLevel level, Villager body, long timestamp) {
        if (pickNextTarget(level, body)) {
            targetSince = timestamp;
            walkTo(body);
        }
    }

    private boolean pickNextTarget(ServerLevel level, Villager body) {
        target = null;
        task = null;
        Optional<BlockPos> jobSite = jobSite(level, body);
        if (jobSite.isEmpty()) return false;
        long now = level.getGameTime();
        unreachable.values().removeIf(until -> until < now);

        if (shouldUnload(body) && targetHomeChest(level, body, Task.DEPOSIT)) return true;
        if (!Hoes.has(body)) {
            if (homeChestHasHoe(level, body) && targetHomeChest(level, body, Task.FETCH_HOE)) return true;
            return depositableCount(body.getInventory()) > 0 && targetHomeChest(level, body, Task.DEPOSIT);
        }

        BlockPos field = nearestFieldWork(level, body, jobSite.get());
        if (field != null) {
            target = field;
            task = isRipeCrop(level.getBlockState(field)) ? Task.HARVEST : Task.PLANT;
            return true;
        }
        return depositableCount(body.getInventory()) > 0 && targetHomeChest(level, body, Task.DEPOSIT);
    }

    private @Nullable BlockPos nearestFieldWork(ServerLevel level, Villager body, BlockPos jobSite) {
        if (candidates.isEmpty()) scanField(level, jobSite);
        candidates.removeIf(pos -> unreachable.containsKey(pos) || !isFieldWork(level, body, pos));
        if (candidates.isEmpty()) return null;
        BlockPos nearest = null;
        double best = Double.MAX_VALUE;
        for (BlockPos pos : candidates) {
            double dist = pos.distToCenterSqr(body.position());
            if (dist < best) {
                best = dist;
                nearest = pos;
            }
        }
        candidates.remove(nearest);
        return nearest;
    }

    private void scanField(ServerLevel level, BlockPos jobSite) {
        for (BlockPos pos : BlockPos.betweenClosed(
                jobSite.offset(-FIELD_RADIUS, -FIELD_HEIGHT, -FIELD_RADIUS),
                jobSite.offset(FIELD_RADIUS, FIELD_HEIGHT, FIELD_RADIUS))) {
            BlockState state = level.getBlockState(pos);
            if (isRipeCrop(state) || (state.isAir() && isFarmland(level.getBlockState(pos.below())))) {
                candidates.add(pos.immutable());
            }
        }
    }

    private boolean isFieldWork(ServerLevel level, Villager body, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!Hoes.has(body)) return false;
        if (isRipeCrop(state)) return hasEmptySlot(body);
        return state.isAir() && isFarmland(level.getBlockState(pos.below())) && body.hasFarmSeeds();
    }

    static boolean homeChestHasHoe(ServerLevel level, Villager body) {
        return HomeStorage.storageIn(level, body).map(pos -> HomeStorage.chestContainer(level, pos)).map(chest -> Hoes.slot(chest) >= 0).orElse(false);
    }

    private boolean targetHomeChest(ServerLevel level, Villager body, Task chestTask) {
        Optional<BlockPos> chest = HomeStorage.storageIn(level, body)
                .filter(pos -> !unreachable.containsKey(pos) && level.getBlockState(pos).getBlock() instanceof ChestBlock);
        if (chest.isEmpty()) return false;
        target = chest.get();
        task = chestTask;
        return true;
    }

    private void act(ServerLevel level, Villager body) {
        switch (task) {
            case HARVEST -> {
                Block crop = harvest(level, body, target);
                if (crop != null) {
                    Hoes.wear(level, body);
                    plant(level, body, target, crop);
                }
            }
            case FETCH_HOE -> {
                StorageTheft.check(level, body, target);
                takeHoe(level, body, target);
                StorageTheft.recordContents(level, target);
                Hoes.showInHand(body, true);
            }
            case PLANT -> plant(level, body, target, null);
            case DEPOSIT -> {
                int before = depositableCount(body.getInventory());
                StorageTheft.check(level, body, target);
                deposit(level, body, target);
                StorageTheft.recordContents(level, target);
                if (depositableCount(body.getInventory()) >= before) unreachable.put(target, level.getGameTime() + STUCK_COOLDOWN);
            }
        }
    }

    private static @Nullable Block harvest(ServerLevel level, Villager body, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!isRipeCrop(state)) return null;
        List<ItemStack> drops = Block.getDrops(state, level, pos, null, body, ItemStack.EMPTY);
        level.destroyBlock(pos, false, body);
        for (ItemStack drop : drops) {
            ItemStack rest = body.getInventory().addItem(drop);
            if (!rest.isEmpty()) Block.popResource(level, pos, rest);
        }
        return state.getBlock();
    }

    private static void plant(ServerLevel level, Villager body, BlockPos pos, @Nullable Block preferred) {
        if (!level.getBlockState(pos).isAir() || !isFarmland(level.getBlockState(pos.below()))) return;
        SimpleContainer inventory = body.getInventory();
        int slot = -1;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.is(ItemTags.VILLAGER_PLANTABLE_SEEDS) || !(stack.getItem() instanceof BlockItem item)) continue;
            if (slot < 0 || item.getBlock() == preferred) slot = i;
            if (item.getBlock() == preferred) break;
        }
        if (slot < 0) return;
        ItemStack seeds = inventory.getItem(slot);
        BlockState planted = ((BlockItem) seeds.getItem()).getBlock().defaultBlockState();
        level.setBlockAndUpdate(pos, planted);
        level.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(body, planted));
        level.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0F, 1.0F);
        seeds.shrink(1);
        if (seeds.isEmpty()) inventory.setItem(slot, ItemStack.EMPTY);
    }

    private static void takeHoe(ServerLevel level, Villager body, BlockPos pos) {
        Container chest = HomeStorage.chestContainer(level, pos);
        if (chest == null) return;
        int slot = Hoes.slot(chest);
        if (slot < 0) return;
        ItemStack rest = body.getInventory().addItem(chest.removeItem(slot, 1));
        if (!rest.isEmpty()) chest.setItem(slot, rest);
        chest.setChanged();
    }

    private static void deposit(ServerLevel level, Villager body, BlockPos pos) {
        Container chest = HomeStorage.chestContainer(level, pos);
        if (chest == null) return;
        SimpleContainer inventory = body.getInventory();
        Map<net.minecraft.world.item.Item, Integer> kept = new HashMap<>();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) continue;
            int alreadyKept = kept.getOrDefault(stack.getItem(), 0);
            int keep = Math.min(stack.getCount(), Math.max(0, keepLimit(stack) - alreadyKept));
            kept.put(stack.getItem(), alreadyKept + keep);
            int give = stack.getCount() - keep;
            if (give <= 0) continue;
            ItemStack rest = HopperBlockEntity.addItem(null, chest, stack.copyWithCount(give), null);
            stack.setCount(keep + rest.getCount());
            if (stack.isEmpty()) inventory.setItem(i, ItemStack.EMPTY);
        }
        level.playSound(null, pos, SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.5F, 1.0F);
    }

    static int keepLimit(ItemStack stack) {
        if (Hoes.isHoe(stack)) return 1;
        if (stack.is(ItemTags.VILLAGER_PLANTABLE_SEEDS) || stack.has(net.minecraft.core.component.DataComponents.VILLAGER_FOOD)) return KEEP_PER_ITEM;
        return 0;
    }

    static int depositableCount(SimpleContainer inventory) {
        Map<net.minecraft.world.item.Item, Integer> seen = new HashMap<>();
        int total = 0;
        for (ItemStack stack : inventory.getItems()) {
            if (stack.isEmpty()) continue;
            int limit = keepLimit(stack);
            int before = seen.getOrDefault(stack.getItem(), 0);
            seen.put(stack.getItem(), before + stack.getCount());
            total += Math.max(0, before + stack.getCount() - Math.max(before, limit));
        }
        return total;
    }

    private static boolean shouldUnload(Villager body) {
        SimpleContainer inventory = body.getInventory();
        int depositable = depositableCount(inventory);
        if (depositable == 0) return false;
        return depositable >= UNLOAD_AT || !hasEmptySlot(body);
    }

    private static boolean hasEmptySlot(Villager body) {
        return body.getInventory().getItems().stream().anyMatch(ItemStack::isEmpty);
    }

    private boolean inReach(Villager body) {
        Vec3 center = Vec3.atBottomCenterOf(target);
        double dx = center.x - body.getX();
        double dz = center.z - body.getZ();
        double horizontal = task == Task.DEPOSIT ? 2.0 : 1.5;
        return dx * dx + dz * dz <= horizontal * horizontal && Math.abs(center.y - body.getY()) <= 1.5;
    }

    private boolean pathCannotReachTarget(Villager body) {
        return body.getBrain().getMemory(MemoryModuleType.PATH)
                .filter(path -> target.equals(path.getTarget()) && !path.canReach())
                .isPresent();
    }

    private boolean couldNotReach(Villager body) {
        return !body.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)
                && body.getBrain().getMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE).filter(since -> since >= targetSince).isPresent();
    }

    private boolean isWalkingToTarget(Villager body) {
        return body.getBrain().getMemory(MemoryModuleType.WALK_TARGET)
                .map(walk -> walk.getTarget().currentBlockPosition().equals(target))
                .orElse(false);
    }

    private void walkTo(Villager body) {
        body.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(target, SPEED, 1));
        body.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(target));
    }

    private static Optional<BlockPos> jobSite(ServerLevel level, Villager body) {
        return body.getBrain().getMemory(MemoryModuleType.JOB_SITE)
                .filter(site -> site.dimension() == level.dimension())
                .map(GlobalPos::pos);
    }

    private static boolean isRipeCrop(BlockState state) {
        return state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state);
    }

    private static boolean isFarmland(BlockState state) {
        return state.getBlock() instanceof FarmlandBlock || state.is(Tags.Blocks.VILLAGER_FARMLANDS);
    }
}
