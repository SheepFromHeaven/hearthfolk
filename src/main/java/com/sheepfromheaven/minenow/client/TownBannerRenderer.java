package com.sheepfromheaven.minenow.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sheepfromheaven.minenow.Config;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BannerRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.AABB;

public class TownBannerRenderer extends BannerRenderer {
    private static final GizmoStyle RANGE_STYLE = GizmoStyle.strokeAndFill(
            ARGB.colorFromFloat(0.8F, 0.2F, 0.6F, 1.0F),
            2.0F,
            ARGB.colorFromFloat(0.1F, 0.2F, 0.6F, 1.0F)
    );

    public TownBannerRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void submit(BannerRenderState renderState, PoseStack poseStack, SubmitNodeCollector nodeCollector, CameraRenderState cameraRenderState) {
        super.submit(renderState, poseStack, nodeCollector, cameraRenderState);

        if (RangeTracker.isShowingRange(renderState.blockPos)) {
            int radius = Config.defaultDominionRadius;
            AABB box = new AABB(
                    renderState.blockPos.getX() - radius,
                    renderState.blockPos.getY() - 4,
                    renderState.blockPos.getZ() - radius,
                    renderState.blockPos.getX() + radius + 1,
                    renderState.blockPos.getY() + 20,
                    renderState.blockPos.getZ() + radius + 1
            );
            Gizmos.cuboid(box, RANGE_STYLE);
        }
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }
}
