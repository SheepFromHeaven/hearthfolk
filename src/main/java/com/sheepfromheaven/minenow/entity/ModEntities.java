package com.sheepfromheaven.minenow.entity;

import com.sheepfromheaven.minenow.MineNow;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MineNow.MOD_ID);

    public static final RegistryObject<EntityType<InhabitantEntity>> INHABITANT =
            ENTITIES.register("inhabitant", () -> EntityType.Builder.of(
                    InhabitantEntity::new, MobCategory.MISC
            ).sized(0.6f, 1.95f).clientTrackingRange(10)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE,
                            Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "inhabitant"))));

    public static void register(BusGroup modBusGroup) {
        ENTITIES.register(modBusGroup);
    }
}
