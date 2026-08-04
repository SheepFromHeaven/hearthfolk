package com.sheepfromheaven.minenow.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sheepfromheaven.minenow.block.StructureMarkerBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.RectGizmo;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class StructureMarkerRenderer implements BlockEntityRenderer<StructureMarkerBlockEntity, StructureMarkerRenderState> {
    private static final GizmoStyle RANGE_STYLE = GizmoStyle.fill(
            ARGB.colorFromFloat(0.45F, 0.2F, 0.7F, 0.9F)
    );
    private static final GizmoStyle WORK_AREA_STYLE = GizmoStyle.fill(
            ARGB.colorFromFloat(0.45F, 1.0F, 0.3F, 0.7F)
    );

    public StructureMarkerRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public AABB getRenderBoundingBox(StructureMarkerBlockEntity blockEntity) {
        return blockEntity.getRenderBoundingBox();
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
        renderState.workAreaCenter = blockEntity.getWorkAreaCenter();
        renderState.workAreaRadius = blockEntity.getWorkAreaRadius();
    }

    @Override
    public void submit(StructureMarkerRenderState renderState, PoseStack poseStack, SubmitNodeCollector nodeCollector, CameraRenderState cameraRenderState) {
        if (renderState.rangeRadius > 0 && RangeTracker.isShowingRange(renderState.blockPos)) {
            emitBox(renderState.blockPos, renderState.rangeRadius, renderState.rangeHeight, RANGE_STYLE);
        }
        if (renderState.workAreaCenter != null && renderState.workAreaRadius > 0
                && WorkAreaTracker.isShowingWorkArea(renderState.blockPos)) {
            emitBox(renderState.workAreaCenter, renderState.workAreaRadius, renderState.rangeHeight, WORK_AREA_STYLE);
        }
    }

    private void emitBox(BlockPos center, int radius, int height, GizmoStyle style) {
        int radiusSq = radius * radius;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radiusSq) continue;

                emitWallIfOutward(center, dx, dz, dx + 1, dz, height, Direction.EAST, radiusSq, style);
                emitWallIfOutward(center, dx, dz, dx - 1, dz, height, Direction.WEST, radiusSq, style);
                emitWallIfOutward(center, dx, dz, dx, dz + 1, height, Direction.SOUTH, radiusSq, style);
                emitWallIfOutward(center, dx, dz, dx, dz - 1, height, Direction.NORTH, radiusSq, style);
            }
        }
    }

    private void emitWallIfOutward(BlockPos center, int dx, int dz, int neighborDx, int neighborDz, int height, Direction face, int radiusSq, GizmoStyle style) {
        if (neighborDx * neighborDx + neighborDz * neighborDz <= radiusSq) {
            return;
        }
        Vec3 min = new Vec3(center.getX() + dx, center.getY() - height, center.getZ() + dz);
        Vec3 max = new Vec3(center.getX() + dx + 1, center.getY() + height + 1, center.getZ() + dz + 1);
        var quad = RectGizmo.fromCuboidFace(min, max, face, style);
        Gizmos.rect(quad.a(), quad.b(), quad.c(), quad.d(), style);
        Gizmos.rect(quad.d(), quad.c(), quad.b(), quad.a(), style);
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
