package com.sheepfromheaven.minenow.villager;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sheepfromheaven.minenow.MineNow;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.npc.villager.Villager;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Every villager gets a name from {@code data/<namespace>/villager_names/*.json}; names from all files are merged. */
public final class VillagerNames {
    private record NameList(List<String> names) {
        static final Codec<NameList> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.listOf().fieldOf("names").forGetter(NameList::names)
        ).apply(i, NameList::new));
    }

    private static volatile List<String> names = List.of();

    private VillagerNames() {}

    public static List<String> names() {
        return names;
    }

    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Villager villager)) return;
        nameIfMissing(villager);
    }

    static void nameIfMissing(Villager villager) {
        List<String> available = names;
        if (villager.hasCustomName() || available.isEmpty()) return;
        villager.setCustomName(Component.literal(available.get(Math.floorMod(villager.getUUID().hashCode(), available.size()))));
    }

    public static void onAddReloadListeners(AddServerReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "villager_names"), new Loader());
    }

    private static final class Loader extends SimpleJsonResourceReloadListener<NameList> {
        Loader() {
            super(NameList.CODEC, FileToIdConverter.json("villager_names"));
        }

        @Override
        protected void apply(Map<Identifier, NameList> lists, ResourceManager manager, ProfilerFiller profiler) {
            List<String> merged = new ArrayList<>();
            lists.values().forEach(list -> list.names().stream().filter(name -> !merged.contains(name)).forEach(merged::add));
            names = List.copyOf(merged);
        }
    }
}
