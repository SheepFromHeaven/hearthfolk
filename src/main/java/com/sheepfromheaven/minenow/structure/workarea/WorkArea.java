package com.sheepfromheaven.minenow.structure.workarea;

import net.minecraft.core.BlockPos;

/**
 * A region a worker may operate in, decoupled from where it came from (the structure's static area or a
 * placed claim). Directional jobs (the miner tunnels outward; the lumberjack/forester forage a radius) use
 * {@link #center} and {@link #contains}. Sources produce these; jobs consume them, so neither has to know
 * the other's shape.
 */
public sealed interface WorkArea permits CylinderArea {
    /** A representative point to walk to / tunnel out from. */
    BlockPos center();

    /** Whether {@code pos} is inside this area (used for boundary checks like tunnel exhaustion). */
    boolean contains(BlockPos pos);
}
