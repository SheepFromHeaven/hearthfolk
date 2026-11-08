package com.sheepfromheaven.minenow.miner;

import com.google.common.collect.ImmutableMap;
import com.sheepfromheaven.minenow.villager.FurnaceTending;
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
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.BlastFurnaceBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
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
 * The mine follows {@link MineLayout}: a shaft with a platform every five layers and tunnels off each platform. It
 * puts a ladder down one wall of the shaft as it digs (filling any gap in it first) and won't dig a ladder cell it has
 * no ladder for, so it can always climb back out. It keeps the quarry floor lit with torches on its walls (making them from coal and sticks) so monsters can't spawn.
 * It smelts its raw ore in its furnace minecart, and in more furnaces beside it as it levels, with coal it holds, and when its goods pile up it carries them to its
 * home chest, climbing out of the quarry for either. Lives in CORE after MoveToTargetSink, like TendFields.
 */
public class DigQuarry extends Behavior<Villager> {
    private static final float SPEED = 0.5F;
    private static final double REACH = 4.0;
    private static final int SWING_EVERY = 5;
    private static final int GIVE_UP_AFTER_TICKS = 300;
    private static final int UNREACHABLE_COOLDOWN = 600;
    private static final int IDLE_RESCAN_DELAY = 100;
    private static final int FURNACE_RANGE = 4;
    private static final int LIT = 8;
    private static final int TORCH_RECHECK = 40;

