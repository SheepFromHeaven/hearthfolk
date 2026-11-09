package com.sheepfromheaven.minenow.mason;

/**
 * Which material a road block becomes, from a hash of its position, so a road looks laid by hand (irregular, the
 * same each time it's checked) rather than striped: cobblestone with gravel among it, and on a stone road mostly stone
 * bricks and slabs with a fifth of the cobblestone and gravel left in.
 */
final class RoadPattern {
    enum Material { COBBLESTONE, GRAVEL, STONE_BRICKS, STONE_BRICK_SLAB }

    private RoadPattern() {}

    static Material material(long seed, boolean stone) {
        int roll = (int) Math.floorMod(mix(seed), 100L);
        int second = (int) Math.floorMod(mix(seed ^ 0x5DEECE66DL), 100L);
        Material rough = second < 60 ? Material.COBBLESTONE : Material.GRAVEL;
        if (!stone || roll < 20) return rough;
        return roll < 45 ? Material.STONE_BRICK_SLAB : Material.STONE_BRICKS;
    }

    private static long mix(long value) {
        long z = value + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
