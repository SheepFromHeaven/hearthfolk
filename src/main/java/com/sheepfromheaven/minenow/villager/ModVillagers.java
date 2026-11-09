package com.sheepfromheaven.minenow.villager;

import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.Codec;
import com.sheepfromheaven.minenow.MineNow;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.UUID;
import java.util.function.Supplier;

public final class ModVillagers {
    private static final DeferredRegister<PoiType> POI_TYPES = DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, MineNow.MOD_ID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, MineNow.MOD_ID);

    public static final DeferredHolder<PoiType, PoiType> HOME_STORAGE = POI_TYPES.register("home_storage",
            () -> new PoiType(ImmutableSet.copyOf(Blocks.CHEST.getStateDefinition().getPossibleStates()), 1, 1));

    public static final Supplier<AttachmentType<GlobalPos>> STORAGE = ATTACHMENT_TYPES.register("storage",
            () -> AttachmentType.builder(() -> GlobalPos.of(net.minecraft.world.level.Level.OVERWORLD, net.minecraft.core.BlockPos.ZERO))
                    .serialize(GlobalPos.MAP_CODEC)
                    .build());

    public static final Supplier<AttachmentType<UUID>> PLACED_BY = ATTACHMENT_TYPES.register("placed_by",
            () -> AttachmentType.builder(() -> new UUID(0L, 0L))
                    .serialize(UUIDUtil.CODEC.fieldOf("player"))
                    .build());

    public static final Supplier<AttachmentType<Boolean>> PURSE_GRANTED = ATTACHMENT_TYPES.register("purse_granted",
            () -> AttachmentType.builder(() -> false)
                    .serialize(Codec.BOOL.fieldOf("granted"))
                    .copyOnDeath()
                    .build());

    /** On a dropped item: the villager whose goods it is (see {@link Belongings}). */
    public static final Supplier<AttachmentType<UUID>> BELONGS_TO = ATTACHMENT_TYPES.register("belongs_to",
            () -> AttachmentType.builder(() -> new UUID(0L, 0L))
                    .serialize(UUIDUtil.CODEC.fieldOf("villager"))
                    .build());

    public static final Supplier<AttachmentType<Long>> LAST_MEAL = ATTACHMENT_TYPES.register("last_meal",
            () -> AttachmentType.builder(() -> 0L)
                    .serialize(Codec.LONG.fieldOf("time"))
                    .build());

    public static final TagKey<Item> NO_KEY = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "villager_storage_key"));

    private ModVillagers() {}

    public static void register(IEventBus modBus) {
        POI_TYPES.register(modBus);
        ATTACHMENT_TYPES.register(modBus);
    }
}
