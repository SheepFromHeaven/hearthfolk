package com.sheepfromheaven.minenow.miner;

/** How long breaking a block takes, the way vanilla times a player: the tool's speed against the block's hardness. */
final class Mining {
    private Mining() {}

    static int ticks(float hardness, float toolSpeed, boolean correctTool) {
        if (hardness <= 0.0F) return 1;
        float progressPerTick = toolSpeed / hardness / (correctTool ? 30.0F : 100.0F);
        return Math.max(1, (int) Math.ceil(1.0F / progressPerTick));
    }
}
