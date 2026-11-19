package com.sheepfromheaven.minenow.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.zombie.ZombieVillagerModel;
import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.client.renderer.entity.state.ZombieVillagerRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

/**
 * The armor a villager wears. Vanilla villager models have no armor layer, so it's drawn with the zombie villager's
 * armor models (same head and body), posed from the villager, with sleeves only when its arms are uncrossed and the
 * chestplate deepened over the robe; a helmet replaces the profession's hat (see {@link SwingingVillagerRenderer}).
 */
public class VillagerArmorLayer extends RenderLayer<VillagerRenderState, VillagerModel> {
    /** The armor's state on vanilla's villager render state, when vanilla's renderer is kept for EMF (see {@link MineNowClient}). */
    static final net.minecraft.util.context.ContextKey<SwingingVillagerRenderer.ArmorState> ARMOR =
            new net.minecraft.util.context.ContextKey<>(net.minecraft.resources.Identifier.fromNamespaceAndPath(com.sheepfromheaven.minenow.MineNow.MOD_ID, "villager_armor"));

    private final ArmorModelSet<Fitted> models;
    private final EquipmentLayerRenderer equipmentRenderer;

    public VillagerArmorLayer(RenderLayerParent<VillagerRenderState, VillagerModel> renderer, EntityRendererProvider.Context context) {
        super(renderer);
        this.models = ArmorModelSet.bake(ModelLayers.ZOMBIE_VILLAGER_ARMOR, context.getModelSet(), Fitted::new);
        this.models.legs().legDepth = 1.45F;
        this.models.feet().legDepth = 1.2F;
        this.equipmentRenderer = context.getEquipmentRenderer();
    }

    /**
     * The zombie villager's armor fitted to a villager: sleeves only on free arms (posed like them), helmet and chestplate a pixel lower, the chestplate
     * deepened over the robe (7 deep, the armor 6.2), and the legs of leggings and boots deepened to sit outside it too.
     */
    static class Fitted extends ZombieVillagerModel<ZombieVillagerRenderState> {
        private static final float DROP = 1.0F;
        private static final float BODY_DEPTH = 1.2F;
        private float legDepth = 1.0F;

        Fitted(net.minecraft.client.model.geom.ModelPart root) {
            super(root);
        }

        @Override
        public void setupAnim(ZombieVillagerRenderState state) {
            super.setupAnim(state);
            boolean free = state instanceof SwingingVillagerRenderer.ArmorState armor && armor.freeArms;
            this.rightArm.visible = free;
            this.leftArm.visible = free;
            if (state instanceof SwingingVillagerRenderer.ArmorState armor) {
                this.rightArm.xRot = armor.rightArmX;
                this.leftArm.xRot = armor.leftArmX;
                this.rightArm.yRot = armor.rightArmY;
                this.leftArm.yRot = armor.leftArmY;
                this.rightArm.zRot = armor.spread;
                this.leftArm.zRot = -armor.spread;
                this.rightArm.x = -5.0F;
                this.leftArm.x = 5.0F;
                this.rightArm.y = 2.0F;
                this.leftArm.y = 2.0F;
                this.rightArm.z = 0.0F;
                this.leftArm.z = 0.0F;
            }
            this.head.y += DROP;
            this.body.y += DROP;
            this.body.xRot = 0.0F;
            this.body.yRot = 0.0F;
            this.body.zRot = 0.0F;
            this.body.zScale = BODY_DEPTH;
            this.rightLeg.zScale = legDepth;
            this.leftLeg.zScale = legDepth;
        }
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, VillagerRenderState state, float yRot, float xRot) {
        SwingingVillagerRenderer.ArmorState armor = state instanceof SwingingVillagerRenderer.SwingState swing ? swing.armor : state.getRenderData(ARMOR);
        if (armor == null || state.isBaby) return;
        piece(poseStack, collector, armor.chestEquipment, EquipmentSlot.CHEST, lightCoords, armor);
        piece(poseStack, collector, armor.legsEquipment, EquipmentSlot.LEGS, lightCoords, armor);
        piece(poseStack, collector, armor.feetEquipment, EquipmentSlot.FEET, lightCoords, armor);
        piece(poseStack, collector, armor.headEquipment, EquipmentSlot.HEAD, lightCoords, armor);
    }

    private void piece(PoseStack poseStack, SubmitNodeCollector collector, ItemStack stack, EquipmentSlot slot, int lightCoords, ZombieVillagerRenderState state) {
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable == null || equippable.slot() != slot || equippable.assetId().isEmpty()) return;
        EquipmentClientInfo.LayerType layer = slot == EquipmentSlot.LEGS ? EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS : EquipmentClientInfo.LayerType.HUMANOID;
        equipmentRenderer.renderLayers(layer, equippable.assetId().get(), models.get(slot), state, stack, poseStack, collector, lightCoords, state.outlineColor);
    }
}
