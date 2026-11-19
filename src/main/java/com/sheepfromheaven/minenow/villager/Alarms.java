package com.sheepfromheaven.minenow.villager;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.level.block.entity.BellBlockEntity;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * A village's alarm: one of its bells ringing alerts every villager of the village however far from the bell (vanilla
 * only alerts those that hear it), for {@link Muster#ALARM_TICKS}, and a raid whose centre lies in the village for as
 * long as the raid goes on. The alarm keeps where the threat is, for {@link Muster} to post its defenders: the raid's
 * centre, else the hostile that last hurt one of the villagers, else the one nearest the bell that a villager or the
 * player at the bell (whoever rang it) saw when it rang. Never saved.
 */
public final class Alarms {
    static final int POLL = 10;
    /** A player this close to a ringing bell rang it, and what it sees is what the alarm is about. */
    private static final double RINGER = 8.0;
    private static final double LOOKOUT = 48.0;

    private static final Map<ServerLevel, List<Alarm>> ALARMS = new WeakHashMap<>();

    private Alarms() {}

    static boolean on() {
        return com.sheepfromheaven.minenow.MineNowConfig.enabled(com.sheepfromheaven.minenow.MineNowConfig.Feature.VILLAGERS_DEFEND_THE_VILLAGE);
    }

    static final class Alarm {
        private final BlockPos origin;
        private long until;
        private boolean raid;
        private @Nullable BlockPos threat;

        private Alarm(BlockPos origin) {
            this.origin = origin;
        }

        Optional<BlockPos> threat() {
            return Optional.ofNullable(threat);
        }

        long until() {
            return until;
        }

        boolean raid() {
            return raid;
        }
    }

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.getGameTime() % POLL != 0) return;
        if (!on()) {
            ALARMS.remove(level);
            return;
        }
        long now = level.getGameTime();
        List<Alarm> alarms = ALARMS.computeIfAbsent(level, l -> new ArrayList<>());
        alarms.removeIf(alarm -> now > alarm.until || now < alarm.until - 2L * Muster.ALARM_TICKS);
        for (Villages.Village village : Villages.all(level)) {
            for (BlockPos bell : village.bells()) {
                if (!level.isLoaded(bell)) continue;
                if (level.getBlockEntity(bell) instanceof BellBlockEntity ringing && ringing.shaking && ringing.ticks <= POLL) ring(level, village, bell);
                Raid raid = level.getRaidAt(bell);
                if (raid != null && raid.isActive() && !raid.isOver() && village.contains(raid.getCenter())) {
                    Alarm alarm = raise(level, village, raid.getCenter());
                    alarm.raid = true;
                    alarm.threat = raid.getCenter();
                    alarm.until = Math.max(alarm.until, now + 3L * POLL);
                }
            }
        }
    }

    /** One of the village's bells rang: everyone in it heard, as vanilla's bell only tells those within earshot. */
    static void ring(ServerLevel level, Villages.Village village, BlockPos bell) {
        long now = level.getGameTime();
        Alarm alarm = raise(level, village, bell);
        alarm.until = Math.max(alarm.until, now + Muster.ALARM_TICKS);
        if (alarm.threat == null) alarm.threat = seen(level, village, bell).map(LivingEntity::blockPosition).orElse(null);
        for (Villager member : village.villagers(level)) member.getBrain().setMemory(MemoryModuleType.HEARD_BELL_TIME, now);
    }

    /** A villager that heard a bell of no village's, or of another village's, raises its own village's alarm. */
    static void heard(ServerLevel level, Villager body) {
        Optional<Villages.Village> village = Villages.of(level, body);
        if (village.isEmpty() || active(level, village.get()).isPresent()) return;
        ring(level, village.get(), body.blockPosition());
    }

    /** The alarm of the villager's village, if one is on. */
    static Optional<Alarm> of(ServerLevel level, Villager body) {
        return Villages.of(level, body).flatMap(village -> active(level, village));
    }

    /** A hostile hurting a villager in an alarm is where the threat is now. */
    public static void onDamage(LivingDamageEvent.Post event) {
        if (!on() || !(event.getEntity() instanceof Villager villager) || !(villager.level() instanceof ServerLevel level)) return;
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker) || !hostile(attacker)) return;
        of(level, villager).filter(alarm -> !alarm.raid).ifPresent(alarm -> alarm.threat = attacker.blockPosition());
    }

    private static Optional<Alarm> active(ServerLevel level, Villages.Village village) {
        long now = level.getGameTime();
        return ALARMS.getOrDefault(level, List.of()).stream()
                .filter(alarm -> now <= alarm.until && (village.bells().contains(alarm.origin) || village.contains(alarm.origin)))
                .findFirst();
    }

    private static Alarm raise(ServerLevel level, Villages.Village village, BlockPos origin) {
        return active(level, village).orElseGet(() -> {
            Alarm alarm = new Alarm(origin);
            alarm.until = level.getGameTime();
            ALARMS.computeIfAbsent(level, l -> new ArrayList<>()).add(alarm);
            return alarm;
        });
    }

    /** The hostile nearest the bell that one of the village's villagers sees, or the player who rang it. */
    private static Optional<LivingEntity> seen(ServerLevel level, Villages.Village village, BlockPos bell) {
        List<LivingEntity> spotted = new ArrayList<>();
        for (Villager member : village.villagers(level)) {
            LivingEntity enemy = Sight.spotted(level, member);
            if (enemy != null) spotted.add(enemy);
        }
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || !player.position().closerThan(net.minecraft.world.phys.Vec3.atBottomCenterOf(bell), RINGER)) continue;
            level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(LOOKOUT),
                            enemy -> enemy.isAlive() && hostile(enemy) && player.hasLineOfSight(enemy))
                    .forEach(spotted::add);
        }
        return spotted.stream().min(Comparator.comparingDouble(enemy -> enemy.distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(bell))));
    }

    private static boolean hostile(LivingEntity entity) {
        return entity instanceof Enemy || Sight.feared(entity);
    }
}
