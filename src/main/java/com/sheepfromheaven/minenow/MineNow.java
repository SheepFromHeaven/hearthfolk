///
package com.sheepfromheaven.minenow;

import com.sheepfromheaven.minenow.block.ModBlocks;
import com.sheepfromheaven.minenow.item.ModItems;
import com.sheepfromheaven.minenow.network.ModNetwork;
import com.mojang.logging.LogUtils;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(MineNow.MOD_ID)
public final class MineNow {
    public static final String MOD_ID = "minenow";
    private static final Logger LOGGER = LogUtils.getLogger();

    public MineNow(FMLJavaModLoadingContext context) {
        var modBusGroup = context.getModBusGroup();

        ModBlocks.register(modBusGroup);
        ModItems.register(modBusGroup);

        FMLCommonSetupEvent.getBus(modBusGroup).addListener(this::commonSetup);

        BuildCreativeModeTabContentsEvent.BUS.addListener(MineNow::addCreative);

        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            registerRenderers();
            context.registerExtensionPoint(
                    net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory.class,
                    () -> new net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory(
                            parent -> new com.sheepfromheaven.minenow.client.ConfigScreen(parent)));
        }
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        ModNetwork.register();
    }

    private static void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ModItems.TOWN_BANNER.get());
            event.accept(ModItems.STRUCTURE_MARKER.get());
        }
    }

    @Mod.EventBusSubscriber(modid = MOD_ID, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
        }
    }

    public static void registerRenderers() {
        net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers.BUS.addListener(event ->
                event.registerBlockEntityRenderer(ModBlocks.TOWN_BANNER_BE.get(),
                        com.sheepfromheaven.minenow.client.TownBannerRenderer::new));
    }
}
