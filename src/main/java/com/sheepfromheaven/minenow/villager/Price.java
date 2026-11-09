package com.sheepfromheaven.minenow.villager;

/**
 * {@code count} of an item for {@code emeralds} emeralds. Trade files give one decimal price per item (0.05 is 20 for an
 * emerald, 3 is one for three emeralds); trades need whole items and emeralds, so it becomes the smallest such deal.
 */
public record Price(int count, int emeralds) {
    private static final int MAX_COUNT = 64;
    private static final double TOLERANCE = 0.02;

    public static Price of(double perItem) {
        for (int count = 1; count <= MAX_COUNT; count++) {
            double total = count * perItem;
            long emeralds = Math.round(total);
            if (emeralds >= 1 && Math.abs(total - emeralds) <= TOLERANCE * emeralds) return new Price(count, (int) emeralds);
        }
        return new Price(1, (int) Math.max(1, Math.ceil(perItem)));
    }

    public double perItem() {
        return (double) emeralds / count;
    }

    /** A crafted item costs what its materials are worth plus the crafter's labor, rounded up to whole emeralds. */
    public static int ofCraft(double materials, double labor) {
        return (int) Math.max(1, Math.ceil(materials + labor - 1e-9));
    }
}
