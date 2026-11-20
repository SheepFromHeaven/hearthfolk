package com.sheepfromheaven.minenow.client;

import com.sheepfromheaven.minenow.MineNow;
import com.sheepfromheaven.minenow.fisherman.FishermanEntities;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.entity.BlockEntityTypes;
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
        if (!keepsVanillaVillagers()) event.registerEntityRenderer(EntityTypes.VILLAGER, SwingingVillagerRenderer::new);
        event.registerEntityRenderer(FishermanEntities.VILLAGER_BOBBER.get(), VillagerBobberRenderer::new);
        event.registerBlockEntityRenderer(BlockEntityTypes.FURNACE, FurnaceCartRenderer::new);
    }

    @SubscribeEvent
    static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(SwingingVillagerRenderer.SwingingVillagerModel.LAYER, SwingingVillagerRenderer.SwingingVillagerModel::layer);
        event.registerLayerDefinition(SwingingVillagerRenderer.SwingingVillagerModel.NO_HAT_LAYER, SwingingVillagerRenderer.SwingingVillagerModel::noHatLayer);
    }

    /**
     * Entity Model Features (and the resource packs built on it, like Fresh Animations) reworks vanilla's villager model,
     * found by vanilla's model layer names, so with it installed vanilla's villager renderer stays and only our armor is
     * added on top: villagers keep crossed arms and EMF's animations instead of our free arms and swings.
     */
    static boolean keepsVanillaVillagers() {
        return net.neoforged.fml.ModList.get().isLoaded("entity_model_features");
    }

    @SubscribeEvent
    static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        if (!keepsVanillaVillagers()) return;
        net.minecraft.client.renderer.entity.LivingEntityRenderer<net.minecraft.world.entity.npc.villager.Villager, net.minecraft.client.renderer.entity.state.VillagerRenderState, net.minecraft.client.model.npc.VillagerModel> villagers = event.getRenderer(EntityTypes.VILLAGER);
        if (villagers != null) villagers.addLayer(new VillagerArmorLayer(villagers, event.getContext()));
    }

    @SubscribeEvent
    static void onRegisterRenderStateModifiers(net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent event) {
        if (!keepsVanillaVillagers()) return;
        event.registerEntityModifier(net.minecraft.client.renderer.entity.VillagerRenderer.class, (entity, state) -> {
            if (!(entity instanceof net.minecraft.world.entity.npc.villager.Villager villager) || !(state instanceof net.minecraft.client.renderer.entity.state.VillagerRenderState villagerState)) return;
            SwingingVillagerRenderer.ArmorState armor = new SwingingVillagerRenderer.ArmorState();
            armor.fill(villager, villagerState, state.partialTick, net.minecraft.client.Minecraft.getInstance().getItemModelResolver(), false, new float[4]);
            state.setRenderData(VillagerArmorLayer.ARMOR, armor);
        });
    }

    @SubscribeEvent
    static void onClientSetup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
        net.neoforged.fml.ModList.get().getModContainerById(MineNow.MOD_ID).ifPresent(container -> container.registerExtensionPoint(
                net.neoforged.neoforge.client.gui.IConfigScreenFactory.class, (mod, parent) -> new FeatureSettingsScreen(parent)));
    }

    @SubscribeEvent
    static void onScreenInit(ScreenEvent.Init.Post event) {
        GametestPlaygroundWorld.onScreenInit(event);
    }
}
