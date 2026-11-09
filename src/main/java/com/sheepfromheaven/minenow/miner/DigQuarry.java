package com.sheepfromheaven.minenow.miner;

import com.sheepfromheaven.minenow.villager.WorkXp;
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
import java.util.Set;

/**
 * The miner digs its {@link Quarry} layer by layer with its pickaxe, each block taking as long as the pickaxe's speed
 * against the block's hardness dictates (with the crack animation a player would see). What it digs goes straight into
 * its inventory, ore with up to {@link MinerSkill#extraOre} extra; blocks its pickaxe can't harvest are left in place.
 * The mine follows {@link MineLayout}: a shaft with a platform every five layers and tunnels off each platform. It
 * puts a ladder down one wall of the shaft as it digs (filling any gap in it first) and won't dig a ladder cell it has
 * no ladder for, so it can always climb back out. It keeps the quarry floor lit with torches on its walls (making them from coal and sticks) so monsters can't spawn.
 * When its goods pile up it carries them to its home chest, climbing out of the quarry; its raw ore goes to the smelter. Lives in CORE after MoveToTargetSink, like TendFields.
 */
public class DigQuarry extends Behavior<Villager> {
    private static final float SPEED = 0.5F;
    private static final double REACH = 4.0;
    private static final int SWING_EVERY = 5;
    private static final int GIVE_UP_AFTER_TICKS = 300;
    private static final int UNREACHABLE_COOLDOWN = 600;
    private static final int IDLE_RESCAN_DELAY = 100;
    private static final int LIT = 8;
    private static final int TORCH_RECHECK = 40;
    private static final double EYE_HEIGHT = 1.62;

