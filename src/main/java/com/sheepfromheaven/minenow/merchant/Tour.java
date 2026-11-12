package com.sheepfromheaven.minenow.merchant;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

import java.util.List;

/**
 * A merchant's tour of nearby villages: the bell it walks to at each stop, the goods it set out to carry (what those
 * villages are short of and its own village sells), and where it is in the day. Saved with the merchant.
 */
public record Tour(List<BlockPos> stops, int next, Phase phase, long since, long started, BlockPos home, List<Want> wanted) {
    /** Buying its goods at home, walking to the next stop's bell, waiting there for buyers, walking back. */
    public enum Phase { SHOP, TRAVEL, MARKET, HOME }

    public record Want(Item item, int amount) {
        static final Codec<Want> CODEC = RecordCodecBuilder.create(i -> i.group(
                BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(Want::item),
                Codec.INT.fieldOf("amount").forGetter(Want::amount)).apply(i, Want::new));
    }

    static final Codec<Tour> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.listOf().fieldOf("stops").forGetter(Tour::stops),
            Codec.INT.fieldOf("next").forGetter(Tour::next),
            Codec.STRING.xmap(Phase::valueOf, Phase::name).fieldOf("phase").forGetter(Tour::phase),
            Codec.LONG.fieldOf("since").forGetter(Tour::since),
            Codec.LONG.fieldOf("started").forGetter(Tour::started),
            BlockPos.CODEC.fieldOf("home").forGetter(Tour::home),
            Want.CODEC.listOf().fieldOf("wanted").forGetter(Tour::wanted)).apply(i, Tour::new));

    Tour in(Phase phase, long now) {
        return new Tour(stops, next, phase, now, started, home, wanted);
    }

    Tour atStop(int index, Phase phase, long now) {
        return new Tour(stops, index, phase, now, started, home, wanted);
    }
}
