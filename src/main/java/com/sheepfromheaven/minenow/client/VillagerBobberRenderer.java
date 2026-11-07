package com.sheepfromheaven.minenow.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sheepfromheaven.minenow.fisherman.VillagerBobber;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.FishingHookRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;

/** Vanilla's fishing hook look, with the line running to the rod in the villager's crossed arms. */
public class VillagerBobberRenderer extends EntityRenderer<VillagerBobber, FishingHookRenderState> {
    private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/entity/fishing/fishing_hook.png");
    private static final RenderType RENDER_TYPE = RenderTypes.entityCutoutCull(TEXTURE);
    private static final int LINE_SEGMENTS = 16;

    public VillagerBobberRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void submit(FishingHookRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.pushPose();
        poseStack.scale(0.5F, 0.5F, 0.5F);
        poseStack.rotate(camera.orientation);
        collector.submitCustomGeometry(poseStack, RENDER_TYPE, (pose, buffer) -> {
            vertex(buffer, pose, state.lightCoords, 0.0F, 0, 0, 1);
            vertex(buffer, pose, state.lightCoords, 1.0F, 0, 1, 1);
            vertex(buffer, pose, state.lightCoords, 1.0F, 1, 1, 0);
            vertex(buffer, pose, state.lightCoords, 0.0F, 1, 0, 0);
        });
        poseStack.popPose();
        float xa = (float) state.lineOriginOffset.x;
        float ya = (float) state.lineOriginOffset.y;
        float za = (float) state.lineOriginOffset.z;
        float width = Minecraft.getInstance().gameRenderer.gameRenderState().windowRenderState.appropriateLineWidth;
        collector.submitCustomGeometry(poseStack, RenderTypes.lines(), (pose, buffer) -> {
            for (int i = 0; i < LINE_SEGMENTS; i++) {
                float a0 = (float) i / LINE_SEGMENTS;
                float a1 = (float) (i + 1) / LINE_SEGMENTS;
                lineVertex(xa, ya, za, buffer, pose, a0, a1, width);
                lineVertex(xa, ya, za, buffer, pose, a1, a0, width);
            }
        });
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

    @Override
    public FishingHookRenderState createRenderState() {
        return new FishingHookRenderState();
    }

    @Override
    public void extractRenderState(VillagerBobber entity, FishingHookRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        if (!(entity.getOwner() instanceof Villager owner)) {
            state.lineOriginOffset = Vec3.ZERO;
            return;
        }
        float bodyYaw = Mth.lerp(partialTicks, owner.yBodyRotO, owner.yBodyRot) * Mth.DEG_TO_RAD;
        double forward = 0.5 * owner.getScale();
        double left = 0.2 * owner.getScale();
        double sin = Mth.sin(bodyYaw);
        double cos = Mth.cos(bodyYaw);
        Vec3 rodTip = owner.getPosition(partialTicks).add(-sin * forward + cos * left, 1.45 * owner.getScale(), cos * forward + sin * left);
        state.lineOriginOffset = rodTip.subtract(entity.getPosition(partialTicks).add(0.0, 0.25, 0.0));
    }

    @Override
    protected boolean affectedByCulling(VillagerBobber entity) {
        return false;
    }

    private static void vertex(VertexConsumer builder, PoseStack.Pose pose, int lightCoords, float x, int y, int u, int v) {
        builder.addVertex(pose, x - 0.5F, y - 0.5F, 0.0F)
                .setColor(-1)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(lightCoords)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    private static void lineVertex(float xa, float ya, float za, VertexConsumer buffer, PoseStack.Pose pose, float aa, float nexta, float width) {
        float x = xa * aa;
        float y = ya * (aa * aa + aa) * 0.5F + 0.25F;
        float z = za * aa;
        float nx = xa * nexta - x;
        float ny = ya * (nexta * nexta + nexta) * 0.5F + 0.25F - y;
        float nz = za * nexta - z;
        float length = Mth.sqrt(nx * nx + ny * ny + nz * nz);
        buffer.addVertex(pose, x, y, z).setColor(0xFF000000).setNormal(pose, nx / length, ny / length, nz / length).setLineWidth(width);
    }
}
