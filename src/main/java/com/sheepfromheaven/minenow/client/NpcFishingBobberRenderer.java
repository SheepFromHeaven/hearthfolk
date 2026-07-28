package com.sheepfromheaven.minenow.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sheepfromheaven.minenow.entity.InhabitantEntity;
import com.sheepfromheaven.minenow.entity.NpcFishingBobber;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class NpcFishingBobberRenderer extends EntityRenderer<NpcFishingBobber, NpcFishingBobberRenderState> {
    private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/entity/fishing_hook.png");
    private static final net.minecraft.client.renderer.rendertype.RenderType HOOK_RENDER_TYPE =
            RenderTypes.entityCutout(TEXTURE);

    public NpcFishingBobberRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public NpcFishingBobberRenderState createRenderState() {
        return new NpcFishingBobberRenderState();
    }

    @Override
    public void extractRenderState(NpcFishingBobber entity, NpcFishingBobberRenderState renderState, float partialTick) {
        super.extractRenderState(entity, renderState, partialTick);

        int ownerId = entity.getOwnerEntityId();
        if (ownerId == 0 || !(entity.level() instanceof ClientLevel clientLevel)) {
            renderState.lineVec = Vec3.ZERO;
            return;
        }

        var owner = clientLevel.getEntity(ownerId);
        if (!(owner instanceof InhabitantEntity inhabitant)) {
            renderState.lineVec = Vec3.ZERO;
            return;
        }

        float yawRad = inhabitant.getYRot(partialTick) * (float) (Math.PI / 180.0);
        float sinYaw = Mth.sin(yawRad);
        float cosYaw = Mth.cos(yawRad);
        float scale = inhabitant.getScale();

        Vec3 ownerPos = inhabitant.getPosition(partialTick);
        // Approximate the tip of the rod (not the hand): during fish_wait the right arm is raised
        // forward, so the rod tip sits ~1.5 blocks up and further forward than the shoulder. Tune the
        // forward (1.05) / height (1.5) factors if the line does not meet the rod tip in-game.
        Vec3 handPos = ownerPos.add(
                -cosYaw * 0.35 * scale - sinYaw * 1.05 * scale,
                1.5 * scale,
                -sinYaw * 0.35 * scale + cosYaw * 1.05 * scale
        );

        Vec3 bobberPos = entity.getPosition(partialTick).add(0, 0.25, 0);
        renderState.lineVec = handPos.subtract(bobberPos);
    }

    @Override
    public void submit(NpcFishingBobberRenderState renderState, PoseStack poseStack, SubmitNodeCollector nodeCollector,
                       CameraRenderState cameraRenderState) {
        poseStack.pushPose();
        poseStack.mulPose(cameraRenderState.orientation);
        poseStack.scale(0.5f, 0.5f, 0.5f); // half-size bobber
        nodeCollector.submitCustomGeometry(poseStack, HOOK_RENDER_TYPE, (pose, vc) -> {
            hookVertex(vc, pose, renderState.lightCoords, 1.0f, 0, 1, 1);
            hookVertex(vc, pose, renderState.lightCoords, 1.0f, 1, 1, 0);
            hookVertex(vc, pose, renderState.lightCoords, 0.0f, 1, 0, 0);
            hookVertex(vc, pose, renderState.lightCoords, 0.0f, 0, 0, 1);
        });
        poseStack.popPose();

        float dx = (float) renderState.lineVec.x;
        float dy = (float) renderState.lineVec.y;
        float dz = (float) renderState.lineVec.z;
        if (dx != 0.0f || dy != 0.0f || dz != 0.0f) {
            poseStack.pushPose();
            nodeCollector.submitCustomGeometry(poseStack, RenderTypes.lines(), (pose, vc) -> {
                for (int j = 0; j < 16; j++) {
                    float t0 = (float) j / 16;
                    float t1 = (float) (j + 1) / 16;
                    lineVertex(dx, dy, dz, vc, pose, t0, t1);
                    lineVertex(dx, dy, dz, vc, pose, t1, t0);
                }
            });
            poseStack.popPose();
        }

        super.submit(renderState, poseStack, nodeCollector, cameraRenderState);
    }

    private static void hookVertex(VertexConsumer vc, PoseStack.Pose pose, int light, float u, int v, int us, int vs) {
        vc.addVertex(pose, u - 0.5f, v - 0.5f, 0.0f)
                .setColor(-1)
                .setUv(us, vs)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, 0.0f, 1.0f, 0.0f);
    }

    private static void lineVertex(float dx, float dy, float dz, VertexConsumer vc, PoseStack.Pose pose, float t, float tNext) {
        float x = dx * t;
        float y = dy * (t * t + t) * 0.5f + 0.25f;
        float z = dz * t;
        float nx = dx * tNext - x;
        float ny = dy * (tNext * tNext + tNext) * 0.5f + 0.25f - y;
        float nz = dz * tNext - z;
        float len = Mth.sqrt(nx * nx + ny * ny + nz * nz);
        nx /= len;
        ny /= len;
        nz /= len;
        vc.addVertex(pose, x, y, z).setColor(-16777216).setNormal(pose, nx, ny, nz).setLineWidth(1.0f);
    }
}
