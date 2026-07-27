package com.sheepfromheaven.minenow.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sheepfromheaven.minenow.block.StructureMarkerBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class StructureMarkerRenderer implements BlockEntityRenderer<StructureMarkerBlockEntity, StructureMarkerRenderState> {
    private static final GizmoStyle RANGE_STYLE = GizmoStyle.strokeAndFill(
            ARGB.colorFromFloat(0.2F, 0.7F, 0.9F, 1.0F),
            2.0F,
            ARGB.colorFromFloat(0.1F, 0.6F, 0.9F, 1.0F)
    );

    public StructureMarkerRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public StructureMarkerRenderState createRenderState() {
        return new StructureMarkerRenderState();
    }

    @Override
    public void extractRenderState(StructureMarkerBlockEntity blockEntity, StructureMarkerRenderState renderState,
                                    float partialTick, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(blockEntity, renderState, breakProgress);
        renderState.rangeRadius = blockEntity.getStructureType().getRangeRadius();
        renderState.rangeHeight = blockEntity.getStructureType().getRangeHeight();
    }

    @Override
    public void submit(StructureMarkerRenderState renderState, PoseStack poseStack, SubmitNodeCollector nodeCollector, CameraRenderState cameraRenderState) {
        if (renderState.rangeRadius <= 0 || !RangeTracker.isShowingRange(renderState.blockPos)) {
            return;
        }

        int radius = renderState.rangeRadius;
        int height = renderState.rangeHeight;
        int radiusSq = radius * radius;
        var center = renderState.blockPos;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radiusSq) continue;
                boolean isBoundary = (dx + 1) * (dx + 1) + dz * dz > radiusSq
                        || (dx - 1) * (dx - 1) + dz * dz > radiusSq
                        || dx * dx + (dz + 1) * (dz + 1) > radiusSq
                        || dx * dx + (dz - 1) * (dz - 1) > radiusSq;
                if (!isBoundary) continue;

                AABB column = new AABB(
                        center.getX() + dx, center.getY() - height, center.getZ() + dz,
                        center.getX() + dx + 1, center.getY() + height + 1, center.getZ() + dz + 1
                );
                Gizmos.cuboid(column, RANGE_STYLE);
            }
        }
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
