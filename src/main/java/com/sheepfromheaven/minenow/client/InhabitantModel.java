package com.sheepfromheaven.minenow.client;

import com.sheepfromheaven.minenow.MineNow;
import com.sheepfromheaven.minenow.dominion.JobType;
import com.sheepfromheaven.minenow.entity.InhabitantEntity;
import net.minecraft.resources.Identifier;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

@OnlyIn(Dist.CLIENT)
public class InhabitantModel extends GeoModel<InhabitantEntity> {
    /** {@link JobType} ordinal captured per-frame by {@link InhabitantRenderer}, or -1 when unemployed. */
    public static final DataTicket<Integer> JOB_TYPE = DataTicket.create("minenow_job_type", Integer.class);

    private static final Identifier MODEL = Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "inhabitant");
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "textures/entity/inhabitant.png");
    private static final Identifier TEXTURE_FISHER = Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "textures/entity/inhabitant_fisherman.png");
    private static final Identifier ANIMATIONS = Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "inhabitant");

    @Override
    public Identifier getModelResource(GeoRenderState renderState) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        int jobId = renderState.getOrDefaultGeckolibData(JOB_TYPE, -1);
        if (jobId >= 0 && jobId < JobType.values().length && JobType.values()[jobId] == JobType.FISHER) {
            return TEXTURE_FISHER;
        }
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(InhabitantEntity animatable) {
        return ANIMATIONS;
    }
}
