package com.sheepfromheaven.minenow.villager;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Why a villager is not having a child, for the villager report. Vanilla breeding needs two adult villagers that are
 * willing (12 breeding food points in their own pockets, not their chests) and near each other, off the cooldown a
 * birth leaves, and a free bed within 48 blocks or anywhere in its village (the baby takes it). The partner may be anyone of the villager's village (they meet at the
 * bell), or within {@link #PARTNER_RADIUS} blocks for a villager that belongs to none.
 */
public final class BreedingReport {
    private static final int PARTNER_RADIUS = 32;

    private BreedingReport() {}

    static boolean willing(Villager villager) {
        return !villager.isBaby() && villager.getAge() == 0 && Wealth.pocketFoodPoints(villager) >= Villager.BREEDING_FOOD_THRESHOLD;
    }

    public static List<String> lines(ServerLevel level, Villager villager) {
        if (villager.isBaby()) return List.of("Breeding: a child, it grows up in " + (int) Math.ceil(-villager.getAge() / 1200.0) + " min");
        List<String> blockers = new ArrayList<>();
        int pockets = Wealth.pocketFoodPoints(villager);
        if (pockets < Villager.BREEDING_FOOD_THRESHOLD) {
            int chests = Wealth.chestFoodPoints(level, villager);
            int emeralds = Economy.emeralds(level, villager);
            int needed = Wealth.required(level, villager);
            blockers.add("too little food in its pockets: " + pockets + " of " + Villager.BREEDING_FOOD_THRESHOLD + " breeding food points (bread 4, carrot, potato, beetroot 1)"
                    + (chests > 0 ? ", " + chests + " more in its chests do not count" : "")
                    + (emeralds >= needed ? ", it has the emeralds to buy some" : ", and with " + emeralds + " of the " + needed + " emeralds it needs to buy family food it can only be given some"));
        }
        if (villager.getAge() > 0) blockers.add("it had a child recently, ready again in " + (int) Math.ceil(villager.getAge() / 1200.0) + " min");
        if (!VillageBeds.anyFree(level, villager)) {
            blockers.add("no free bed within " + VillageBeds.NEAR + " blocks or in its village");
        }
        Optional<Villages.Village> village = Villages.of(level, villager);
        List<Villager> candidates = village.isPresent() ? village.get().villagers(level)
                : level.getEntitiesOfClass(Villager.class, new AABB(villager.blockPosition()).inflate(PARTNER_RADIUS), Villager::isAlive);
        if (candidates.stream().noneMatch(other -> other != villager && willing(other))) {
            blockers.add(village.isPresent() ? "no other willing villager in its village" : "no other willing villager within " + PARTNER_RADIUS + " blocks (it belongs to no village)");
        }
        return List.of(blockers.isEmpty()
                ? "Breeding: nothing stops it, it is willing with a free bed and a willing partner in its village; they pair up when they meet, at the bell in the afternoon"
                : "Breeding: stopped by " + String.join("; ", blockers));
    }
}
