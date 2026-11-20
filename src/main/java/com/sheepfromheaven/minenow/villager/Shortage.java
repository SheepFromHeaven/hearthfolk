package com.sheepfromheaven.minenow.villager;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** The supply a working villager can't work without. Only fuel so far: a trade that buys coal (butcher, smelter) cooks nothing without fuel. Trades that merely buy planks or logs for their goods (armorer, fletcher) are not fuel trades. */
final class Shortage {
    private Shortage() {}

    /** The cheapest fuel of its trade, when the trade buys fuel, it isn't waiting on its tool first, is out of other work and holds none. */
    static Optional<TradeTable.Supply> fuel(ServerLevel level, Villager villager) {
        Optional<TradeTable> table = TradeTable.forVillager(villager);
        if (table.isEmpty() || IdleReason.needsToolNow(level, villager) || !IdleReason.needsFuelNow(level, villager)) return Optional.empty();
        if (Economy.items(level, villager).stream().anyMatch(item -> FurnaceTending.isFuel(new ItemStack(item)))) return Optional.empty();
        List<TradeTable.Supply> fuels = fuels(level, table.get(), villager);
        if (fuels.stream().noneMatch(supply -> new ItemStack(supply.item()).is(ItemTags.COALS))) return Optional.empty();
        return fuels.stream().min(Comparator.comparingInt(supply -> supply.price().emeralds()));
    }

    /** Whether any villager of its village would sell it some fuel, for money or on credit. */
    static boolean canBuyFromAVillager(ServerLevel level, Villager villager) {
        return TradeTable.forVillager(villager).stream().flatMap(table -> fuels(level, table, villager).stream())
                .anyMatch(supply -> BuyFromVillagers.canBuySupplyFromAVillager(level, villager, supply));
    }

    /** Whether it has the emeralds for a player's price, with its food money left over, as it does for a first tool. */
    static boolean canPayAPlayer(ServerLevel level, Villager villager) {
        return fuel(level, villager).filter(supply -> supply.price().emeralds() <= Economy.villagerBudget(Economy.emeralds(level, villager) - Economy.FOOD_MONEY)).isPresent();
    }

    private static List<TradeTable.Supply> fuels(ServerLevel level, TradeTable table, Villager villager) {
        return table.suppliesUpTo(villager.getVillagerData().level()).stream()
                .filter(supply -> FurnaceTending.isFuel(new ItemStack(supply.item())) && SupplyNeeds.needed(level, villager, supply.item())).toList();
    }
}
