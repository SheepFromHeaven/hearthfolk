package com.sheepfromheaven.minenow.villager;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sheepfromheaven.minenow.MineNow;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;

import java.util.Map;

/**
 * The emeralds a villager must hold before it buys food for a family, from {@code data/<namespace>/wealth/breeding.json}:
 * {@code base + per_villager * villagers in its village}, rounded up, kept between {@code minimum} and {@code maximum}.
 * A bigger village needs more wealth to grow further. Reloads with {@code /reload}.
 */
public record WealthFormula(double base, double perVillager, int minimum, int maximum) {
    public static final Codec<WealthFormula> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.fieldOf("base").forGetter(WealthFormula::base),
            Codec.DOUBLE.fieldOf("per_villager").forGetter(WealthFormula::perVillager),
            Codec.INT.optionalFieldOf("minimum", 0).forGetter(WealthFormula::minimum),
            Codec.INT.optionalFieldOf("maximum", Integer.MAX_VALUE).forGetter(WealthFormula::maximum)
    ).apply(i, WealthFormula::new));

    static final WealthFormula DEFAULT = new WealthFormula(3, 1, 4, 40);
    private static final Identifier ID = Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "breeding");

    private static volatile WealthFormula current = DEFAULT;

    public static WealthFormula current() {
        return current;
    }

    public int required(int villagers) {
        int needed = (int) Math.ceil(base + perVillager * villagers);
        return Math.clamp(needed, minimum, Math.max(minimum, maximum));
    }

    public static void onAddReloadListeners(AddServerReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "wealth"), new Loader());
    }

    private static final class Loader extends SimpleJsonResourceReloadListener<WealthFormula> {
        Loader() {
            super(CODEC, FileToIdConverter.json("wealth"));
        }

        @Override
        protected void apply(Map<Identifier, WealthFormula> formulas, ResourceManager manager, ProfilerFiller profiler) {
            current = formulas.getOrDefault(ID, DEFAULT);
        }
    }
}
