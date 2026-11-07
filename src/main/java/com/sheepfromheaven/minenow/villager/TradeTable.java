package com.sheepfromheaven.minenow.villager;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sheepfromheaven.minenow.MineNow;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Loaded from {@code data/<namespace>/villager_trades/<profession>.json}. Levels unlock cumulatively. */
public record TradeTable(ResourceKey<VillagerProfession> profession, List<Tool> tools, List<Produce> produce, List<Supply> supplies) {
    public record Tool(Item item, int price, int level) {
        static final Codec<Tool> CODEC = RecordCodecBuilder.create(i -> i.group(
                BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(Tool::item),
                ExtraCodecs.POSITIVE_INT.fieldOf("price").forGetter(Tool::price),
                Codec.intRange(1, 5).optionalFieldOf("level", 1).forGetter(Tool::level)
        ).apply(i, Tool::new));
    }

    public record Produce(Item item, int perEmerald, int level) {
        static final Codec<Produce> CODEC = RecordCodecBuilder.create(i -> i.group(
                BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(Produce::item),
                ExtraCodecs.POSITIVE_INT.fieldOf("per_emerald").forGetter(Produce::perEmerald),
                Codec.intRange(1, 5).optionalFieldOf("level", 1).forGetter(Produce::level)
        ).apply(i, Produce::new));
    }

    /** Bought from players in batches of {@code count} while the villager holds at most {@code max} afterwards. */
    public record Supply(Item item, int count, int price, int max, int level) {
        static final Codec<Supply> CODEC = RecordCodecBuilder.create(i -> i.group(
                BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(Supply::item),
                ExtraCodecs.POSITIVE_INT.fieldOf("count").forGetter(Supply::count),
                ExtraCodecs.POSITIVE_INT.fieldOf("price").forGetter(Supply::price),
                ExtraCodecs.POSITIVE_INT.fieldOf("max").forGetter(Supply::max),
                Codec.intRange(1, 5).optionalFieldOf("level", 1).forGetter(Supply::level)
        ).apply(i, Supply::new));
    }

    static final Codec<TradeTable> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceKey.codec(Registries.VILLAGER_PROFESSION).fieldOf("profession").forGetter(TradeTable::profession),
            Tool.CODEC.listOf().optionalFieldOf("tools", List.of()).forGetter(TradeTable::tools),
            Produce.CODEC.listOf().optionalFieldOf("produce", List.of()).forGetter(TradeTable::produce),
            Supply.CODEC.listOf().optionalFieldOf("supplies", List.of()).forGetter(TradeTable::supplies)
    ).apply(i, TradeTable::new));

    private static volatile Map<ResourceKey<VillagerProfession>, TradeTable> byProfession = Map.of();

    public static Optional<TradeTable> forVillager(Villager villager) {
        return villager.getVillagerData().profession().unwrapKey().map(byProfession::get);
    }

    public static Optional<TradeTable> forProfession(ResourceKey<VillagerProfession> profession) {
        return Optional.ofNullable(byProfession.get(profession));
    }

    public boolean isTool(Item item) {
        return tools.stream().anyMatch(tool -> tool.item() == item);
    }

    public Optional<Supply> supply(Item item) {
        return supplies.stream().filter(supply -> supply.item() == item).findFirst();
    }

    public List<Supply> suppliesUpTo(int level) {
        return supplies.stream().filter(supply -> supply.level() <= level).toList();
    }

    public List<Tool> toolsUpTo(int level) {
        return tools.stream().filter(tool -> tool.level() <= level).toList();
    }

    public List<Produce> produceUpTo(int level) {
        return produce.stream().filter(crop -> crop.level() <= level).toList();
    }

    public static void onAddReloadListeners(AddServerReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "villager_trades"), new Loader());
    }

    private static final class Loader extends SimpleJsonResourceReloadListener<TradeTable> {
        Loader() {
            super(CODEC, FileToIdConverter.json("villager_trades"));
        }

        @Override
        protected void apply(Map<Identifier, TradeTable> tables, ResourceManager manager, ProfilerFiller profiler) {
            Map<ResourceKey<VillagerProfession>, TradeTable> loaded = new HashMap<>();
            tables.values().forEach(table -> loaded.put(table.profession(), table));
            byProfession = Map.copyOf(loaded);
        }
    }
}
