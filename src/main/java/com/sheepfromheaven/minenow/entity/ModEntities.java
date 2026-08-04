package com.sheepfromheaven.minenow.entity;

import com.sheepfromheaven.minenow.MineNow;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, MineNow.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<InhabitantEntity>> INHABITANT =
            ENTITIES.register("inhabitant", () -> EntityType.Builder.of(
                    InhabitantEntity::new, MobCategory.MISC
            ).sized(0.6f, 1.95f).clientTrackingRange(10)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE,
                            Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "inhabitant"))));

    public static final DeferredHolder<EntityType<?>, EntityType<NpcFishingBobber>> NPC_FISHING_BOBBER =
            ENTITIES.register("npc_fishing_bobber", () -> EntityType.Builder.of(
                    NpcFishingBobber::new, MobCategory.MISC
            ).sized(0.25f, 0.25f).clientTrackingRange(10).noSave()
                    .build(ResourceKey.create(Registries.ENTITY_TYPE,
                            Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "npc_fishing_bobber"))));

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
    }
}
