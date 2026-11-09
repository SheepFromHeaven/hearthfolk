package com.sheepfromheaven.minenow.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.CrossedArmsItemLayer;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.Tags;

/**
 * What a villager holds: tools and weapons in its right hand, its arms uncrossed (see
 * {@link SwingingVillagerRenderer.SwingingVillagerModel}), held as a player holds them; anything else, like the goods it
 * shows a trading player, lies across its crossed arms as in vanilla.
 */
public class HeldItemLayer extends CrossedArmsItemLayer<VillagerRenderState, VillagerModel> {
    public HeldItemLayer(RenderLayerParent<VillagerRenderState, VillagerModel> renderer) {
        super(renderer);
    }

    public static boolean gripped(ItemStack stack) {
        return stack.has(DataComponents.TOOL) || stack.has(DataComponents.WEAPON) || stack.is(Tags.Items.TOOLS);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, VillagerRenderState state, float yRot, float xRot) {
        if (!(state instanceof SwingingVillagerRenderer.SwingState swing) || !swing.gripped || swing.grippedItem.isEmpty()) {
            super.submit(poseStack, collector, lightCoords, state, yRot, xRot);
            return;
        }
        poseStack.pushPose();
        ((SwingingVillagerRenderer.SwingingVillagerModel) this.getParentModel()).translateToRightHand(poseStack);
        poseStack.rotateDegrees(Axis.XP, -90.0F);
        poseStack.rotateDegrees(Axis.YP, 180.0F);
        poseStack.translate(1.0F / 16.0F, 2.0F / 16.0F, -10.0F / 16.0F);
        swing.grippedItem.submit(poseStack, collector, lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
    }
}
