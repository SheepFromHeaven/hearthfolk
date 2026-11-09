package com.sheepfromheaven.minenow.client;

import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.npc.BabyVillagerModel;
import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.renderer.entity.AgeableMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.VillagerRenderer;
import net.minecraft.client.renderer.entity.layers.CrossedArmsItemLayer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.VillagerProfessionLayer;
import net.minecraft.client.renderer.entity.state.HoldingEntityRenderState;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * Vanilla's VillagerRenderer, but adult models raise and drop the crossed arms through a swing so a working villager
 * visibly swings the tool it holds (the held item layer follows the arms). Vanilla villager models ignore swings.
 */
public class SwingingVillagerRenderer extends AgeableMobRenderer<Villager, VillagerRenderState, VillagerModel> {
    private static final Identifier VILLAGER_BASE_LOCATION = Identifier.withDefaultNamespace("textures/entity/villager/villager.png");
    private static final Identifier VILLAGER_BABY_LOCATION = Identifier.withDefaultNamespace("textures/entity/villager/villager_baby.png");

    public SwingingVillagerRenderer(EntityRendererProvider.Context context) {
        super(context, new SwingingVillagerModel(context.bakeLayer(ModelLayers.VILLAGER)),
                new BabyVillagerModel(context.bakeLayer(ModelLayers.VILLAGER_BABY)), 0.5F);
        this.addLayer(new CustomHeadLayer<>(this, context.getModelSet(), context.getPlayerSkinRenderCache(), VillagerRenderer.CUSTOM_HEAD_TRANSFORMS));
        this.addLayer(new VillagerProfessionLayer<>(this, context.getResourceManager(), "villager",
                new SwingingVillagerModel(context.bakeLayer(ModelLayers.VILLAGER_NO_HAT)),
                new BabyVillagerModel(context.bakeLayer(ModelLayers.VILLAGER_BABY_NO_HAT))));
        this.addLayer(new CrossedArmsItemLayer<>(this));
    }

    @Override
    public Identifier getTextureLocation(VillagerRenderState state) {
        return state.isBaby ? VILLAGER_BABY_LOCATION : VILLAGER_BASE_LOCATION;
    }

    @Override
    protected float getShadowRadius(VillagerRenderState state) {
        float radius = super.getShadowRadius(state);
        return state.isBaby ? radius * 0.5F : radius;
    }

    @Override
    public VillagerRenderState createRenderState() {
        return new SwingState();
    }

    @Override
    public void extractRenderState(Villager entity, VillagerRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        HoldingEntityRenderState.extractHoldingEntityRenderState(entity, state, this.itemModelResolver);
        state.isUnhappy = entity.getUnhappyCounter() > 0;
        state.villagerData = entity.getVillagerData();
        if (state instanceof SwingState swing) swing.swing = entity.getSwingAnimation(partialTicks);
    }

    public static class SwingState extends VillagerRenderState {
        public float swing;
    }

    static class SwingingVillagerModel extends VillagerModel {
        private static final float SWING_LIFT = 0.9F;
        private final ModelPart arms;

        SwingingVillagerModel(ModelPart root) {
            super(root);
            this.arms = root.getChild("arms");
        }

        @Override
        public void setupAnim(VillagerRenderState state) {
            super.setupAnim(state);
            if (state instanceof SwingState swing && swing.swing > 0.0F) {
                this.arms.xRot -= Mth.sin(swing.swing * (float) Math.PI) * SWING_LIFT;
            }
        }
    }
}
