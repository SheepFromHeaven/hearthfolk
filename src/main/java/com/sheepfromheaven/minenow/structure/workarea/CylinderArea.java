package com.sheepfromheaven.minenow.structure.workarea;

import net.minecraft.core.BlockPos;

/**
 * A vertical cylinder: an XZ disc of {@code radius} around {@code center}. {@link #contains} tests the XZ
 * disc <em>only</em> - the column is unbounded in Y, so a miner tunnelling down out of the surface band
 * still counts as inside the claim. {@code height} is retained for callers that scan a vertical band around
 * the centre (lumberjack/forester), which read it directly off the record.
 */
public record CylinderArea(BlockPos center, int radius, int height) implements WorkArea {
    @Override
    public boolean contains(BlockPos pos) {
        long dx = pos.getX() - center.getX();
        long dz = pos.getZ() - center.getZ();
        return dx * dx + dz * dz <= (long) radius * radius;
    }
}
