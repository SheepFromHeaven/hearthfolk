package com.sheepfromheaven.minenow.farmer;

import com.google.common.collect.ImmutableMap;
import com.sheepfromheaven.minenow.villager.HomeStorage;
import com.sheepfromheaven.minenow.villager.StorageTheft;
import com.sheepfromheaven.minenow.villager.TradeTable;
import com.sheepfromheaven.minenow.villager.Unloading;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import net.neoforged.neoforge.common.SpecialPlantable;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class TendFields extends Behavior<Villager> {
    private static final float SPEED = 0.5F;
    private static final int GIVE_UP_AFTER_TICKS = 200;
    private static final int STUCK_COOLDOWN = 100;
    private static final int UNREACHABLE_COOLDOWN = 600;
    private static final int IDLE_RESCAN_DELAY = 100;
    private static final int SWING_INTERVAL = 10;

    private enum Task { HARVEST, PLANT, TILL, BONEMEAL, DEPOSIT, FETCH_HOE }

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
            if (needsHoeWork(level) && !finishedReaping(level, body)) return;
            act(level, body);
            workLeft = -1;
            advance(level, body, timestamp);
        } else if (isFieldTask() && (pathCannotReachTarget(body) || couldNotReach(body))) {
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

    private boolean needsHoeWork(ServerLevel level) {
        return (task == Task.HARVEST && isRipeCrop(level.getBlockState(target)))
                || (task == Task.TILL && Field.isTillable(level, target.below()));
    }

    /** Reaping and tilling take as long as the hoe is slow; the farmer stands at the crop and swings while it works. */
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

        if (Unloading.shouldUnload(body) && targetHomeChest(level, body, Task.DEPOSIT)) return true;
        if (!Hoes.has(body)) {
            if (homeChestHasHoe(level, body) && targetHomeChest(level, body, Task.FETCH_HOE)) return true;
            return Unloading.depositableCount(body) > 0 && targetHomeChest(level, body, Task.DEPOSIT);
        }

        BlockPos field = nearestFieldWork(level, body, jobSite.get());
        if (field != null) {
            target = field;
            task = taskAt(level, field);
            return true;
        }
        return Unloading.depositableCount(body) > 0 && targetHomeChest(level, body, Task.DEPOSIT);
    }

    private @Nullable BlockPos nearestFieldWork(ServerLevel level, Villager body, BlockPos jobSite) {
        if (candidates.isEmpty()) scanField(level, body, jobSite);
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

    private void scanField(ServerLevel level, Villager body, BlockPos jobSite) {
        int radius = Field.radiusFor(body.getVillagerData().level());
        for (BlockPos ground : Field.ground(level, jobSite, radius)) candidates.add(ground.above());
    }

    private boolean isFieldWork(ServerLevel level, Villager body, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!Hoes.has(body)) return false;
        if (isRipeCrop(state)) return true;
        if (Crops.isGrowing(state)) return body.getInventory().countItem(Items.BONE_MEAL) > 0;
        if (isFarmland(level.getBlockState(pos.below()))) return state.isAir() && Crops.hasSeeds(body);
        return Field.isClearable(state) && Field.isTillable(level, pos.below());
    }

    private Task taskAt(ServerLevel level, BlockPos pos) {
        if (isRipeCrop(level.getBlockState(pos))) return Task.HARVEST;
        if (Crops.isCrop(level.getBlockState(pos))) return Task.BONEMEAL;
        return isFarmland(level.getBlockState(pos.below())) ? Task.PLANT : Task.TILL;
    }

    private static void bonemeal(ServerLevel level, Villager body, BlockPos pos) {
        SimpleContainer inventory = body.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.is(Items.BONE_MEAL)) continue;
            if (BoneMealItem.growCrop(stack, level, pos)) {
                level.levelEvent(LevelEvent.PARTICLES_AND_SOUND_PLANT_GROWTH, pos, 15);
                body.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
            }
            if (stack.isEmpty()) inventory.setItem(i, ItemStack.EMPTY);
            return;
        }
    }

    private static void till(ServerLevel level, Villager body, BlockPos pos) {
        if (!Field.isClearable(level.getBlockState(pos)) || !Field.isTillable(level, pos.below())) return;
        if (!level.getBlockState(pos).isAir()) level.destroyBlock(pos, false, body);
        BlockState farmland = net.minecraft.world.level.block.Blocks.FARMLAND.defaultBlockState();
        level.setBlockAndUpdate(pos.below(), farmland);
        level.gameEvent(GameEvent.BLOCK_CHANGE, pos.below(), GameEvent.Context.of(body, farmland));
        level.playSound(null, pos.below(), SoundEvents.HOE_TILL.value(), SoundSource.BLOCKS, 1.0F, 1.0F);
        Hoes.wear(level, body);
        plant(level, body, pos, null);
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
                Block ripe = level.getBlockState(target).getBlock();
                if (RightClickHarvest.tryHarvest(level, body, target)) {
                    Hoes.wear(level, body);
                    plant(level, body, target, ripe);
                    break;
                }
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
            case TILL -> till(level, body, target);
            case BONEMEAL -> bonemeal(level, body, target);
            case DEPOSIT -> {
                int before = Unloading.depositableCount(body);
                Unloading.deposit(level, body, target);
                if (Unloading.depositableCount(body) >= before) unreachable.put(target, level.getGameTime() + STUCK_COOLDOWN);
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
            if (!canPlant(level, body, pos, stack)) continue;
            boolean isPreferred = stack.getItem() instanceof BlockItem item && item.getBlock() == preferred;
            if (slot < 0 || isPreferred) slot = i;
            if (isPreferred) break;
        }
        if (slot < 0) return;
        ItemStack seeds = inventory.getItem(slot);
        if (seeds.getItem() instanceof BlockItem item) {
            BlockState planted = item.getBlock().defaultBlockState();
            level.setBlockAndUpdate(pos, planted);
            level.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(body, planted));
        } else if (seeds.getItem() instanceof SpecialPlantable plantable) {
            plantable.spawnPlantAtPosition(seeds, level, pos, net.minecraft.core.Direction.DOWN);
        }
        level.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0F, 1.0F);
        seeds.shrink(1);
        if (seeds.isEmpty()) inventory.setItem(slot, ItemStack.EMPTY);
    }

    private static boolean canPlant(ServerLevel level, Villager body, BlockPos pos, ItemStack stack) {
        if (!Crops.isSeed(stack)) return false;
        if (stack.getItem() instanceof BlockItem item) return item.getBlock().defaultBlockState().canSurvive(level, pos);
        return stack.getItem() instanceof SpecialPlantable plantable && plantable.villagerCanPlantItem(body)
                && plantable.canPlacePlantAtPosition(stack, level, pos, net.minecraft.core.Direction.DOWN);
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






    private boolean inReach(Villager body) {
        Vec3 center = Vec3.atBottomCenterOf(target);
        double dx = center.x - body.getX();
        double dz = center.z - body.getZ();
        double horizontal = task == Task.DEPOSIT ? 2.0 : 1.5;
        return dx * dx + dz * dz <= horizontal * horizontal && Math.abs(center.y - body.getY()) <= 1.5;
    }

    /** A chest is solid, so a path to it never formally "reaches" it; only open field targets can be judged unreachable. */
    private boolean isFieldTask() {
        return task != Task.DEPOSIT && task != Task.FETCH_HOE;
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
        return Crops.isRipe(state);
    }

    private static boolean isFarmland(BlockState state) {
        return Field.isFarmland(state);
    }
}
