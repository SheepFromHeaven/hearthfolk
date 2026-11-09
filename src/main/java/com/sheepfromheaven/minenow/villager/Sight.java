package com.sheepfromheaven.minenow.villager;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.neoforged.neoforge.registries.datamaps.builtin.NeoForgeDataMaps;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.WeakHashMap;

/**
 * What a villager notices of the hostiles it fears (vanilla's list, with its sensor's distance for each: 8 blocks for a
 * husk, 15 for a pillager), now by height and light: that distance plus {@link #PER_BLOCK_OF_HEIGHT} per block it stands
 * above the mob (up to {@link #MAX_HEIGHT}), scaled by how well lit the mob is, so one in the dark is only seen from
 * close by and one in the light of day or of torches from far. It must be in line of sight. {@link Eyes} writes the result
 * into the villager's brain in place of vanilla's sensor. {@link #bowRange} is how far an archer shoots.
 */
final class Sight {
    static final double PER_BLOCK_OF_HEIGHT = 1.5;
    static final double MAX_HEIGHT = 8.0;
    static final double BOW_RANGE = 15.0;
    private static final double DARKNESS = 0.4;
    private static final double SCAN = 30.0;
    private static final int RECHECK = 10;
    private static final Map<EntityType<?>, Float> VANILLA = Map.ofEntries(
            Map.entry(EntityTypes.DROWNED, 8.0F), Map.entry(EntityTypes.EVOKER, 12.0F), Map.entry(EntityTypes.HUSK, 8.0F),
            Map.entry(EntityTypes.ILLUSIONER, 12.0F), Map.entry(EntityTypes.PILLAGER, 15.0F), Map.entry(EntityTypes.RAVAGER, 12.0F),
            Map.entry(EntityTypes.VEX, 8.0F), Map.entry(EntityTypes.VINDICATOR, 10.0F), Map.entry(EntityTypes.ZOGLIN, 10.0F),
            Map.entry(EntityTypes.ZOMBIE, 8.0F), Map.entry(EntityTypes.ZOMBIE_VILLAGER, 8.0F));
    private static final Map<Villager, Spotted> SEEN = new WeakHashMap<>();

    private record Spotted(long at, @Nullable LivingEntity enemy) {}

    private Sight() {}

    /** How far a mob is seen from {@code rise} blocks above it at light level {@code light} (0 to 15), given the sensor's distance. */
    static double reach(double base, double rise, int light) {
        return (base + PER_BLOCK_OF_HEIGHT * Math.clamp(rise, 0.0, MAX_HEIGHT)) * (DARKNESS + (1.0 - DARKNESS) * Math.clamp(light, 0, 15) / 15.0);
    }

    /** A bow carries further from above. */
    static double bowRange(Villager body, LivingEntity enemy) {
        return BOW_RANGE + Math.clamp(body.getY() - enemy.getY(), 0.0, MAX_HEIGHT);
    }

    /** Vanilla's list of what a villager fears and how far its sensor sees each, or none for a mob it ignores. */
    private static OptionalDouble base(LivingEntity enemy) {
        var distance = enemy.getData(NeoForgeDataMaps.ACCEPTABLE_VILLAGER_DISTANCES);
        if (distance != null) return OptionalDouble.of(distance.distance());
        Float vanilla = VANILLA.get(enemy.getType());
        return vanilla == null ? OptionalDouble.empty() : OptionalDouble.of(vanilla);
    }

    /** The nearest hostile this villager sees from where it stands, looked up at most every few ticks. */
    static @Nullable LivingEntity spotted(ServerLevel level, Villager body) {
        long now = level.getGameTime();
        Spotted cached = SEEN.get(body);
        if (cached != null && now - cached.at < RECHECK && (cached.enemy == null || cached.enemy.isAlive())) return cached.enemy;
        LivingEntity nearest = level.getEntitiesOfClass(LivingEntity.class, body.getBoundingBox().inflate(SCAN, 16.0, SCAN),
                        enemy -> enemy.isAlive() && sees(level, body, enemy))
                .stream().min(Comparator.comparingDouble(body::distanceToSqr)).orElse(null);
        SEEN.put(body, new Spotted(now, nearest));
        return nearest;
    }

    private static boolean sees(ServerLevel level, Villager body, LivingEntity enemy) {
        OptionalDouble base = base(enemy);
        if (base.isEmpty()) return false;
        int light = level.getMaxLocalRawBrightness(enemy.blockPosition());
        return body.distanceTo(enemy) <= reach(base.getAsDouble(), body.getY() - enemy.getY(), light) && body.hasLineOfSight(enemy);
    }
}
