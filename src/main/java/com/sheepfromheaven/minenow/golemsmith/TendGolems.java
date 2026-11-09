package com.sheepfromheaven.minenow.golemsmith;

import com.google.common.collect.ImmutableMap;
import com.sheepfromheaven.minenow.villager.CraftSkill;
import com.sheepfromheaven.minenow.villager.Economy;
import com.sheepfromheaven.minenow.villager.Walking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
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
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarvedPumpkinBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The golemsmith's work: it mends the hurt iron golems within {@link #REPAIR_RANGE} of its workshop with ingots (one
 * mends a quarter), and from journeyman ({@link GolemsmithSkill}) builds a golem beside the workshop while the village
 * (48 blocks) has fewer golems than its level allows: whenever it has an iron block it adds it to vanilla's T, so a
 * half-built golem stands by the workshop while it earns the next block ({@link Golemsmith#SITE}), and once the T is
 * whole it puts the carved pumpkin on, so vanilla's pumpkin brings it to life. A poppy within {@link #MARK_RANGE} of
 * the workshop marks where to build; it moves aside for the base and is planted back under the new golem. Each block and each mend takes as long as a craft at its level.
 * Lives in CORE after MoveToTargetSink, like the other trades.
 */
public class TendGolems extends Behavior<Villager> {
    private static final int REPAIR_RANGE = 24;
    private static final int VILLAGE_RANGE = 48;
    private static final int MARK_RANGE = 8;
    private static final double REACH = 3.5;
    private static final float SPEED = 0.5F;
    private static final int RESCAN = 100;
    private static final int SWING_EVERY = 10;

    private @Nullable IronGolem patient;
    private @Nullable GolemSite site;
    private int working;
    private long nextStartAllowed;

    public TendGolems() {
        super(ImmutableMap.of(
                MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT
        ), 2400);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager body) {
        if (level.getGameTime() < nextStartAllowed || body.isTrading()) return false;
        if (!body.getVillagerData().profession().is(Golemsmith.PROFESSION) || !body.getBrain().isActive(Activity.WORK)) return false;
        if (plan(level, body)) return true;
        nextStartAllowed = level.getGameTime() + RESCAN;
        return false;
    }

    @Override
    protected void start(ServerLevel level, Villager body, long timestamp) {
        working = 0;
        walk(level, body);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager body, long timestamp) {
        if (!body.getBrain().isActive(Activity.WORK) || body.isTrading()) return false;
        if (patient != null) return patient.isAlive() && Economy.count(level, body, Items.IRON_INGOT) > 0 && mendable(patient);
        return site != null;
    }

    @Override
    protected void tick(ServerLevel level, Villager body, long timestamp) {
        BlockPos target = patient != null ? patient.blockPosition() : site != null ? site.stand() : null;
        if (target == null) return;
        double reach = patient != null ? REACH : 1.0;
        if (body.position().distanceTo(patient != null ? patient.position() : Vec3.atBottomCenterOf(target)) > reach) {
            if (!Walking.towards(body, target)) walk(level, body);
            return;
        }
        body.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        if (working % SWING_EVERY == 0) {
            body.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
            if (patient != null) level.playSound(null, target, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.4F, 1.2F);
        }
        if (++working < CraftSkill.ticks(body.getVillagerData().level())) return;
        if (patient != null) {
            working = 0;
            mend(level, body, patient);
        } else if (site != null) {
            stackNext(level, body, site, timestamp);
        }
    }

    @Override
    protected void stop(ServerLevel level, Villager body, long timestamp) {
        patient = null;
        site = null;
        working = 0;
        body.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        body.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
    }

    private boolean plan(ServerLevel level, Villager body) {
        patient = null;
        site = null;
        Optional<BlockPos> workshop = workshop(level, body);
        if (workshop.isEmpty()) return false;
        if (Economy.count(level, body, Items.IRON_INGOT) > 0) {
            patient = level.getEntitiesOfClass(IronGolem.class, new AABB(workshop.get()).inflate(REPAIR_RANGE), TendGolems::mendable).stream()
                    .min(Comparator.comparingDouble(golem -> golem.distanceToSqr(body)))
                    .orElse(null);
            if (patient != null) return true;
        }
        int villagerLevel = body.getVillagerData().level();
        if (!GolemsmithSkill.buildsGolems(villagerLevel) || golems(level, workshop.get()) >= GolemsmithSkill.maxGolems(villagerLevel)) return false;
        site = site(level, body, workshop.get());
        if (site != null && !hasNext(level, body, site)) site = null;
        return site != null;
    }

    /** One more block of the golem, then it's done for now; the pumpkin brings it to life. Gives up when the site's been built over. */
    private void stackNext(ServerLevel level, Villager body, GolemSite at, long timestamp) {
        if (!fits(level, at) || !hasNext(level, body, at)) {
            site = null;
            return;
        }
        List<BlockPos> steps = at.steps();
        for (int i = 0; i < steps.size(); i++) {
            BlockPos pos = steps.get(i);
            boolean pumpkin = i == steps.size() - 1;
            if (!open(level, at, pos)) continue;
            if (!level.getEntities((net.minecraft.world.entity.Entity) null, new AABB(pos)).isEmpty()) return;
            working = 0;
            Economy.take(level, body, pumpkin ? Items.CARVED_PUMPKIN : Items.IRON_BLOCK, 1);
            BlockState state = pumpkin
                    ? Blocks.CARVED_PUMPKIN.defaultBlockState().setValue(CarvedPumpkinBlock.FACING, at.facing())
                    : Blocks.IRON_BLOCK.defaultBlockState();
            body.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(pos));
            body.setData(Golemsmith.SITE, at);
            level.setBlock(pos, state, 3);
            level.playSound(null, pos, state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 0.8F);
            if (pumpkin) {
                level.getEntitiesOfClass(IronGolem.class, new AABB(at.base()).inflate(1.5), golem -> golem.tickCount == 0)
                        .forEach(golem -> golem.setPlayerCreated(false));
                body.removeData(Golemsmith.SITE);
                BlockState poppy = Blocks.POPPY.defaultBlockState();
                if (at.marked() && level.getBlockState(at.base()).isAir() && poppy.canSurvive(level, at.base())) level.setBlock(at.base(), poppy, 3);
            }
            site = null;
            nextStartAllowed = timestamp + RESCAN;
            return;
        }
    }

    /** It holds what goes on next: an iron block while the T is unfinished, then the pumpkin. */
    private static boolean hasNext(ServerLevel level, Villager body, GolemSite at) {
        boolean ironDone = ironPlaced(level, at) == at.steps().size() - 1;
        return Economy.count(level, body, ironDone ? Items.CARVED_PUMPKIN : Items.IRON_BLOCK) >= 1;
    }

    private static long ironPlaced(ServerLevel level, GolemSite at) {
        return at.ironCells().stream().filter(pos -> level.getBlockState(pos).is(Blocks.IRON_BLOCK)).count();
    }

    /**
     * Where to build: the golem it has started, else a spot a poppy marks within {@link #MARK_RANGE} of the workshop
     * (and nowhere else while one does), else the nearest spot two to four blocks out from the workshop. A spot needs
     * solid ground, room for the T and a place to stand in front of it.
     */
    private static @Nullable GolemSite site(ServerLevel level, Villager body, BlockPos workshop) {
        if (body.hasData(Golemsmith.SITE)) {
            GolemSite started = body.getData(Golemsmith.SITE);
            if (started.base().closerThan(workshop, MARK_RANGE + 2) && fits(level, started)) return started;
            body.removeData(Golemsmith.SITE);
        }
        List<BlockPos> poppies = BlockPos.betweenClosedStream(workshop.offset(-MARK_RANGE, -2, -MARK_RANGE), workshop.offset(MARK_RANGE, 2, MARK_RANGE))
                .filter(pos -> level.getBlockState(pos).is(Blocks.POPPY))
                .map(BlockPos::immutable)
                .sorted(Comparator.comparingDouble(pos -> pos.distSqr(workshop)))
                .toList();
        if (!poppies.isEmpty()) {
            for (BlockPos poppy : poppies) {
                GolemSite marked = Direction.Plane.HORIZONTAL.stream()
                        .map(facing -> new GolemSite(poppy, facing, true))
                        .filter(candidate -> fits(level, candidate))
                        .min(Comparator.comparingDouble(candidate -> candidate.stand().distSqr(workshop)))
                        .orElse(null);
                if (marked != null) return marked;
            }
            return null;
        }
        for (int reach = 2; reach <= 4; reach++) {
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                GolemSite candidate = new GolemSite(workshop.relative(facing, reach), facing, false);
                if (fits(level, candidate)) return candidate;
            }
        }
        return null;
    }

    private static boolean open(ServerLevel level, GolemSite at, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || (at.marked() && pos.equals(at.base()) && state.is(Blocks.POPPY));
    }

    /** The T's cells are empty or already hold its iron, the cells around it vanilla's pattern needs empty are, and there's ground under it and its builder. */
    private static boolean fits(ServerLevel level, GolemSite at) {
        if (!solidBelow(level, at.base()) || !solidBelow(level, at.stand())) return false;
        if (!level.getBlockState(at.stand()).isAir() || !level.getBlockState(at.stand().above()).isAir()) return false;
        List<BlockPos> steps = at.steps();
        for (int i = 0; i < steps.size(); i++) {
            BlockPos pos = steps.get(i);
            if (!open(level, at, pos) && (i == steps.size() - 1 || !level.getBlockState(pos).is(Blocks.IRON_BLOCK))) return false;
        }
        return at.clearings().stream().allMatch(pos -> level.getBlockState(pos).isAir());
    }

    private static boolean solidBelow(ServerLevel level, BlockPos feet) {
        return level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP);
    }

    private static boolean mendable(IronGolem golem) {
        return GolemsmithSkill.worthRepairing(golem.getMaxHealth() - golem.getHealth(), golem.getMaxHealth());
    }

    private static int golems(ServerLevel level, BlockPos workshop) {
        return level.getEntitiesOfClass(IronGolem.class, new AABB(workshop).inflate(VILLAGE_RANGE)).size();
    }

    private static void mend(ServerLevel level, Villager body, IronGolem golem) {
        if (Economy.take(level, body, Items.IRON_INGOT, 1) == 0) return;
        golem.heal(25.0F);
        golem.playSound(SoundEvents.IRON_GOLEM_REPAIR, 1.0F, 1.0F + (golem.getRandom().nextFloat() - golem.getRandom().nextFloat()) * 0.2F);
    }

    private void walk(ServerLevel level, Villager body) {
        if (patient != null) {
            body.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(patient.blockPosition(), SPEED, 2));
            body.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(patient, true));
        } else if (site != null) {
            body.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(site.stand(), SPEED, 0));
            body.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(site.base().above()));
        }
    }

    private static Optional<BlockPos> workshop(ServerLevel level, Villager body) {
        return body.getBrain().getMemory(MemoryModuleType.JOB_SITE).filter(pos -> pos.dimension() == level.dimension()).map(GlobalPos::pos);
    }
}
