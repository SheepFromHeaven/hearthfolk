package com.sheepfromheaven.minenow.client;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class NpcFishingBobberRenderState extends EntityRenderState {
    public Vec3 lineVec = Vec3.ZERO;
}
