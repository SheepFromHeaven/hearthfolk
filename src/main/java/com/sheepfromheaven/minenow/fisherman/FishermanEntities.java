package com.sheepfromheaven.minenow.fisherman;

import com.sheepfromheaven.minenow.MineNow;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class FishermanEntities {
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, MineNow.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<VillagerBobber>> VILLAGER_BOBBER = ENTITY_TYPES.register("villager_bobber",
            id -> EntityType.Builder.<VillagerBobber>of(VillagerBobber::new, MobCategory.MISC)
                    .noLootTable()
                    .noSave()
                    .noSummon()
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(4)
                    .updateInterval(5)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id)));

    private FishermanEntities() {}

    public static void register(IEventBus modBus) {
        ENTITY_TYPES.register(modBus);
    }
}