    private final Map<BlockPos, Long> unreachable = new HashMap<>();
    private @Nullable BlockPos target;
    private @Nullable BlockPos chest;
    private Quarry.@Nullable TorchSpot torch;
    private boolean placingLadder;
    private boolean sealing;
    private boolean inShaft = true;
    private @Nullable BlockPos stand;
    private long nextTorchCheck;
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
            Optional<BlockPos> cart = furnace(level, body);
            if (cart.isPresent() && LadderClimb.inQuarry(level, body)) {
                LadderClimb.climbOut(level, body, cart.get());
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
            } else if (!walkingWhereWanted(body)) {
                walk(body);
            }
            return;
        }
        if (target == null) return;
        if (body.getEyePosition().distanceTo(Vec3.atCenterOf(target)) > REACH && useLadder(level, body, timestamp)) return;
        if (torch != null || placingLadder || sealing) {
            if (body.getEyePosition().distanceTo(Vec3.atCenterOf(target)) <= REACH) {
                if (placingLadder) placeLadder(level, body, target);
                else if (sealing) seal(level, body, target);
                else placeTorch(level, body, timestamp);
                advance(level, body, timestamp);
            } else if (timestamp - targetSince > GIVE_UP_AFTER_TICKS) {
                advance(level, body, timestamp);
            } else if (!walkingWhereWanted(body)) {
                walk(body);
            }
            return;
        }
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
        } else if (!walkingWhereWanted(body)) {
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
        WorkXp.award(body, WorkXp.MINED);
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
        torch = null;
        stand = null;
        placingLadder = false;
        sealing = false;
        inShaft = true;
        Optional<BlockPos> furnace = furnace(level, body);
        if (furnace.isEmpty()) return false;
        long now = level.getGameTime();
        unreachable.values().removeIf(until -> until < now);
        if (Unloading.shouldUnload(body)) {
            Optional<BlockPos> home = Unloading.unloadTarget(level, body);
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
        Optional<BlockPos> missing = quarry.missingLadder(level);
        if (missing.isPresent()) {
            if (!carriesLadder(body)) return false;
            target = missing.get();
            placingLadder = true;
            stand = ladder.at(target.getY() + 1);
            return true;
        }
        int bottom = furnace.get().getY() - MinerSkill.depth(body.getVillagerData().level());
        int tunnels = MinerSkill.tunnelLength(body.getVillagerData().level());
        if (carriesCobblestone(body)) {
            Map<Integer, Set<BlockPos>> floods = new HashMap<>();
            for (BlockPos spot : quarry.sealSpots(level, body.blockPosition(), bottom, tunnels).stream()
                    .sorted(Comparator.comparingDouble(pos -> pos.distToCenterSqr(body.position()))).toList()) {
                BlockPos from = standFor(level, quarry, spot, floods);
                if (from == null) continue;
                target = spot;
                stand = from;
                sealing = true;
                return true;
            }
        }
        if (now >= nextTorchCheck && (carriesTorch(body) || canMakeTorches(body))) {
            torch = quarry.darkSpots(level, bottom, LIT, tunnels).stream()
                    .filter(spot -> !spot.pos().equals(ladder.at(spot.pos().getY())))
                    .min(Comparator.comparingDouble(spot -> spot.pos().distToCenterSqr(body.position())))
                    .orElse(null);
            if (torch != null) {
                target = torch.pos();
                stand = standFor(level, quarry, target, new HashMap<>());
                if (stand != null) return true;
                torch = null;
                target = null;
            }
        }
        int deepestPlatform = MineLayout.deepestPlatform(quarry.top(), level.getBestNeighborSignal(furnace.get()));
        List<BlockPos> work = quarry.nextWork(level, bottom, pickaxe, tunnels, deepestPlatform);
        if (work.isEmpty()) return false;
        Optional<BlockPos> column = work.stream().filter(pos -> pos.equals(ladder.at(pos.getY()))).max(Comparator.comparingInt(BlockPos::getY));
        if (column.isPresent() && !carriesLadder(body)) return false;
        Map<Integer, Set<BlockPos>> floods = new HashMap<>();
        List<BlockPos> candidates = column.isPresent() ? List.of(column.get()) : work.stream().filter(pos -> !unreachable.containsKey(pos)).toList();
        target = null;
        stand = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos candidate : candidates) {
            BlockPos spot = standFor(level, quarry, candidate, floods);
            if (spot == null) continue;
            double distance = spot.distToCenterSqr(body.position());
            if (distance < bestDistance) {
                bestDistance = distance;
                target = candidate;
                stand = spot;
            }
        }
        inShaft = target != null && quarry.isShaft(target);
        return target != null;
    }

    /**
     * Takes the ladder when the work is more than a step above or below: vanilla paths never use ladders, and a deep
     * mine can't be reached by dropping in. True while still on the way.
     */
    private boolean useLadder(ServerLevel level, Villager body, long timestamp) {
        Optional<BlockPos> furnace = furnace(level, body);
        if (furnace.isEmpty() || ladder == null) return false;
        Quarry quarry = Quarry.around(level, furnace.get());
        if (stand == null) return false;
        int standY = stand.getY();
        boolean onLadderColumn = Math.abs(body.getX() - (ladder.top().getX() + 0.5)) <= 0.45 && Math.abs(body.getZ() - (ladder.top().getZ() + 0.5)) <= 0.45;
        boolean atStand = Math.abs(body.getY() - standY) < 0.2
                && Math.abs(body.getX() - (stand.getX() + 0.5)) <= 0.6 && Math.abs(body.getZ() - (stand.getZ() + 0.5)) <= 0.6;
        boolean nearEnough = atStand || (Math.abs(body.getY() - standY) <= 1.5 && !quarry.platformBetween(body.getBlockY(), standY));
        if (atStand || (nearEnough && !onLadderColumn)) {
            if (climbing) {
                climbing = false;
                LadderClimb.forgetRoute(body);
            }
            return false;
        }
        climbing = true;
        targetSince = timestamp;
        LadderClimb.climbTo(body, quarry, ladder, standY, stand);
        return true;
    }

    /**
     * Where to stand to work on {@code cell}: the nearest spot within reach that the miner can get to on its ladder
     * ({@link Quarry#reachable}) in the cell's own layer, the two below or the one above, the nearest first (the layer
     * order breaks ties: sideways, upwards, downwards). Null when nothing it can reach is close enough yet.
     */
    private static @Nullable BlockPos standFor(ServerLevel level, Quarry quarry, BlockPos cell, Map<Integer, Set<BlockPos>> floods) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        int[] levels = {cell.getY(), cell.getY() - 1, cell.getY() - 2, cell.getY() + 1};
        for (int preference = 0; preference < levels.length; preference++) {
            for (BlockPos spot : floods.computeIfAbsent(levels[preference], y -> quarry.reachable(level, y))) {
                double reach = Vec3.atBottomCenterOf(spot).add(0, EYE_HEIGHT, 0).distanceTo(Vec3.atCenterOf(cell));
                if (spot.equals(cell) || reach > REACH) continue;
                double distance = spot.distSqr(cell) + preference * 0.1;
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = spot;
                }
            }
        }
        return best;
    }

    private static boolean headroom(net.minecraft.world.level.Level level, BlockPos feet) {
        return level.getBlockState(feet).getCollisionShape(level, feet).isEmpty() && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty();
    }

    private static boolean carriesCobblestone(Villager body) {
        return body.getInventory().hasAnyMatching(DigQuarry::isCobblestone);
    }

    private static boolean isCobblestone(ItemStack stack) {
        return stack.is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, net.minecraft.resources.Identifier.fromNamespaceAndPath("c", "cobblestones")));
    }

    /** Fills fluid or a cave opening with cobblestone from its stock, the way a player keeps a mine dry and closed. */
    private static void seal(ServerLevel level, Villager body, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.canBeReplaced() && state.getFluidState().isEmpty()) return;
        Optional<ItemStack> stone = body.getInventory().getItems().stream().filter(DigQuarry::isCobblestone).findFirst();
        if (stone.isEmpty() || !(stone.get().getItem() instanceof net.minecraft.world.item.BlockItem block)) return;
        level.setBlock(pos, block.getBlock().defaultBlockState(), 3);
        level.playSound(null, pos, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
        body.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
        stone.get().shrink(1);
    }

    private static boolean carriesTorch(Villager body) {
        return body.getInventory().countItem(Items.TORCH) > 0;
    }

    private static boolean canMakeTorches(Villager body) {
        return body.getInventory().hasAnyMatching(stack -> stack.is(net.minecraft.tags.ItemTags.COALS)) && body.getInventory().countItem(Items.STICK) > 0;
    }

    /** Puts a torch on the quarry wall, making four from a coal and a stick if it has none, the way a player would craft them. */
    private void placeTorch(ServerLevel level, Villager body, long timestamp) {
        nextTorchCheck = timestamp + TORCH_RECHECK;
        if (torch == null || !level.getBlockState(torch.pos()).isAir()) return;
        if (!carriesTorch(body)) {
            if (!canMakeTorches(body)) return;
            body.getInventory().getItems().stream().filter(stack -> stack.is(net.minecraft.tags.ItemTags.COALS)).findFirst().ifPresent(stack -> stack.shrink(1));
            body.getInventory().getItems().stream().filter(stack -> stack.is(Items.STICK)).findFirst().ifPresent(stack -> stack.shrink(1));
            body.getInventory().addItem(new ItemStack(Items.TORCH, 4));
        }
        BlockState state = Blocks.WALL_TORCH.defaultBlockState().setValue(net.minecraft.world.level.block.WallTorchBlock.FACING, torch.facing());
        if (!state.canSurvive(level, torch.pos())) return;
        level.setBlock(torch.pos(), state, 3);
        level.playSound(null, torch.pos(), SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
        body.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
        body.getInventory().getItems().stream().filter(stack -> stack.is(Items.TORCH)).findFirst().ifPresent(stack -> stack.shrink(1));
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

    /** Vanilla's work package keeps pulling a villager back within 9 blocks of its job site, so the walk target is re-pinned every tick. */
    private boolean walkingWhereWanted(Villager body) {
        BlockPos wanted = wantedStand(body);
        return wanted != null && body.getBrain().getMemory(MemoryModuleType.WALK_TARGET)
                .filter(walk -> walk.getTarget().currentBlockPosition().equals(wanted))
                .isPresent();
    }

    private @Nullable BlockPos wantedStand(Villager body) {
        return chest != null ? chest : stand;
    }

    private void walk(Villager body) {
        if (chest != null) {
            body.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(chest, SPEED, 1));
            body.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(chest));
        } else if (target != null && stand != null) {
            body.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(stand, SPEED, 0));
            body.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(target));
        }
    }

    private static Optional<BlockPos> furnace(ServerLevel level, Villager body) {
        return body.getBrain().getMemory(MemoryModuleType.JOB_SITE)
                .filter(site -> site.dimension() == level.dimension())
                .map(GlobalPos::pos);
    }
}
