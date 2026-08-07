package com.sheepfromheaven.minenow.inhabitant.client;

import com.sheepfromheaven.minenow.inhabitant.InhabitantEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.neoforged.api.distmarker.Dist;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import com.sheepfromheaven.minenow.jobs.fisher.client.FisherRodInHandLayer;

public class InhabitantRenderer<R extends EntityRenderState & GeoRenderState> extends GeoEntityRenderer<InhabitantEntity, R> {
    public InhabitantRenderer(EntityRendererProvider.Context context) {
        super(context, new InhabitantModel());
        withRenderLayer(new InhabitantHeadRotationLayer<>(this, "head"));
        withRenderLayer(new InhabitantChopBodyLayer<>(this, "body"));
        withRenderLayer(new FisherRodInHandLayer<>(this, "RightHandItem", "LeftHandItem"));
    }

    @Override
    public void addRenderData(InhabitantEntity animatable, Void relatedObject, R renderState, float partialTick) {
        super.addRenderData(animatable, relatedObject, renderState, partialTick);
        renderState.addGeckolibData(InhabitantModel.JOB_TYPE, (int) animatable.getJobTypeId());
    }
}
