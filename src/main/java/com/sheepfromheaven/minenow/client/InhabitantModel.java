package com.sheepfromheaven.minenow.client;

import com.sheepfromheaven.minenow.MineNow;
import com.sheepfromheaven.minenow.entity.InhabitantEntity;
import net.minecraft.resources.Identifier;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

@OnlyIn(Dist.CLIENT)
public class InhabitantModel extends GeoModel<InhabitantEntity> {
    private static final Identifier MODEL = Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "inhabitant");
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "textures/entity/inhabitant.png");
    private static final Identifier ANIMATIONS = Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "inhabitant");

    @Override
    public Identifier getModelResource(GeoRenderState renderState) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(InhabitantEntity animatable) {
        return ANIMATIONS;
    }
}
