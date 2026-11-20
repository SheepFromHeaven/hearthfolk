package com.sheepfromheaven.minenow.villager;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.Optional;

/**
 * The vanilla trades of a villager whose profession is switched off, rolled once and kept on it ({@link ModVillagers#VANILLA_TRADES}).
 * {@link ProfessionTrades} clears a villager's offers on every trade while its profession is on, so there are none left to
 * fall back on; rerolling them on every switch-off would let a player toggle a librarian until it offers mending.
 *
 * @param rolledTo the highest level whose trades are in {@code offers}
 * @param shown whether {@code offers} are the villager's live offers, which then hold the truth: uses, and the trades
 *              vanilla added for a level gained since
 */
public record VanillaTrades(Optional<ResourceKey<VillagerProfession>> profession, MerchantOffers offers, int rolledTo, boolean shown) {
    public static final Codec<VanillaTrades> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceKey.codec(Registries.VILLAGER_PROFESSION).optionalFieldOf("profession").forGetter(VanillaTrades::profession),
            MerchantOffers.CODEC.fieldOf("offers").forGetter(VanillaTrades::offers),
            Codec.INT.fieldOf("rolled_to").forGetter(VanillaTrades::rolledTo),
            Codec.BOOL.fieldOf("shown").forGetter(VanillaTrades::shown)
    ).apply(i, VanillaTrades::new));

    public static VanillaTrades none() {
        return new VanillaTrades(Optional.empty(), new MerchantOffers(), 0, false);
    }

    /** Makes the villager's offers its vanilla trades, rolling those of each level it reached since they were last rolled. */
    static void show(ServerLevel level, Villager villager) {
        VanillaTrades saved = villager.getData(ModVillagers.VANILLA_TRADES);
        VillagerData data = villager.getVillagerData();
        Optional<ResourceKey<VillagerProfession>> profession = data.profession().unwrapKey();
        if (!saved.profession().equals(profession)) saved = none();
        MerchantOffers offers = saved.shown() ? villager.getOffers() : saved.offers();
        int rolledTo = saved.shown() ? data.level() : saved.rolledTo();
        villager.setOffers(offers);
        if (rolledTo < data.level()) {
            villager.setVillagerData(data.withLevel(Math.max(1, rolledTo)));
            if (rolledTo == 0) {
                villager.setOffers(null);
                villager.getOffers();
            }
            while (villager.getVillagerData().level() < data.level()) villager.increaseMerchantCareer(level);
        }
        villager.setData(ModVillagers.VANILLA_TRADES, new VanillaTrades(profession, villager.getOffers(), data.level(), true));
    }

    /** Keeps the vanilla trades the villager shows, if it does, and gives it an empty list for Hearthfolk's offers. */
    static void putAway(Villager villager) {
        VanillaTrades saved = villager.getData(ModVillagers.VANILLA_TRADES);
        if (!saved.shown()) return;
        villager.setData(ModVillagers.VANILLA_TRADES,
                new VanillaTrades(saved.profession(), villager.getOffers(), villager.getVillagerData().level(), false));
        villager.setOffers(new MerchantOffers());
    }
}
