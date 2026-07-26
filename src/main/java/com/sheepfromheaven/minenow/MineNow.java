///
package com.sheepfromheaven.minenow;

import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
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

        FMLCommonSetupEvent.getBus(modBusGroup).addListener(this::commonSetup);

        BuildCreativeModeTabContentsEvent.BUS.addListener(MineNow::addCreative);

        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {

    }

    private static void addCreative(BuildCreativeModeTabContentsEvent event) {

    }

    @Mod.EventBusSubscriber(modid = MOD_ID, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {

        }
    }
}
