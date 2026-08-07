package com.sheepfromheaven.minenow.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Configuration carried by a Filter item: up to 9 template stacks plus whitelist/blacklist and metadata-matching flags. */
public record FilterData(List<ItemStack> templates, boolean whitelist, boolean matchMetadata) {
    public static final FilterData EMPTY = new FilterData(List.of(), true, false);

    public static final Codec<FilterData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ItemStack.OPTIONAL_CODEC.listOf().fieldOf("templates").forGetter(FilterData::templates),
            Codec.BOOL.fieldOf("whitelist").forGetter(FilterData::whitelist),
            Codec.BOOL.fieldOf("matchMetadata").forGetter(FilterData::matchMetadata)
    ).apply(instance, FilterData::new));

    public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, FilterData> STREAM_CODEC = StreamCodec.composite(
            ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list()), FilterData::templates,
            ByteBufCodecs.BOOL, FilterData::whitelist,
            ByteBufCodecs.BOOL, FilterData::matchMetadata,
            FilterData::new
    );
}
