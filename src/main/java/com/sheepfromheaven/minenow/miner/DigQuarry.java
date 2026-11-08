package com.sheepfromheaven.minenow.miner;

import com.google.common.collect.ImmutableMap;
import com.sheepfromheaven.minenow.villager.HomeStorage;
import com.sheepfromheaven.minenow.villager.Tools;
import com.sheepfromheaven.minenow.villager.Unloading;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.item.Items;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The miner digs its {@link Quarry} layer by layer with its pickaxe, each block taking as long as the pickaxe's speed
 * against the block's hardness dictates (with the crack animation a player would see). What it digs goes straight into
 * its inventory, ore with up to {@link MinerSkill#extraOre} extra; blocks its pickaxe can't harvest are left in place.
 * It puts a ladder down one wall of the quarry as it digs ({@link Quarry#ladder}) and won't dig a layer it can't put a
 * ladder in, so it can always climb back out. When its goods pile up it carries them to its home chest. Lives in CORE after MoveToTargetSink, like TendFields.
 */
public class DigQuarry extends Behavior<Villager> {
    private static final float SPEED = 0.5F;
    private static final double REACH = 4.0;
    private static final int SWING_EVERY = 5;
    private static final int GIVE_UP_AFTER_TICKS = 300;
    private static final int UNREACHABLE_COOLDOWN = 600;
    private static final int IDLE_RESCAN_DELAY = 100;

    private final Map<BlockPos, Long> unreachable = new HashMap<>();
    private @Nullable BlockPos target;
    private @Nullable BlockPos chest;
    private Quarry.@Nullable Ladder ladder;
    private long targetSince;
    private long nextStartAllowed;
    private int progress;
    private boolean climbing;

    public DigQuarry() {
        super(ImmutableMap.of(
                MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT
        ), 2400);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager body) {
        if (level.getGameTime() < nextStartAllowed || body.isTrading()) return false;
        if (!body.getVillagerData().profession().is(Miner.PROFESSION) || !body.getBrain().isActive(Activity.WORK)) return false;
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
        return (target != null || chest != null) && !body.isTrading() && body.getBrain().isActive(Activity.WORK) && furnace(level, body).isPresent();
    }

    @Override
    protected void tick(ServerLevel level, Villager body, long timestamp) {
        if (chest != null) {
            if (LadderClimb.inQuarry(level, body)) {
                LadderClimb.climbOut(level, body);
                climbing = true;
                targetSince = timestamp;
                return;
            }
            if (climbing) {
                climbing = false;
                LadderClimb.forgetRoute(body);
            }
            if (body.distanceToSqr(Vec3.atBottomCenterOf(chest)) <= 4.0 && Math.abs(chest.getY() - body.getY()) <= 1.5) {
                Unloading.deposit(level, body, chest);
                advance(level, body, timestamp);
            } else if (timestamp - targetSince > GIVE_UP_AFTER_TICKS) {
                advance(level, body, timestamp);
            } else if (!body.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
                walk(body);
            }
            return;
        }
        if (target == null) return;
        ItemStack pickaxe = pickaxe(body);
        if (pickaxe.isEmpty() || !Quarry.diggable(level, target, pickaxe)) {
            advance(level, body, timestamp);
            return;
        }
        if (body.getEyePosition().distanceTo(Vec3.atCenterOf(target)) <= REACH) {
            dig(level, body, pickaxe, timestamp);
        } else if (timestamp - targetSince > GIVE_UP_AFTER_TICKS) {
            unreachable.put(target, timestamp + UNREACHABLE_COOLDOWN);
            advance(level, body, timestamp);
        } else if (!body.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
            walk(body);
        }
    }

    @Override
    protected void stop(ServerLevel level, Villager body, long timestamp) {
        if (target != null) level.destroyBlockProgress(body.getId(), target, -1);
        target = null;
        chest = null;
        progress = 0;
        body.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        body.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        Tools.showInHand(body, false);
        nextStartAllowed = timestamp + 40;
    }

    private void dig(ServerLevel level, Villager body, ItemStack pickaxe, long timestamp) {
        body.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        body.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(target));
        BlockState state = level.getBlockState(target);
        int ticks = Mining.ticks(state.getDestroySpeed(level, target), pickaxe.getDestroySpeed(state), pickaxe.isCorrectToolForDrops(state));
        if (progress % SWING_EVERY == 0) body.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
        level.destroyBlockProgress(body.getId(), target, Math.min(9, progress * 10 / ticks));
        if (++progress < ticks) return;

        List<ItemStack> drops = Block.getDrops(state, level, target, null, body, pickaxe);
        int extra = state.is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK, net.minecraft.resources.Identifier.fromNamespaceAndPath("c", "ores")))
                ? MinerSkill.extraOre(body.getVillagerData().level()) : 0;
        level.destroyBlockProgress(body.getId(), target, -1);
        level.destroyBlock(target, false, body);
        if (ladder != null && target.equals(ladder.at(target.getY()))) placeLadder(level, body, target);
        for (ItemStack drop : drops) {
            if (extra > 0) drop.grow(body.getRandom().nextInt(extra + 1));
            ItemStack rest = body.getInventory().addItem(drop);
            if (!rest.isEmpty()) Block.popResource(level, body.blockPosition(), rest);
        }
        Tools.wear(level, body);
        advance(level, body, timestamp);
    }

    private void advance(ServerLevel level, Villager body, long timestamp) {
        progress = 0;
        if (pickTarget(level, body)) {
            targetSince = timestamp;
            walk(body);
        }
    }

    private boolean pickTarget(ServerLevel level, Villager body) {
        target = null;
        chest = null;
        Optional<BlockPos> furnace = furnace(level, body);
        if (furnace.isEmpty()) return false;
        long now = level.getGameTime();
        unreachable.values().removeIf(until -> until < now);
        if (Unloading.shouldUnload(body)) {
            Optional<BlockPos> home = HomeStorage.storageIn(level, body).filter(pos -> level.getBlockState(pos).getBlock() instanceof ChestBlock);
            if (home.isPresent()) {
                chest = home.get();
                return true;
            }
        }
        ItemStack pickaxe = pickaxe(body);
        if (pickaxe.isEmpty()) return false;
        Quarry quarry = Quarry.around(level, furnace.get());
        if (quarry.isEmpty()) return false;
        ladder = quarry.ladder(level, furnace.get()).orElse(null);
        if (ladder == null) return false;
        if (!hasLadder(level, ladder.at(quarry.top())) && !placeLadder(level, body, ladder.at(quarry.top()))) return false;
        int bottom = furnace.get().getY() - MinerSkill.depth(body.getVillagerData().level());
        List<BlockPos> layer = quarry.nextLayer(level, bottom, pickaxe);
        if (layer.isEmpty() || (!hasLadder(level, ladder.at(layer.getFirst().getY())) && !carriesLadder(body))) return false;
        BlockPos column = ladder.at(layer.getFirst().getY());
        target = layer.contains(column) ? column : layer.stream()
                .filter(pos -> !unreachable.containsKey(pos))
                .min(Comparator.comparingDouble(pos -> pos.distToCenterSqr(body.position())))
                .orElse(null);
        return target != null;
    }

    private static boolean hasLadder(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).is(Blocks.LADDER);
    }

    private static boolean carriesLadder(Villager body) {
        return body.getInventory().countItem(Items.LADDER) > 0;
    }

    /** Puts a ladder from its inventory against the column's wall, so it can always climb back out of what it dug. */
    private boolean placeLadder(ServerLevel level, Villager body, BlockPos pos) {
        if (ladder == null || !carriesLadder(body) || !level.getBlockState(pos).isAir()) return false;
        BlockState state = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, ladder.facing());
        if (!state.canSurvive(level, pos)) return false;
        level.setBlock(pos, state, 3);
        level.playSound(null, pos, SoundEvents.LADDER_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
        body.getInventory().removeItem(body.getInventory().getItems().indexOf(body.getInventory().getItems().stream().filter(stack -> stack.is(Items.LADDER)).findFirst().get()), 1);
        return true;
    }

    private static ItemStack pickaxe(Villager body) {
        int slot = Tools.slot(body);
        return slot < 0 ? ItemStack.EMPTY : body.getInventory().getItem(slot);
    }

    private void walk(Villager body) {
        if (chest != null) {
            body.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(chest, SPEED, 1));
            body.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(chest));
        } else if (target != null) {
            body.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(target.above(), SPEED, 1));
            body.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(target));
        }
    }

    private static Optional<BlockPos> furnace(ServerLevel level, Villager body) {
        return body.getBrain().getMemory(MemoryModuleType.JOB_SITE)
                .filter(site -> site.dimension() == level.dimension())
                .map(GlobalPos::pos);
    }
}