    private final Map<BlockPos, Long> unreachable = new HashMap<>();
    private @Nullable BlockPos target;
    private @Nullable BlockPos chest;
    private boolean smelting;
    private RecipeType<? extends AbstractCookingRecipe> smeltType = RecipeType.SMELTING;
    private int smeltShare = Integer.MAX_VALUE;
    private Quarry.@Nullable TorchSpot torch;
    private boolean placingLadder;
    private boolean inShaft = true;
    private int standLevel;
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
                if (smelting) {
                    FurnaceTending.tend(level, body, chest, smeltType, Miner::smelts, smeltShare);
                    body.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
                } else {
                    Unloading.deposit(level, body, chest);
                }
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
        if (torch != null || placingLadder) {
            if (body.getEyePosition().distanceTo(Vec3.atCenterOf(target)) <= REACH) {
                if (placingLadder) placeLadder(level, body, target);
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
        placingLadder = false;
        inShaft = true;
        smelting = false;
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
        List<BlockPos> furnaces = furnaces(level, body, furnace.get());
        int raw = body.getInventory().getItems().stream().filter(Miner::smelts).mapToInt(ItemStack::getCount).sum();
        for (BlockPos pos : furnaces) {
            if (level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity cooking && Miner.smelts(cooking.getItem(0))) raw += cooking.getItem(0).getCount();
        }
        int share = Math.max(1, (raw + furnaces.size() - 1) / furnaces.size());
        for (BlockPos pos : furnaces) {
            RecipeType<? extends AbstractCookingRecipe> type = level.getBlockState(pos).getBlock() instanceof BlastFurnaceBlock ? RecipeType.BLASTING : RecipeType.SMELTING;
            if (FurnaceTending.needsTending(level, body, pos, type, Miner::smelts, share)) {
                chest = pos;
                smelting = true;
                smeltType = type;
                smeltShare = share;
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
            standLevel = target.getY() + 1;
            return true;
        }
        int bottom = furnace.get().getY() - MinerSkill.depth(body.getVillagerData().level());
        int tunnels = MinerSkill.tunnelLength(body.getVillagerData().level());
        if (now >= nextTorchCheck && (carriesTorch(body) || canMakeTorches(body))) {
            torch = quarry.darkSpots(level, bottom, LIT, tunnels).stream()
                    .filter(spot -> !spot.pos().equals(ladder.at(spot.pos().getY())))
                    .min(Comparator.comparingDouble(spot -> spot.pos().distToCenterSqr(body.position())))
                    .orElse(null);
            if (torch != null) {
                target = torch.pos();
                standLevel = target.getY();
                return true;
            }
        }
        List<BlockPos> work = quarry.nextWork(level, bottom, pickaxe, tunnels);
        if (work.isEmpty()) return false;
        BlockPos column = ladder.at(work.getFirst().getY());
        if (work.contains(column) && !carriesLadder(body)) return false;
        target = work.contains(column) ? column : work.stream()
                .filter(pos -> !unreachable.containsKey(pos))
                .min(Comparator.comparingDouble(pos -> pos.distToCenterSqr(body.position())))
                .orElse(null);
        inShaft = target != null && quarry.isShaft(target);
        if (target != null) standLevel = quarry.standLevel(target);
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
        int standY = placingLadder ? target.getY() + 1 : quarry.standLevel(target);
        boolean onLadderColumn = Math.abs(body.getX() - (ladder.top().getX() + 0.5)) <= 0.45 && Math.abs(body.getZ() - (ladder.top().getZ() + 0.5)) <= 0.45;
        if (Math.abs(body.getY() - standY) <= 1.5 && !(onLadderColumn && body.getBlockY() != standY)) {
            if (climbing) {
                climbing = false;
                LadderClimb.forgetRoute(body);
            }
            return false;
        }
        climbing = true;
        targetSince = timestamp;
        BlockPos stepTo = ladder.top().relative(ladder.facing()).atY(standY);
        LadderClimb.climbTo(body, quarry, ladder, standY, stepTo);
        return true;
    }

    /**
     * Where to stand to dig a shaft cell: on it, if there's headroom above, else (under a platform's ring, where only
     * one block is open until the next layer is out) beside it in a part of the same layer already dug.
     */
    private static BlockPos standFor(net.minecraft.world.level.Level level, BlockPos cell) {
        if (headroom(level, cell.above())) return cell.above();
        for (net.minecraft.core.Direction side : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            BlockPos beside = cell.relative(side);
            if (headroom(level, beside) && level.getBlockState(beside.below()).isFaceSturdy(level, beside.below(), net.minecraft.core.Direction.UP)) return beside;
        }
        return cell.above();
    }

    private static boolean headroom(net.minecraft.world.level.Level level, BlockPos feet) {
        return level.getBlockState(feet).getCollisionShape(level, feet).isEmpty() && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty();
    }

    /** Its furnace minecart, then the furnaces and blast furnaces nearest to it that its level lets it use. */
    private static List<BlockPos> furnaces(ServerLevel level, Villager body, BlockPos cart) {
        List<BlockPos> furnaces = new java.util.ArrayList<>();
        furnaces.add(cart);
        int extra = MinerSkill.extraFurnaces(body.getVillagerData().level());
        if (extra == 0) return furnaces;
        BlockPos.betweenClosedStream(cart.offset(-FURNACE_RANGE, -1, -FURNACE_RANGE), cart.offset(FURNACE_RANGE, 1, FURNACE_RANGE))
                .filter(pos -> !pos.equals(cart) && (level.getBlockState(pos).is(Blocks.FURNACE) || level.getBlockState(pos).is(Blocks.BLAST_FURNACE)))
                .map(BlockPos::immutable)
                .sorted(Comparator.comparingDouble(pos -> pos.distSqr(cart)))
                .limit(extra)
                .forEach(furnaces::add);
        return furnaces;
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
        if (chest != null) return chest;
        if (target == null) return null;
        boolean shaftCell = inShaft && torch == null && !placingLadder;
        return shaftCell ? standFor(body.level(), target) : target.atY(standLevel);
    }

    private void walk(Villager body) {
        if (chest != null) {
            body.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(chest, SPEED, 1));
            body.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(chest));
        } else if (target != null) {
            boolean shaftCell = inShaft && torch == null && !placingLadder;
            body.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(wantedStand(body), SPEED, shaftCell ? 1 : 2));
            body.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(target));
        }
    }

    private static Optional<BlockPos> furnace(ServerLevel level, Villager body) {
        return body.getBrain().getMemory(MemoryModuleType.JOB_SITE)
                .filter(site -> site.dimension() == level.dimension())
                .map(GlobalPos::pos);
    }
}
