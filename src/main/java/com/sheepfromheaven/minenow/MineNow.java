///
package com.sheepfromheaven.minenow;

import com.sheepfromheaven.minenow.block.ModBlocks;
import com.sheepfromheaven.minenow.item.ModCreativeTabs;
import com.sheepfromheaven.minenow.item.ModItems;
import com.sheepfromheaven.minenow.network.ModNetwork;
import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
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
        ModCreativeTabs.register(modBusGroup);
        com.sheepfromheaven.minenow.entity.ModEntities.register(modBusGroup);
        com.sheepfromheaven.minenow.inventory.ModMenus.register(modBusGroup);

        FMLCommonSetupEvent.getBus(modBusGroup).addListener(this::commonSetup);

        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        net.minecraftforge.event.entity.EntityAttributeCreationEvent.BUS.addListener(event ->
                event.put(com.sheepfromheaven.minenow.entity.ModEntities.INHABITANT.get(),
                        com.sheepfromheaven.minenow.entity.InhabitantEntity.createAttributes().build()));

        net.minecraftforge.registries.RegisterEvent.getBus(modBusGroup).addListener(event ->
                event.register(net.minecraft.core.registries.Registries.TEST_FUNCTION, helper -> {
                    helper.register("inhabitant_sleeps_in_assigned_bed_at_night",
                            com.sheepfromheaven.minenow.gametest.GoHomeGoalTests::inhabitantSleepsInAssignedBedAtNight);
                    helper.register("inhabitant_walks_home_when_requested",
                            com.sheepfromheaven.minenow.gametest.GoHomeGoalTests::inhabitantWalksHomeWhenRequested);
                    helper.register("inhabitant_walks_around_house_to_bed",
                            com.sheepfromheaven.minenow.gametest.GoHomeGoalTests::inhabitantWalksAroundHouseToBed);
                    helper.register("inhabitant_opens_door_to_reach_bed",
                            com.sheepfromheaven.minenow.gametest.GoHomeGoalTests::inhabitantOpensDoorToReachBed);
                    helper.register("inhabitant_opens_gate_to_reach_bed",
                            com.sheepfromheaven.minenow.gametest.GoHomeGoalTests::inhabitantOpensGateToReachBed);
                    helper.register("disbanding_dominion_converts_inhabitants_and_clears_data",
                            com.sheepfromheaven.minenow.gametest.DominionTests::disbandingDominionConvertsInhabitantsAndClearsData);
                    helper.register("destroying_structure_removes_inhabitant_home_and_job",
                            com.sheepfromheaven.minenow.gametest.DominionTests::destroyingStructureRemovesInhabitantHomeAndJob);
                    helper.register("assigning_new_home_unassigns_inhabitant_from_previous_home",
                            com.sheepfromheaven.minenow.gametest.DominionTests::assigningNewHomeUnassignsInhabitantFromPreviousHome);
                    helper.register("assigning_new_job_unassigns_inhabitant_from_previous_job",
                            com.sheepfromheaven.minenow.gametest.DominionTests::assigningNewJobUnassignsInhabitantFromPreviousJob);
                    helper.register("assigning_house_reserves_bed_from_vanilla_villagers",
                            com.sheepfromheaven.minenow.gametest.DominionTests::assigningHouseReservesBedFromVanillaVillagers);
                    helper.register("storage_containers_skips_workstations",
                            com.sheepfromheaven.minenow.gametest.FisheryStorageTests::storageContainersSkipsWorkstations);
                    helper.register("storage_containers_finds_extra_containers_not_just_required_barrel",
                            com.sheepfromheaven.minenow.gametest.FisheryStorageTests::storageContainersFindsExtraContainersNotJustRequiredBarrel);
                }));

        if (FMLEnvironment.dist == Dist.CLIENT) {
            registerRenderers();
            com.sheepfromheaven.minenow.client.ConfigScreen.registerExtensionPoint(context);
        }
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        ModNetwork.register();
        com.sheepfromheaven.minenow.dominion.StructureRequirementsConfig.load(
                net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get());
    }

    @Mod.EventBusSubscriber(modid = MOD_ID, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> net.minecraft.client.gui.screens.MenuScreens.register(
                    com.sheepfromheaven.minenow.inventory.ModMenus.INHABITANT_INVENTORY_TYPE.get(),
                    com.sheepfromheaven.minenow.client.VanillaRowScreen::new));
        }
    }

    public static void registerRenderers() {
        net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers.BUS.addListener(event -> {
            event.registerBlockEntityRenderer(ModBlocks.TOWN_BANNER_BE.get(),
                    com.sheepfromheaven.minenow.client.TownBannerRenderer::new);
            event.registerBlockEntityRenderer(ModBlocks.STRUCTURE_MARKER_BE.get(),
                    com.sheepfromheaven.minenow.client.StructureMarkerRenderer::new);
            event.registerEntityRenderer(com.sheepfromheaven.minenow.entity.ModEntities.INHABITANT.get(),
                    com.sheepfromheaven.minenow.client.InhabitantRenderer::new);
        });
    }
}
