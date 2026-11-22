package com.sheepfromheaven.minenow.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sheepfromheaven.minenow.MineNow;
import com.sheepfromheaven.minenow.villager.VillageBorder;
import com.sheepfromheaven.minenow.villager.VillageBorders;
import com.sheepfromheaven.minenow.villager.Villages;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

import java.util.List;

/** Draws the border of the village the server last sent (see {@link VillageBorders}): lines along its edge in a band around the camera. */
@EventBusSubscriber(modid = MineNow.MOD_ID, value = Dist.CLIENT)
public final class VillageBorderRenderer {
    private static final int COLOR = 0xFF55FF55;
    private static final int GROWTH_COLOR = 0xFFFFA500;
    private static final int BAND_BELOW = 3;
    private static final int BAND_HEIGHT = 12;
    private static final int STEP = 3;
    private static final double RANGE = 128;

    private static boolean on;
    private static List<BlockPos> anchors = List.of();
    private static List<VillageBorder.Edge> edges = List.of();
    private static List<BlockPos> claimable = List.of();
    private static List<VillageBorder.Edge> growth = List.of();

    private VillageBorderRenderer() {}

    public static void receive(VillageBorders.Payload payload) {
        if (!payload.on()) {
            clear();
            return;
        }
        on = true;
        if (!payload.anchors().equals(anchors) || !payload.claimable().equals(claimable)) {
            anchors = payload.anchors();
            claimable = payload.claimable();
            edges = VillageBorder.edges(anchors, Villages.REACH);
            growth = VillageBorder.growth(claimable, anchors, Villages.REACH);
        }
    }

    private static void clear() {
        on = false;
        anchors = List.of();
        edges = List.of();
        claimable = List.of();
        growth = List.of();
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    @SubscribeEvent
    static void onSubmitCustomGeometry(SubmitCustomGeometryEvent event) {
        if (!on || (edges.isEmpty() && growth.isEmpty())) return;
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        float lineWidth = Minecraft.getInstance().gameRenderer.gameRenderState().windowRenderState.appropriateLineWidth;
        int base = (int) Math.floor(camera.y) - BAND_BELOW;
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        event.getSubmitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.lines(), (pose, consumer) -> {
            draw(pose, consumer, edges, camera, base, lineWidth, COLOR);
            draw(pose, consumer, growth, camera, base, lineWidth, GROWTH_COLOR);
        });
        poseStack.popPose();
    }

    private static void draw(PoseStack.Pose pose, VertexConsumer consumer, List<VillageBorder.Edge> list, Vec3 camera, int base, float lineWidth, int color) {
        for (VillageBorder.Edge edge : list) {
            double dx = (edge.x1() + edge.x2()) / 2.0 - camera.x;
            double dz = (edge.z1() + edge.z2()) / 2.0 - camera.z;
            if (dx * dx + dz * dz > RANGE * RANGE) continue;
            for (int y = 0; y <= BAND_HEIGHT; y += STEP) line(pose, consumer, edge.x1(), base + y, edge.z1(), edge.x2(), base + y, edge.z2(), lineWidth, color);
            line(pose, consumer, edge.x1(), base, edge.z1(), edge.x1(), base + BAND_HEIGHT, edge.z1(), lineWidth, color);
            line(pose, consumer, edge.x2(), base, edge.z2(), edge.x2(), base + BAND_HEIGHT, edge.z2(), lineWidth, color);
        }
    }

    private static void line(PoseStack.Pose pose, VertexConsumer consumer, float x1, float y1, float z1, float x2, float y2, float z2, float width, int color) {
        float nx = x2 - x1;
        float ny = y2 - y1;
        float nz = z2 - z1;
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length == 0) return;
        nx /= length;
        ny /= length;
        nz /= length;
        consumer.addVertex(pose, x1, y1, z1).setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(width);
        consumer.addVertex(pose, x2, y2, z2).setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(width);
    }
}
