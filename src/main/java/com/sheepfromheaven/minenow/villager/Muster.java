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
    private static final double KEEP_PERCH = 64.0;
    private static final double SPREAD = 32.0;
    private static final double GATHER_OUT = 4.0;
    /** A post it gets no closer to for this long is one it can't walk to. */
    private static final int STUCK_TICKS = 200;

    /** Where each archer is going to stand, or stands; weak, so a villager that's gone takes its claim with it. */
    private static final Map<Villager, Perch> PERCHES = new WeakHashMap<>();
    /** Posts it set out for but found it can't walk to, this alarm. */
    private static final Map<Villager, java.util.Set<BlockPos>> REFUSED = new WeakHashMap<>();

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
    private double closest;
    private long progressAt;

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
        if (now - heard <= 2L * Alarms.POLL) Alarms.heard(level, body);
        if (now - heard < ALARM_TICKS) until = Math.max(until, heard + ALARM_TICKS);
        Optional<Alarms.Alarm> alarm = Alarms.of(level, body);
        if (alarm.isPresent()) until = Math.max(until, alarm.get().until());
        if (now >= until && !raidOn(level, body)) return false;
        if (now < nextSearch || !body.onGround()) return false;
        nextSearch = now + SEARCH_INTERVAL;
        post = choosePost(level, body, alarm.flatMap(Alarms.Alarm::threat));
        if (post == null) nextSearch = now + NO_POST_RETRY;
        pendingUntil = until;
        return post != null;
    }

    @Override
    protected void start(ServerLevel level, Villager body, long timestamp) {
        alarmUntil = pendingUntil;
        closest = Double.MAX_VALUE;
        progressAt = timestamp;
        body.getBrain().eraseMemory(MemoryModuleType.HEARD_BELL_TIME);
        raid = raidOn(level, body);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager body, long timestamp) {
        hearBell(body, timestamp);
        if (timestamp % 20 == 0) {
            raid = raidOn(level, body);
            Alarms.of(level, body).ifPresent(alarm -> alarmUntil = Math.max(alarmUntil, alarm.until()));
        }
        return post != null && fit(body) && (timestamp < alarmUntil || raid);
    }

    @Override
    protected void tick(ServerLevel level, Villager body, long timestamp) {
        if (post == null || PanicOrFight.isFighting(body)) return;
        if (!post.perch() && Gear.shoots(body) && timestamp >= nextSearch && body.onGround()) {
            nextSearch = timestamp + NO_POST_RETRY;
            Post perch = choosePost(level, body, Alarms.of(level, body).flatMap(Alarms.Alarm::threat));
            if (perch != null && perch.perch()) {
                post = perch;
                closest = Double.MAX_VALUE;
                progressAt = timestamp;
            }
        }
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
        } else {
            double distance = body.position().distanceTo(spot);
            if (distance < closest - 0.5) {
                closest = distance;
                progressAt = timestamp;
            } else if (post.perch() && timestamp - progressAt > STUCK_TICKS && !reached(body)) {
                REFUSED.computeIfAbsent(body, b -> new java.util.HashSet<>()).add(post.pos());
                PERCHES.remove(body);
                post = null;
                return;
            }
            if (!Walking.towards(body, post.pos())) brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(post.pos(), SPEED, post.perch() ? 0 : GATHER_RADIUS));
        }
    }

    @Override
    protected void stop(ServerLevel level, Villager body, long timestamp) {
        boolean over = !fit(body) || (timestamp >= alarmUntil && !raid);
        if (!over) return;
        PERCHES.remove(body);
        REFUSED.remove(body);
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

    private static boolean reached(Villager body) {
        Perch perch = PERCHES.get(body);
        return perch != null && perch.arrived;
    }

    /** A raid where it stands, or one inside its village however far off. */
    private static boolean raidOn(ServerLevel level, Villager body) {
        Raid raid = level.getRaidAt(body.blockPosition());
        return raid != null && raid.isActive() && !raid.isVictory() && !raid.isLoss() || Alarms.of(level, body).filter(Alarms.Alarm::raid).isPresent();
    }

    /**
     * In a village with the threat known, swordsmen gather on its side ({@link #gatherPoint}) and an archer takes the
     * free marked post nearest it, else high ground on the village's edge facing it; with the threat unknown archers
     * spread over the marked posts and swordsmen gather at the bell. Outside a village, high ground near the bell.
     * A perch it chose is kept while it holds it.
     */
    private static @Nullable Post choosePost(ServerLevel level, Villager body, Optional<BlockPos> threat) {
        Optional<Villages.Village> village = Villages.of(level, body);
        Optional<BlockPos> edge = village.flatMap(v -> threat.map(t -> edge(v, t)));
        Optional<BlockPos> rally = edge.isPresent() ? Optional.of(gatherPoint(level, edge.get(), threat.get())) : rallyPoint(level, body);
        if (Gear.shoots(body)) {
            Perch kept = PERCHES.get(body);
            if (kept != null && body.position().closerThan(Vec3.atBottomCenterOf(kept.pos), KEEP_PERCH)) return new Post(kept.pos, true);
            Optional<BlockPos> spot = village.flatMap(v -> markedPost(level, body, v, threat));
            if (spot.isEmpty()) {
                BlockPos around = edge.orElse(rallyPoint(level, body).orElse(body.blockPosition()));
                java.util.List<BlockPos> taken = new java.util.ArrayList<>(takenPerches(body));
                taken.addAll(REFUSED.getOrDefault(body, java.util.Set.of()));
                spot = HighGround.find(level, body, around, taken);
            }
            if (spot.isPresent()) {
                PERCHES.put(body, new Perch(spot.get()));
                return new Post(spot.get(), true);
            }
        }
        return rally.map(pos -> new Post(pos, false)).orElse(null);
    }

    /**
     * The free post the player marked anywhere in the village nearest the threat, or with the threat unknown the one
     * furthest from those other archers hold (nearest itself among equals), so they spread out. Paths are planned only
     * 16 blocks out, so there's no telling beforehand whether a post far off can be reached: one it gets no closer to
     * for {@link #STUCK_TICKS} is refused for the rest of the alarm.
     */
    private static Optional<BlockPos> markedPost(ServerLevel level, Villager body, Villages.Village village, Optional<BlockPos> threat) {
        java.util.List<BlockPos> taken = takenPerches(body);
        java.util.Set<BlockPos> refused = REFUSED.getOrDefault(body, java.util.Set.of());
        java.util.Comparator<BlockPos> order = threat.isPresent()
                ? java.util.Comparator.comparingDouble(post -> post.distSqr(threat.get()))
                : java.util.Comparator.comparingDouble(post -> -Math.min(SPREAD, taken.stream().mapToDouble(other -> Math.sqrt(other.distSqr(post))).min().orElse(SPREAD))
                        + 0.25 * Math.sqrt(post.distSqr(body.blockPosition())));
        return HighGround.markedPosts(level, village).stream()
                .filter(post -> !refused.contains(post) && taken.stream().noneMatch(other -> other.distSqr(post) < HighGround.SPACING * HighGround.SPACING))
                .sorted(order)
                .findFirst();
    }

    /** The village's bell, bed, job site or chest nearest the threat: its edge on that side. */
    static BlockPos edge(Villages.Village village, BlockPos threat) {
        return village.anchors().stream()
                .min(java.util.Comparator.comparingDouble(anchor -> Math.pow(anchor.getX() - threat.getX(), 2) + Math.pow(anchor.getZ() - threat.getZ(), 2)))
                .orElse(threat);
    }

    /** Ground a few blocks out from the edge towards the threat, where swordsmen gather. */
    static BlockPos gatherPoint(ServerLevel level, BlockPos edge, BlockPos threat) {
        double dx = threat.getX() - edge.getX();
        double dz = threat.getZ() - edge.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        double step = distance < 1.0 ? 0.0 : Math.min(GATHER_OUT, distance / 2.0) / distance;
        int x = edge.getX() + (int) Math.round(dx * step);
        int z = edge.getZ() + (int) Math.round(dz * step);
        return HighGround.ground(level, x, z, edge.getY()).or(() -> HighGround.ground(level, edge.getX(), edge.getZ(), edge.getY())).orElse(edge);
    }

    private static java.util.List<BlockPos> takenPerches(Villager body) {
        return PERCHES.entrySet().stream().filter(entry -> entry.getKey() != body && entry.getKey().isAlive()).map(entry -> entry.getValue().pos).toList();
    }

    /** The bell it claimed, however far, else the nearest one within vanilla's search range. */
    static Optional<BlockPos> rallyPoint(ServerLevel level, Villager body) {
        Optional<BlockPos> claimed = body.getBrain().getMemory(MemoryModuleType.MEETING_POINT)
                .filter(pos -> pos.dimension() == level.dimension()).map(GlobalPos::pos);
        if (claimed.isPresent()) return claimed;
        return level.getPoiManager().findAllClosestFirstWithType(type -> type.is(PoiTypes.MEETING), pos -> true,
                body.blockPosition(), SEARCH_RANGE, PoiManager.Occupancy.ANY).map(Pair::getSecond).findFirst();
    }
}
