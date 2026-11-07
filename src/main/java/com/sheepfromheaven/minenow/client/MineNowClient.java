package com.sheepfromheaven.minenow.client;

import com.sheepfromheaven.minenow.MineNow;
import net.minecraft.world.entity.EntityTypes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = MineNow.MOD_ID, value = Dist.CLIENT)
public final class MineNowClient {
    private MineNowClient() {}

    @SubscribeEvent
    static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(EntityTypes.VILLAGER, SwingingVillagerRenderer::new);
    }
}
