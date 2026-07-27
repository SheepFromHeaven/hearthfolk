package com.sheepfromheaven.minenow.client;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.BlockPos;

public class StructureMarkerRenderState extends BlockEntityRenderState {
    public int rangeRadius;
    public int rangeHeight;
    public BlockPos workAreaCenter;
    public int workAreaRadius;
}
