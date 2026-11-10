package com.sheepfromheaven.minenow.cleric;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sheepfromheaven.minenow.MineNow;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What a cleric brews, from {@code data/<namespace>/cleric_brews/*.json} (all files merged, in file order, so a chain
 * lists its steps first): {@code reagent} turns the {@code input} potion into the {@code output}, as vanilla's brewing
 * recipes say. A step is brewed once its level is reached and the cleric holds fewer than {@code stock} of the output;
 * one with a {@code price} (emeralds per potion) is for sale, one without only feeds the next step.
 */
public final class Brews {
    public record PotionRef(Item form, Holder<Potion> potion) {
        static final Codec<PotionRef> CODEC = RecordCodecBuilder.create(i -> i.group(
                BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(PotionRef::form),
                BuiltInRegistries.POTION.holderByNameCodec().fieldOf("potion").forGetter(PotionRef::potion)
        ).apply(i, PotionRef::new));

        public ItemStack stack() {
            return PotionGoods.stack(form, potion);
        }
    }

    public record Brew(PotionRef input, Item reagent, PotionRef output, int level, int stock, Optional<Integer> price) {
        static final Codec<Brew> CODEC = RecordCodecBuilder.create(i -> i.group(
                PotionRef.CODEC.fieldOf("input").forGetter(Brew::input),
                BuiltInRegistries.ITEM.byNameCodec().fieldOf("reagent").forGetter(Brew::reagent),
                PotionRef.CODEC.fieldOf("output").forGetter(Brew::output),
                Codec.intRange(1, 5).optionalFieldOf("level", 1).forGetter(Brew::level),
                ExtraCodecs.POSITIVE_INT.optionalFieldOf("stock", 1).forGetter(Brew::stock),
                ExtraCodecs.POSITIVE_INT.optionalFieldOf("price").forGetter(Brew::price)
        ).apply(i, Brew::new));
    }

    private record BrewList(List<Brew> brews) {
        static final Codec<BrewList> CODEC = RecordCodecBuilder.create(i -> i.group(
                Brew.CODEC.listOf().fieldOf("brews").forGetter(BrewList::brews)
        ).apply(i, BrewList::new));
    }

    private static volatile List<Brew> all = List.of();

    private Brews() {}

    public static List<Brew> all() {
        return all;
    }

    public static List<Brew> upTo(int level) {
        return all.stream().filter(brew -> brew.level() <= level).toList();
    }

    public static void onAddReloadListeners(AddServerReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "cleric_brews"), new Loader());
    }

    private static final class Loader extends SimpleJsonResourceReloadListener<BrewList> {
        Loader() {
            super(BrewList.CODEC, FileToIdConverter.json("cleric_brews"));
        }

        @Override
        protected void apply(Map<Identifier, BrewList> lists, ResourceManager manager, ProfilerFiller profiler) {
            List<Brew> merged = new ArrayList<>();
            lists.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> merged.addAll(entry.getValue().brews()));
            all = List.copyOf(merged);
        }
    }
}
