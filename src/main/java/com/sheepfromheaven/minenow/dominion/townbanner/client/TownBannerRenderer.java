package com.sheepfromheaven.minenow.dominion.townbanner.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sheepfromheaven.minenow.core.Config;
import com.sheepfromheaven.minenow.dominion.townbanner.TownBannerBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BannerRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.AABB;
import com.sheepfromheaven.minenow.pathfinding.client.RangeTracker;

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
    public AABB getRenderBoundingBox(net.minecraft.world.level.block.entity.BannerBlockEntity blockEntity) {
        // The default is a 1x1x1 cube around the banner itself, which the renderer uses every frame
        // to decide whether to bother calling submit() at all - regardless of shouldRenderOffScreen().
        // The range border drawn below is much larger than that cube, so widen it to match, or the
        // border vanishes as soon as that tiny cube (not the border) leaves the view frustum.
        var pos = blockEntity.getBlockPos();
        int radius = getEffectiveRadius(pos);
        return new AABB(
                pos.getX() - radius, pos.getY() - 4, pos.getZ() - radius,
                pos.getX() + radius + 1, pos.getY() + 20, pos.getZ() + radius + 1
        );
    }

    @Override
    public void submit(BannerRenderState renderState, PoseStack poseStack, SubmitNodeCollector nodeCollector, CameraRenderState cameraRenderState) {
        super.submit(renderState, poseStack, nodeCollector, cameraRenderState);

        if (RangeTracker.isShowingRange(renderState.blockPos)) {
            int radius = getEffectiveRadius(renderState.blockPos);
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

    /**
     * The claim radius to draw for the banner at {@code pos}: the flat configured base radius scaled
     * by the dominion's tier (see {@code Dominion#getEffectiveRadius()} server-side). The tier is read
     * from the {@link TownBannerBlockEntity} at this position, which is already synced to the client
     * via block entity data (see {@link TownBannerBlockEntity#setDominionData}); falls back to the flat
     * base radius if the block entity isn't loaded/available client-side for any reason.
     */
    private static int getEffectiveRadius(net.minecraft.core.BlockPos pos) {
        var level = Minecraft.getInstance().level;
        if (level != null && level.getBlockEntity(pos) instanceof TownBannerBlockEntity townBanner) {
            return (int) Math.round(Config.defaultDominionRadius * townBanner.getDominionTier().getRadiusMultiplier());
        }
        return Config.defaultDominionRadius;
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
