package com.sheepfromheaven.minenow.jobs.lumberjack.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.RectGizmo;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/**
 * Draws the Tree Scanner Wand's current highlight (see {@link TreeScanOutlineTracker}) as a solid
 * face outline, the same technique {@code StructureMarkerRenderer} uses for the work-area/range
 * boxes: only the faces that border a non-highlighted neighbor are drawn, so a whole recognized
 * tree renders as a hollow outline rather than a solid block of overlapping quads.
 */
public final class TreeScanGizmoRenderer {
    private static final GizmoStyle LOG_STYLE = GizmoStyle.fill(ARGB.colorFromFloat(0.55F, 0.3F, 1.0F, 0.3F));
    private static final GizmoStyle LEAF_STYLE = GizmoStyle.fill(ARGB.colorFromFloat(0.55F, 1.0F, 1.0F, 0.2F));
    private static final GizmoStyle BLOCKED_STYLE = GizmoStyle.fill(ARGB.colorFromFloat(0.55F, 1.0F, 0.2F, 0.2F));

    private TreeScanGizmoRenderer() {}

    public static void onClientTick() {
        if (Minecraft.getInstance().level == null || TreeScanOutlineTracker.isEmpty()) {
            return;
        }
        emitOutline(TreeScanOutlineTracker.getLogs(), LOG_STYLE);
        emitOutline(TreeScanOutlineTracker.getLeaves(), LEAF_STYLE);
        emitOutline(TreeScanOutlineTracker.getBlocked(), BLOCKED_STYLE);
    }

    private static void emitOutline(Set<BlockPos> blocks, GizmoStyle style) {
        for (var pos : blocks) {
            for (var dir : Direction.values()) {
                if (blocks.contains(pos.relative(dir))) {
                    continue;
                }
                Vec3 min = new Vec3(pos.getX(), pos.getY(), pos.getZ());
                Vec3 max = min.add(1, 1, 1);
                var quad = RectGizmo.fromCuboidFace(min, max, dir, style);
                Gizmos.rect(quad.a(), quad.b(), quad.c(), quad.d(), style);
                Gizmos.rect(quad.d(), quad.c(), quad.b(), quad.a(), style);
            }
        }
    }
}
