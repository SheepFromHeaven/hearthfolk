package com.sheepfromheaven.minenow.client;

import com.sheepfromheaven.minenow.MineNow;
import com.sheepfromheaven.minenow.fisherman.FishermanEntities;
import net.minecraft.world.entity.EntityTypes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

@EventBusSubscriber(modid = MineNow.MOD_ID, value = Dist.CLIENT)
public final class MineNowClient {
    private MineNowClient() {}

    @SubscribeEvent
    static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(EntityTypes.VILLAGER, SwingingVillagerRenderer::new);
        event.registerEntityRenderer(FishermanEntities.VILLAGER_BOBBER.get(), VillagerBobberRenderer::new);
    }

    @SubscribeEvent
    static void onScreenInit(ScreenEvent.Init.Post event) {
        GametestPlaygroundWorld.onScreenInit(event);
    }
}
