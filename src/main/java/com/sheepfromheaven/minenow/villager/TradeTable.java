package com.sheepfromheaven.minenow.villager;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sheepfromheaven.minenow.MineNow;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.codec.RegistryCodecs;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Loaded from {@code data/<namespace>/villager_trades/<profession>.json}. Levels unlock cumulatively. */
public record TradeTable(ResourceKey<VillagerProfession> profession, Optional<TagKey<Item>> toolTag, List<Tool> tools, List<Produce> produce,
                         List<Supply> supplies, Optional<DefaultProduce> defaultProduce, List<Keep> keeps, List<Craft> crafts) {
    public record Tool(Item item, int price, int level) {
        static final Codec<Tool> CODEC = RecordCodecBuilder.create(i -> i.group(
                BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(Tool::item),
                ExtraCodecs.POSITIVE_INT.fieldOf("price").forGetter(Tool::price),
                Codec.intRange(1, 5).optionalFieldOf("level", 1).forGetter(Tool::level)
        ).apply(i, Tool::new));
    }

    /** One decimal price per item: 0.05 is 20 for an emerald, 3 is one for three emeralds ({@link Price#of}). */
    static final Codec<Price> PRICE_CODEC = Codec.DOUBLE
            .validate(value -> value > 0 ? com.mojang.serialization.DataResult.success(value) : com.mojang.serialization.DataResult.error(() -> "price must be above 0, got " + value))
            .xmap(Price::of, Price::perItem);

    public record Produce(Item item, Price price, int level) {
        static final Codec<Produce> CODEC = RecordCodecBuilder.create(i -> i.group(
                BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(Produce::item),
                PRICE_CODEC.fieldOf("price").forGetter(Produce::price),
                Codec.intRange(1, 5).optionalFieldOf("level", 1).forGetter(Produce::level)
        ).apply(i, Produce::new));
    }

    /** Bought from players at {@code price} per item, in the smallest whole-emerald batch, while the villager holds at most {@code max} afterwards. */
    public record Supply(Item item, Price price, int max, int level) {
        static final Codec<Supply> CODEC = RecordCodecBuilder.create(i -> i.group(
                BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(Supply::item),
                PRICE_CODEC.fieldOf("price").forGetter(Supply::price),
                ExtraCodecs.POSITIVE_INT.fieldOf("max").forGetter(Supply::max),
                Codec.intRange(1, 5).optionalFieldOf("level", 1).forGetter(Supply::level)
        ).apply(i, Supply::new));
    }

    /** Price for anything in {@code items} that has no {@code produce} entry of its own. */
    public record DefaultProduce(HolderSet<Item> items, Price price, int level) {
        static final Codec<DefaultProduce> CODEC = RecordCodecBuilder.create(i -> i.group(
                RegistryCodecs.holderSet(Registries.ITEM).fieldOf("items").forGetter(DefaultProduce::items),
                PRICE_CODEC.fieldOf("price").forGetter(DefaultProduce::price),
                Codec.intRange(1, 5).optionalFieldOf("level", 1).forGetter(DefaultProduce::level)
        ).apply(i, DefaultProduce::new));
    }

    /** Goods the villager makes itself and works on further (the butcher's raw meat for its smoker): kept up to {@code max} when unloading, never bought. */
    public record Keep(HolderSet<Item> items, int max) {
        static final Codec<Keep> CODEC = RecordCodecBuilder.create(i -> i.group(
                RegistryCodecs.holderSet(Registries.ITEM).fieldOf("items").forGetter(Keep::items),
                ExtraCodecs.POSITIVE_INT.fieldOf("max").forGetter(Keep::max)
        ).apply(i, Keep::new));
    }

    public record Material(Item item, int count) {
        static final Codec<Material> CODEC = RecordCodecBuilder.create(i -> i.group(
                BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(Material::item),
                ExtraCodecs.POSITIVE_INT.fieldOf("count").forGetter(Material::count)
        ).apply(i, Material::new));
    }

    /**
     * Made at the villager's workstation from {@code materials}, {@code count} at a time, wearing the tool of its trade
     * {@code tool_uses} times, kept {@code stock} (and at least one sale's batch) in its goods, sold at {@link #craftPrice},
     * or at its {@code produce} price when it has one. {@code enchants}: each one made is a book the librarian enchants
     * at its level's power ({@code librarian/Librarian}), so no two are alike and it is sold by the book, never by item.
     */
    public record Craft(Item item, List<Material> materials, double labor, int level, int stock, int count, int toolUses, boolean enchants) {
        static final Codec<Craft> CODEC = RecordCodecBuilder.create(i -> i.group(
                BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(Craft::item),
                Material.CODEC.listOf().fieldOf("materials").forGetter(Craft::materials),
                Codec.DOUBLE.optionalFieldOf("labor", 0.0).forGetter(Craft::labor),
                Codec.intRange(1, 5).optionalFieldOf("level", 1).forGetter(Craft::level),
                ExtraCodecs.POSITIVE_INT.optionalFieldOf("stock", 1).forGetter(Craft::stock),
                ExtraCodecs.POSITIVE_INT.optionalFieldOf("count", 1).forGetter(Craft::count),
                ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("tool_uses", 0).forGetter(Craft::toolUses),
                Codec.BOOL.optionalFieldOf("enchants", false).forGetter(Craft::enchants)
        ).apply(i, Craft::new));
    }

    static final Codec<TradeTable> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceKey.codec(Registries.VILLAGER_PROFESSION).fieldOf("profession").forGetter(TradeTable::profession),
            TagKey.hashedCodec(Registries.ITEM).optionalFieldOf("tool_tag").forGetter(TradeTable::toolTag),
            Tool.CODEC.listOf().optionalFieldOf("tools", List.of()).forGetter(TradeTable::tools),
            Produce.CODEC.listOf().optionalFieldOf("produce", List.of()).forGetter(TradeTable::produce),
            Supply.CODEC.listOf().optionalFieldOf("supplies", List.of()).forGetter(TradeTable::supplies),
            DefaultProduce.CODEC.optionalFieldOf("default_produce").forGetter(TradeTable::defaultProduce),
            Keep.CODEC.listOf().optionalFieldOf("keeps", List.of()).forGetter(TradeTable::keeps),
            Craft.CODEC.listOf().optionalFieldOf("crafts", List.of()).forGetter(TradeTable::crafts)
    ).apply(i, TradeTable::new));

    private static final int MAX_CRAFT_DEPTH = 4;
    private static volatile Map<ResourceKey<VillagerProfession>, TradeTable> byProfession = Map.of();

    public static Optional<TradeTable> forVillager(Villager villager) {
        return villager.getVillagerData().profession().unwrapKey().map(byProfession::get);
    }

    /** What the cheapest trade sells {@code item} for at its top level, whoever makes it: the price a merchant pays for it at home. */
    public static Optional<Price> anySellPrice(Item item) {
        return byProfession.values().stream().flatMap(table -> table.sellPrice(item, 5).stream()).min(java.util.Comparator.comparingDouble(Price::perItem));
    }

    public static Optional<TradeTable> forProfession(ResourceKey<VillagerProfession> profession) {
        return Optional.ofNullable(byProfession.get(profession));
    }

    public boolean hasProduceEntry(Item item) {
        return produce.stream().anyMatch(crop -> crop.item() == item);
    }

    /** A tool of this trade: anything in {@code tool_tag} (so modded tools count) or listed under {@code tools}. */
    public boolean isToolItem(ItemStack stack) {
        return toolTag.map(stack::is).orElse(false) || isTool(stack.getItem());
    }

    /** How good a tool of this trade is: its place in {@code tools}, which runs from the worst to the best; -1 if it isn't listed (a modded tool in the tag). */
    public int toolRank(Item item) {
        for (int i = 0; i < tools.size(); i++) {
            if (tools.get(i).item() == item) return i;
        }
        return -1;
    }

    public boolean isTool(Item item) {
        return tools.stream().anyMatch(tool -> tool.item() == item);
    }

    public Optional<Supply> supply(Item item) {
        return supplies.stream().filter(supply -> supply.item() == item).findFirst();
    }

    /** What this trade charges for {@code item} at {@code level}: its own entry, else what it crafts it for, else the default price. */
    public Optional<Price> sellPrice(Item item, int level) {
        return sellPrice(item, level, 0);
    }

    private Optional<Price> sellPrice(Item item, int level, int depth) {
        Optional<Produce> entry = produce.stream().filter(crop -> crop.item() == item).findFirst();
        if (entry.isPresent()) return entry.get().level() <= level ? Optional.of(entry.get().price()) : Optional.empty();
        if (craft(item).isPresent()) {
            return craft(item, level).filter(craft -> depth < MAX_CRAFT_DEPTH).map(craft -> craftPrice(craft, depth));
        }
        return defaultProduce.filter(fallback -> fallback.level() <= level && fallback.items().contains(item.builtInRegistryHolder()))
                .map(DefaultProduce::price);
    }

    public Optional<Craft> craft(Item item) {
        return crafts.stream().filter(craft -> craft.item() == item).findFirst();
    }

    /** The recipe for {@code item} at {@code level}: a later level's entry for the same item replaces an earlier one. */
    public Optional<Craft> craft(Item item, int level) {
        return crafts.stream().filter(craft -> craft.item() == item && craft.level() <= level).max(java.util.Comparator.comparingInt(Craft::level));
    }

    /**
     * A craft costs its materials, valued at what this trade pays for them (or sells them for), plus its labor and the
     * wear on its tool, split over the items it makes; sold in the smallest whole-emerald batch ({@link Price#of}). A
     * tool or anything else that doesn't stack is one item for whole emeralds, rounded up.
     */
    public Price craftPrice(Craft craft) {
        return craftPrice(craft, 0);
    }

    private Price craftPrice(Craft craft, int depth) {
        double total = craft.labor() + craft.toolUses() * wearPerUse();
        for (Material material : craft.materials()) {
            double perItem = supply(material.item()).map(supply -> supply.price().perItem())
                    .or(() -> sellPrice(material.item(), 5, depth + 1).map(Price::perItem))
                    .orElse(0.0);
            total += perItem * material.count();
        }
        if (craft.item().getDefaultMaxStackSize() == 1 || CraftResults.isUnique(craft.item())) return new Price(1, Price.ofCraft(total, 0.0));
        return Price.of(total / craft.count());
    }

    /** What one use of its tool costs: the cheapest tool of the trade over the uses it lasts. */
    double wearPerUse() {
        return tools.stream().min(java.util.Comparator.comparingInt(Tool::price))
                .map(tool -> (double) tool.price() / Math.max(1, new ItemStack(tool.item()).getMaxDamage()))
                .orElse(0.0);
    }

    /** How many a crafter keeps in its goods: the craft's stock, but never less than one sale's batch. */
    public int stockTarget(Craft craft) {
        return Math.max(craft.stock(), craftPrice(craft).count());
    }

    public int keepMax(ItemStack stack) {
        return keeps.stream().filter(keep -> stack.is(keep.items())).mapToInt(Keep::max).max().orElse(0);
    }

    /** The crafts unlocked at {@code level}: per item the latest level's recipes, all of them (sticks from any planks). */
    public List<Craft> craftsUpTo(int level) {
        return crafts.stream()
                .filter(craft -> craft(craft.item(), level).filter(best -> best.level() == craft.level()).isPresent())
                .toList();
    }

    /** The supplies the villager would buy or pick up now: unlocked at its level and needed ({@link SupplyNeeds}). */
    public List<Supply> suppliesWanted(net.minecraft.server.level.ServerLevel level, net.minecraft.world.entity.npc.villager.Villager villager) {
        return suppliesUpTo(villager.getVillagerData().level()).stream().filter(supply -> SupplyNeeds.needed(level, villager, supply.item())).toList();
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
