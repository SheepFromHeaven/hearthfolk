package com.sheepfromheaven.minenow.client;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.base.GeoRenderer;
import software.bernie.geckolib.renderer.base.RenderPassInfo;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Rotates the model's "head" bone to match the entity's actual look direction, pitch included.
 * <p>
 * GeckoLib's built-in {@code DefaultAnimations.hardcodedHeadRotation} reads pitch/yaw from data tickets
 * that {@link software.bernie.geckolib.renderer.GeoEntityRenderer} only populates for non-living entities
 * (see its {@code captureDefaultRenderState}) - for a {@link LivingEntityRenderState} (which every
 * {@link net.minecraft.world.entity.LivingEntity}, including {@link com.sheepfromheaven.minenow.entity.InhabitantEntity},
 * uses) those tickets are simply never set, so the head bone only ever picked up yaw from the model's
 * overall body rotation and never turned up or down. This reads the pitch/yaw Mojang already computes
 * onto {@link LivingEntityRenderState#xRot}/{@link LivingEntityRenderState#yRot} instead.
 */
public class InhabitantHeadRotationLayer<T extends GeoAnimatable, O, R extends GeoRenderState> extends GeoRenderLayer<T, O, R> {
    private final String headBone;

    public InhabitantHeadRotationLayer(GeoRenderer<T, O, R> renderer, String headBone) {
        super(renderer);
        this.headBone = headBone;
    }

    @Override
    public void preRender(RenderPassInfo<R> renderPassInfo, SubmitNodeCollector renderTasks) {
        if (!(renderPassInfo.renderState() instanceof LivingEntityRenderState livingState)) {
            return;
        }
        renderPassInfo.addBoneUpdater((info, snapshots) -> snapshots.get(headBone).ifPresent(bone -> {
            bone.setRotX(-livingState.xRot * Mth.DEG_TO_RAD);
            bone.setRotY(-livingState.yRot * Mth.DEG_TO_RAD);
        }));
    }
}
