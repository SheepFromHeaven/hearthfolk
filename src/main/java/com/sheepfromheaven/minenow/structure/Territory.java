package com.sheepfromheaven.minenow.structure;

import net.minecraft.core.BlockPos;

/**
 * A structure's placement area: an axis-aligned box centred on the structure marker, within which work-area
 * marker blocks (mine claims, fields, ...) may be placed. Grows with level via
 * {@link com.sheepfromheaven.minenow.effect.TerritoryEffect}. Each axis is odd-sized so the marker block is
 * always the exact centre - {@code half*} is the number of blocks reachable to each side, so the box spans
 * {@code 2*half + 1} blocks on that axis. A territory may extend outside the dominion bounds; only the
 * structure itself must sit inside the dominion.
 */
public record Territory(BlockPos center, int halfX, int halfY, int halfZ) {
    public boolean contains(BlockPos pos) {
        return Math.abs(pos.getX() - center.getX()) <= halfX
                && Math.abs(pos.getY() - center.getY()) <= halfY
                && Math.abs(pos.getZ() - center.getZ()) <= halfZ;
    }
}
