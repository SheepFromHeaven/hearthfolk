package com.sheepfromheaven.minenow.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.sheepfromheaven.minenow.miner.Miner;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.cart.MinecartModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.AbstractMinecartRenderer;
import net.minecraft.client.renderer.entity.state.MinecartRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Draws a parked furnace minecart exactly like the vanilla minecart entity: the cart model with a lit or unlit furnace in it. */
public class FurnaceCartRenderer implements BlockEntityRenderer<FurnaceBlockEntity, FurnaceCartRenderer.State> {
    private static final Identifier MINECART_TEXTURE = Identifier.withDefaultNamespace("textures/entity/minecart/minecart.png");
    private static final int DISPLAY_OFFSET = 6;

    private final MinecartModel model;
    private final BlockModelResolver blockModels;

    public FurnaceCartRenderer(BlockEntityRendererProvider.Context context) {
        this.model = new MinecartModel(context.bakeLayer(ModelLayers.FURNACE_MINECART));
        this.blockModels = context.blockModelResolver();
    }

    public static class State extends BlockEntityRenderState {
        boolean cart;
        float yaw;
        final MinecartRenderState minecart = new MinecartRenderState();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(FurnaceBlockEntity blockEntity, State state, float partialTicks, Vec3 cameraPosition,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);
        BlockState block = blockEntity.getBlockState();
        state.cart = block.is(Miner.FURNACE_CART.get());
        if (!state.cart) return;
        state.yaw = 90.0F - block.getValue(FurnaceBlock.FACING).toYRot();
        state.minecart.displayOffset = DISPLAY_OFFSET;
        state.minecart.lightCoords = state.lightCoords;
        BlockState furnace = Blocks.FURNACE.defaultBlockState().setValue(FurnaceBlock.FACING, Direction.NORTH).setValue(FurnaceBlock.LIT, block.getValue(FurnaceBlock.LIT));
        blockModels.update(state.minecart.displayBlockModel, furnace, AbstractMinecartRenderer.BLOCK_DISPLAY_CONTEXT);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!state.cart) return;
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.375F, 0.5F);
        poseStack.rotateDegrees(Axis.YP, state.yaw);
        poseStack.pushPose();
        poseStack.scale(0.75F, 0.75F, 0.75F);
        poseStack.translate(-0.5F, (DISPLAY_OFFSET - 8) / 16.0F, 0.5F);
        poseStack.rotateDegrees(Axis.YP, 90.0F);
        state.minecart.displayBlockModel.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        collector.submitModel(model, state.minecart, poseStack, MINECART_TEXTURE, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }
}
