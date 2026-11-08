package com.sheepfromheaven.minenow.miner;

/**
 * What a miner gets better at with each level: how deep below its furnace it may dig, and how much extra ore it gets
 * out of each ore block (up to {@link #extraOre} more, like Fortune). Better pickaxes, unlocked by level in the trade
 * file, dig faster and harvest harder ores.
 */
final class MinerSkill {
    private static final int[] DEPTH = {8, 16, 24, 40, 64};
    private static final int[] EXTRA_ORE = {0, 0, 1, 1, 2};

    private MinerSkill() {}

    static int depth(int villagerLevel) {
        return DEPTH[index(villagerLevel)];
    }

    static int extraOre(int villagerLevel) {
        return EXTRA_ORE[index(villagerLevel)];
    }

    private static int index(int villagerLevel) {
        return Math.clamp(villagerLevel, 1, 5) - 1;
    }
}
