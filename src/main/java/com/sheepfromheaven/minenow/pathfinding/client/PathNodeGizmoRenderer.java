package com.sheepfromheaven.minenow.pathfinding.client;

import com.sheepfromheaven.minenow.pathfinding.DominionPathNode;
import com.sheepfromheaven.minenow.core.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.Gizmo;
import net.minecraft.gizmos.GizmoPrimitives;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;

/**
 * Draws every path node the server has revealed to this client (see {@link PathNodeTracker}) as a
 * small translucent arrow (a cone tip over a narrower cylinder shaft) pointing down at the node,
 * gently hovering up and down - but only while the player is actually holding the wand; nodes are
 * otherwise invisible in the world, per the design.
 *
 * <p>Built from custom {@link Gizmo} implementations ({@link ConeGizmo}, {@link CylinderGizmo})
 * rather than a real ghost block model: three separate attempts at rendering an actual sign model
 * via {@code BlockRenderDispatcher.renderSingleBlock} all rendered nothing in practice, and this
 * project has no way to visually verify client rendering without a live client to check against.
 * Gizmos are the only approach confirmed to actually show up in-game - building real 3D shapes from
 * {@link GizmoPrimitives#addTriangleFan} / {@link GizmoPrimitives#addQuad} stays on that confirmed
 * path while getting a proper arrow silhouette instead of flat panels or boxes.
 */
public final class PathNodeGizmoRenderer {
    private static final float ALPHA = 0.5F;
    // LOW = green, MEDIUM = yellow, HIGH = red
    private static final GizmoStyle STYLE_LOW    = GizmoStyle.fill(ARGB.colorFromFloat(ALPHA, 0.20F, 0.85F, 0.20F));
    private static final GizmoStyle STYLE_MEDIUM = GizmoStyle.fill(ARGB.colorFromFloat(ALPHA, 0.95F, 0.85F, 0.10F));
    private static final GizmoStyle STYLE_HIGH   = GizmoStyle.fill(ARGB.colorFromFloat(ALPHA, 0.90F, 0.15F, 0.10F));
    private static final int SEGMENTS = 12;

    private static final double CONE_RADIUS = 0.22;
    private static final double CONE_HEIGHT = 0.35;
    private static final double SHAFT_RADIUS = 0.08;
    private static final double SHAFT_HEIGHT = 0.4;

    private static final double HOVER_BASE_HEIGHT = 0.5;
    private static final double HOVER_AMPLITUDE = 0.12;
    /** Ticks for one full up-down cycle; a sine wave already eases in/out at the extremes. */
    private static final double HOVER_PERIOD_TICKS = 60.0;

    private PathNodeGizmoRenderer() {}

    public static void onClientTick() {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !isHoldingWand(mc)) {
            return;
        }
        long gameTime = mc.level.getGameTime();
        for (var entry : PathNodeTracker.get().entrySet()) {
            emitArrow(entry.getKey(), entry.getValue(), gameTime);
        }
    }

    private static GizmoStyle styleFor(DominionPathNode.Impact impact) {
        return switch (impact) {
            case LOW    -> STYLE_LOW;
            case MEDIUM -> STYLE_MEDIUM;
            case HIGH   -> STYLE_HIGH;
        };
    }

    private static void emitArrow(BlockPos pos, DominionPathNode.Impact impact, long gameTime) {
        double cx = pos.getX() + 0.5;
        double cz = pos.getZ() + 0.5;

        // Stagger the phase per node (from its position) so a field of nodes doesn't bob in lockstep.
        double phase = (pos.getX() * 928371 + pos.getZ() * 121933) % 1000 / 1000.0 * Math.PI * 2;
        double bob = HOVER_AMPLITUDE * Math.sin(gameTime / HOVER_PERIOD_TICKS * Math.PI * 2 + phase);

        double tipY = pos.getY() + HOVER_BASE_HEIGHT + bob;
        double coneBaseY = tipY + CONE_HEIGHT;
        double shaftTopY = coneBaseY + SHAFT_HEIGHT;

        var style = styleFor(impact);
        Gizmos.addGizmo(new ConeGizmo(
                new Vec3(cx, tipY, cz), new Vec3(cx, coneBaseY, cz), CONE_RADIUS, SEGMENTS, style));
        Gizmos.addGizmo(new CylinderGizmo(
                new Vec3(cx, coneBaseY, cz), new Vec3(cx, shaftTopY, cz), SHAFT_RADIUS, SEGMENTS, style));
    }

    private static boolean isHoldingWand(Minecraft mc) {
        return mc.player.getMainHandItem().is(ModItems.PATH_NODE_WAND.get())
                || mc.player.getOffhandItem().is(ModItems.PATH_NODE_WAND.get());
    }

    /** A ring of {@code segments + 1} points (closed loop) around {@code center} in the XZ plane. */
    private static Vec3[] ring(Vec3 center, double radius, int segments) {
        var points = new Vec3[segments + 1];
        for (int i = 0; i < segments; i++) {
            double angle = i * (Math.PI * 2 / segments);
            points[i] = center.add(radius * Math.cos(angle), 0.0, radius * Math.sin(angle));
        }
        points[segments] = points[0];
        return points;
    }

    /** A solid cone: {@code apex} is the pointed tip, {@code baseCenter} the flat circular end. */
    private record ConeGizmo(Vec3 apex, Vec3 baseCenter, double radius, int segments, GizmoStyle style) implements Gizmo {
        @Override
        public void emit(GizmoPrimitives primitives, float alphaMultiplier) {
            if (!style.hasFill()) return;
            int color = style.multipliedFill(alphaMultiplier);
            var rim = ring(baseCenter, radius, segments);

            primitives.addTriangleFan(rim, color);

            var lateral = new Vec3[rim.length + 1];
            lateral[0] = apex;
            System.arraycopy(rim, 0, lateral, 1, rim.length);
            primitives.addTriangleFan(lateral, color);
        }
    }

    /** A solid cylinder between two same-radius circular ends. */
    private record CylinderGizmo(Vec3 bottomCenter, Vec3 topCenter, double radius, int segments, GizmoStyle style) implements Gizmo {
        @Override
        public void emit(GizmoPrimitives primitives, float alphaMultiplier) {
            if (!style.hasFill()) return;
            int color = style.multipliedFill(alphaMultiplier);
            var bottomRim = ring(bottomCenter, radius, segments);
            var topRim = ring(topCenter, radius, segments);

            primitives.addTriangleFan(bottomRim, color);
            primitives.addTriangleFan(topRim, color);

            for (int i = 0; i < segments; i++) {
                primitives.addQuad(bottomRim[i], bottomRim[i + 1], topRim[i + 1], topRim[i], color);
                primitives.addQuad(topRim[i], topRim[i + 1], bottomRim[i + 1], bottomRim[i], color);
            }
        }
    }
}
