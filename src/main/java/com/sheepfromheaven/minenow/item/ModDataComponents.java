package com.sheepfromheaven.minenow.item;

import com.mojang.serialization.Codec;
import com.sheepfromheaven.minenow.MineNow;
import com.sheepfromheaven.minenow.dominion.StructureType;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModDataComponents {
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, MineNow.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<StructureType>> WAND_MODE =
            DATA_COMPONENTS.register("wand_mode", () -> DataComponentType.<StructureType>builder()
                    .persistent(Codec.STRING.xmap(StructureType::valueOf, StructureType::name))
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8.map(StructureType::valueOf, StructureType::name))
                    .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<FilterData>> FILTER_DATA =
            DATA_COMPONENTS.register("filter_data", () -> DataComponentType.<FilterData>builder()
                    .persistent(FilterData.CODEC)
                    .networkSynchronized(FilterData.STREAM_CODEC)
                    .build());

    public static void register(net.neoforged.bus.api.IEventBus modBus) {
        DATA_COMPONENTS.register(modBus);
    }
}
