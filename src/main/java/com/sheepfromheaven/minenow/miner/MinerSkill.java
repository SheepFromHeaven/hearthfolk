package com.sheepfromheaven.minenow.miner;

/**
 * What a miner gets better at with each level: how deep below its furnace it may dig, and how much extra ore it gets
 * out of each ore block (up to {@link #extraOre} more, like Fortune). Better pickaxes, unlocked by level in the trade
 * file, dig faster and harvest harder ores. It also smelts in more furnaces ({@link #extraFurnaces}).
 */
final class MinerSkill {
    /** A master has no depth limit: bedrock, or a redstone signal into the cart, stops it. */
    private static final int[] DEPTH = {8, 16, 24, 40, 1 << 20};
    private static final int[] EXTRA_ORE = {0, 0, 1, 1, 2};
    private static final int[] EXTRA_FURNACES = {0, 1, 2, 3, 4};
    private static final int[] TUNNEL_LENGTH = {8, 12, 16, 20, 24};

    private MinerSkill() {}

    static int depth(int villagerLevel) {
        return DEPTH[index(villagerLevel)];
    }

    static int extraOre(int villagerLevel) {
        return EXTRA_ORE[index(villagerLevel)];
    }

    /** How many furnaces or blast furnaces beside its furnace minecart it smelts in as well. */
    static int extraFurnaces(int villagerLevel) {
        return EXTRA_FURNACES[index(villagerLevel)];
    }

    /** How far each tunnel runs out from a platform. */
    static int tunnelLength(int villagerLevel) {
        return TUNNEL_LENGTH[index(villagerLevel)];
    }

    private static int index(int villagerLevel) {
        return Math.clamp(villagerLevel, 1, 5) - 1;
    }
}
