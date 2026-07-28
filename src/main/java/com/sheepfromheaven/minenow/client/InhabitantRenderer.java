package com.sheepfromheaven.minenow.client;

import com.sheepfromheaven.minenow.entity.InhabitantEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;

@OnlyIn(Dist.CLIENT)
public class InhabitantRenderer<R extends EntityRenderState & GeoRenderState> extends GeoEntityRenderer<InhabitantEntity, R> {
    public InhabitantRenderer(EntityRendererProvider.Context context) {
        super(context, new InhabitantModel());
        withRenderLayer(new InhabitantHeadRotationLayer<>(this, "head"));
    }
}
