package com.sheepfromheaven.minenow.villager;

import com.google.common.collect.ImmutableMap;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * An armed villager ({@link Gear#isArmed}, not badly hurt) answers an alarm, a bell it hears or a raid on its village,
 * by taking a post instead of hiding as vanilla would: it gathers at the meeting point, and an archer takes the best
 * high ground it can walk to near the bell ({@link HighGround}, the player's walls and towers) and holds it, shooting
 * from there ({@link PanicOrFight} keeps it on its perch). It stays in the FIGHT activity so hiding and raid packages
 * don't pull it away, for {@link #ALARM_TICKS} after the last ring or as long as a raid goes on, then goes back to its day.
 * Villagers that aren't armed hide as in vanilla.
 */
public class Muster extends Behavior<Villager> {
    static final int ALARM_TICKS = 1200;
    private static final int SEARCH_RANGE = 48;
    private static final int CHECK_INTERVAL = 10;
    private static final int SEARCH_INTERVAL = 40;
    private static final int NO_POST_RETRY = 200;
    private static final int REFRESH = 1200;
    private static final float SPEED = 0.7F;
    private static final int GATHER_RADIUS = 3;
    private static final double ARRIVED_AT_PERCH = 1.3;

    /** Where each archer is going to stand, or stands; weak, so a villager that's gone takes its claim with it. */
    private static final Map<Villager, Perch> PERCHES = new WeakHashMap<>();

    private record Post(BlockPos pos, boolean perch) {}

    private static final class Perch {
        final BlockPos pos;
        boolean arrived;

        Perch(BlockPos pos) {
            this.pos = pos;
        }
    }

    private long alarmUntil;
    private long pendingUntil;
    private long nextCheck;
    private long nextSearch;
    private boolean raid;
    private @Nullable Post post;

    public Muster() {
        super(ImmutableMap.of(), REFRESH);
    }

    /** Whether this archer stands on the high ground it chose, which it holds instead of walking to a hostile. */
    static boolean holdsPerch(Villager villager) {
        Perch perch = PERCHES.get(villager);
        return perch != null && perch.arrived && villager.position().closerThan(Vec3.atBottomCenterOf(perch.pos), ARRIVED_AT_PERCH + 1.0);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager body) {
        long now = level.getGameTime();
        boolean rang = body.getBrain().hasMemoryValue(MemoryModuleType.HEARD_BELL_TIME);
        if (!rang && now < nextCheck) return false;
        nextCheck = now + CHECK_INTERVAL;
        if (body.isBaby() || body.isSleeping() || !fit(body)) return false;
        long until = alarmUntil;
        long heard = body.getBrain().getMemory(MemoryModuleType.HEARD_BELL_TIME).orElse(Long.MIN_VALUE / 2);
        if (now - heard < ALARM_TICKS) until = Math.max(until, heard + ALARM_TICKS);
        if (now >= until && !raidOn(level, body)) return false;
        if (now < nextSearch || !body.onGround()) return false;
        nextSearch = now + SEARCH_INTERVAL;
        post = choosePost(level, body);
        if (post == null) nextSearch = now + NO_POST_RETRY;
        pendingUntil = until;
        return post != null;
    }

    @Override
    protected void start(ServerLevel level, Villager body, long timestamp) {
        alarmUntil = pendingUntil;
        body.getBrain().eraseMemory(MemoryModuleType.HEARD_BELL_TIME);
        raid = raidOn(level, body);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager body, long timestamp) {
        hearBell(body, timestamp);
        if (timestamp % 20 == 0) raid = raidOn(level, body);
        return post != null && fit(body) && (timestamp < alarmUntil || raid);
    }

    @Override
    protected void tick(ServerLevel level, Villager body, long timestamp) {
        if (post == null || PanicOrFight.isFighting(body)) return;
        var brain = body.getBrain();
        if (!brain.isActive(Activity.FIGHT)) brain.setActiveActivityIfPossible(Activity.FIGHT);
        Vec3 spot = Vec3.atBottomCenterOf(post.pos());
        boolean arrived = post.perch()
                ? body.position().closerThan(spot, ARRIVED_AT_PERCH) && Math.abs(body.getY() - spot.y) < 1.0
                : body.position().closerThan(spot, GATHER_RADIUS + 0.5);
        if (arrived) {
            if (post.perch()) {
                Perch perch = PERCHES.get(body);
                if (perch != null) perch.arrived = true;
            }
            brain.eraseMemory(MemoryModuleType.WALK_TARGET);
            brain.eraseMemory(MemoryModuleType.PATH);
        } else if (!Walking.towards(body, post.pos())) {
            brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(post.pos(), SPEED, post.perch() ? 0 : GATHER_RADIUS));
        }
    }

    @Override
    protected void stop(ServerLevel level, Villager body, long timestamp) {
        boolean over = !fit(body) || (timestamp >= alarmUntil && !raid);
        if (!over) return;
        PERCHES.remove(body);
        post = null;
        if (PanicOrFight.isFighting(body)) return;
        var brain = body.getBrain();
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        if (brain.isActive(Activity.FIGHT)) brain.updateActivityFromSchedule(level.environmentAttributes(), timestamp, body.position());
    }

    private void hearBell(Villager body, long now) {
        body.getBrain().getMemory(MemoryModuleType.HEARD_BELL_TIME).ifPresent(heard -> {
            if (now - heard < ALARM_TICKS) alarmUntil = Math.max(alarmUntil, heard + ALARM_TICKS);
            body.getBrain().eraseMemory(MemoryModuleType.HEARD_BELL_TIME);
        });
    }

    private static boolean fit(Villager body) {
        return Gear.isArmed(body) && body.getHealth() > body.getMaxHealth() / 3.0F;
    }

    private static boolean raidOn(ServerLevel level, Villager body) {
        Raid raid = level.getRaidAt(body.blockPosition());
        return raid != null && raid.isActive() && !raid.isVictory() && !raid.isLoss();
    }

    /** An archer's high ground near the bell (kept while it holds it), else the bell itself; anyone else's the bell. */
    private static @Nullable Post choosePost(ServerLevel level, Villager body) {
        Optional<BlockPos> rally = rallyPoint(level, body);
        if (Gear.shoots(body)) {
            Perch kept = PERCHES.get(body);
            if (kept != null && body.position().closerThan(Vec3.atBottomCenterOf(kept.pos), 24.0)) return new Post(kept.pos, true);
            Optional<BlockPos> spot = HighGround.find(level, body, rally.orElse(body.blockPosition()), takenPerches(body));
            if (spot.isPresent()) {
                PERCHES.put(body, new Perch(spot.get()));
                return new Post(spot.get(), true);
            }
        }
        return rally.map(pos -> new Post(pos, false)).orElse(null);
    }

    private static java.util.List<BlockPos> takenPerches(Villager body) {
        return PERCHES.entrySet().stream().filter(entry -> entry.getKey() != body && entry.getKey().isAlive()).map(entry -> entry.getValue().pos).toList();
    }

    /** The bell it claimed, else the nearest one within vanilla's search range. */
    static Optional<BlockPos> rallyPoint(ServerLevel level, Villager body) {
        Optional<BlockPos> claimed = body.getBrain().getMemory(MemoryModuleType.MEETING_POINT)
                .filter(pos -> pos.dimension() == level.dimension()).map(GlobalPos::pos)
                .filter(pos -> pos.closerThan(body.blockPosition(), SEARCH_RANGE));
        if (claimed.isPresent()) return claimed;
        return level.getPoiManager().findAllClosestFirstWithType(type -> type.is(PoiTypes.MEETING), pos -> true,
                body.blockPosition(), SEARCH_RANGE, PoiManager.Occupancy.ANY).map(Pair::getSecond).findFirst();
    }
}
