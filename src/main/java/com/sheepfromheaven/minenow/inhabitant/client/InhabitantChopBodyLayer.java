package com.sheepfromheaven.minenow.inhabitant.client;

import com.sheepfromheaven.minenow.inhabitant.InhabitantEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.base.GeoRenderer;
import software.bernie.geckolib.renderer.base.RenderPassInfo;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import javax.annotation.Nullable;

/**
 * Additively tilts the "body" bone toward the target block's elevation while the inhabitant is
 * chopping. The chop animation drives body X for the swing sway — this layer adds a fraction of the
 * entity's look pitch on top, so the torso leans back when swinging at a log above head height and
 * forward when swinging below.
 */
public class InhabitantChopBodyLayer<T extends GeoAnimatable, O, R extends GeoRenderState> extends GeoRenderLayer<T, O, R> {
    private static final DataTicket<Float> CHOP_BODY_PITCH = DataTicket.create("minenow_chop_body_pitch", Float.class);
    private static final float BODY_PITCH_SCALE = 0.35f;
    private final String bodyBone;

    public InhabitantChopBodyLayer(GeoRenderer<T, O, R> renderer, String bodyBone) {
        super(renderer);
        this.bodyBone = bodyBone;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void addRenderData(T animatable, @Nullable O relatedObject, R renderState, float partialTick) {
        if (animatable instanceof InhabitantEntity inhabitant && inhabitant.isChopping()) {
            renderState.addGeckolibData(CHOP_BODY_PITCH, inhabitant.getXRot(partialTick));
        }
    }

    @Override
    public void preRender(RenderPassInfo<R> renderPassInfo, SubmitNodeCollector renderTasks) {
        Float pitch = renderPassInfo.renderState().getGeckolibData(CHOP_BODY_PITCH);
        if (pitch == null) return;

        renderPassInfo.addBoneUpdater((info, snapshots) ->
                snapshots.get(bodyBone).ifPresent(bone ->
                        bone.setRotX(bone.getRotX() + (-pitch * Mth.DEG_TO_RAD) * BODY_PITCH_SCALE)));
    }
}
