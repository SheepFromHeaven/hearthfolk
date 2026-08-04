package com.sheepfromheaven.minenow.client;

import com.sheepfromheaven.minenow.MineNow;
import com.sheepfromheaven.minenow.dominion.JobType;
import com.sheepfromheaven.minenow.entity.InhabitantEntity;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

public class InhabitantModel extends GeoModel<InhabitantEntity> {
    /** {@link JobType} ordinal captured per-frame by {@link InhabitantRenderer}, or -1 when unemployed. */
    public static final DataTicket<Integer> JOB_TYPE = DataTicket.create("minenow_job_type", Integer.class);

    private static final Identifier MODEL = Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "inhabitant");
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "textures/entity/inhabitant.png");
    private static final Identifier TEXTURE_FISHER = Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "textures/entity/inhabitant_fisherman.png");
    private static final Identifier TEXTURE_COOK = Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "textures/entity/inhabitant_cook.png");
    private static final Identifier TEXTURE_LUMBERJACK = Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "textures/entity/inhabitant_lumberjack.png");
    private static final Identifier ANIMATIONS = Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "inhabitant");

    @Override
    public Identifier getModelResource(GeoRenderState renderState) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        int jobId = renderState.getOrDefaultGeckolibData(JOB_TYPE, -1);
        if (jobId >= 0 && jobId < JobType.values().length) {
            return switch (JobType.values()[jobId]) {
                case FISHER -> TEXTURE_FISHER;
                case COOK -> TEXTURE_COOK;
                case LUMBERJACK -> TEXTURE_LUMBERJACK;
                case COURIER -> TEXTURE;
            };
        }
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(InhabitantEntity animatable) {
        return ANIMATIONS;
    }
}
