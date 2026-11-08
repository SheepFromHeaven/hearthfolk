package com.sheepfromheaven.minenow.villager;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.HopperBlockEntity;

import java.util.Optional;

/** A villager's goods are what it carries plus what is in its home chest. */
public final class Economy {
    public static final int STARTING_PURSE = 6;
    public static final int FOOD_MONEY = 2;

    private Economy() {}

    public static int emeralds(ServerLevel level, Villager villager) {
        return count(level, villager, Items.EMERALD);
    }

    public static int count(ServerLevel level, Villager villager, Item item) {
        int total = villager.getInventory().countItem(item);
        Container chest = homeChest(level, villager);
        if (chest != null) total += chest.countItem(item);
        return total;
    }

    public static java.util.Set<Item> items(ServerLevel level, Villager villager) {
        java.util.Set<Item> items = new java.util.LinkedHashSet<>();
        villager.getInventory().getItems().stream().filter(stack -> !stack.isEmpty()).forEach(stack -> items.add(stack.getItem()));
        Container chest = homeChest(level, villager);
        if (chest != null) {
            for (int i = 0; i < chest.getContainerSize(); i++) {
                if (!chest.getItem(i).isEmpty()) items.add(chest.getItem(i).getItem());
            }
        }
        return items;
    }

    /** Granted once, when the villager first takes a job, so a village's money grows with the villagers working in it. */
    public static void grantStartingPurseOnce(ServerLevel level, Villager villager) {
        if (villager.getExistingData(ModVillagers.PURSE_GRANTED).orElse(false)) return;
        villager.setData(ModVillagers.PURSE_GRANTED, true);
        give(level, villager, new ItemStack(Items.EMERALD, STARTING_PURSE));
    }

    /**
     * What a villager won't spend on what players sell it: its food money, plus the price of the cheapest tool of its
     * trade, so a player selling and never buying can't leave a village unable to eat or work.
     */
    public static int reserve(Villager villager) {
        return FOOD_MONEY + TradeTable.forVillager(villager)
                .flatMap(table -> table.tools().stream().map(TradeTable.Tool::price).min(Integer::compare))
                .orElse(0);
    }

    /** The emeralds a villager may pay a player for supplies. */
    public static int spendable(ServerLevel level, Villager villager) {
        return Math.max(0, emeralds(level, villager) - reserve(villager));
    }

    public static int take(ServerLevel level, Villager villager, Item item, int amount) {
        int taken = takeFrom(villager.getInventory(), item, amount);
        Optional<BlockPos> chestPos = HomeStorage.storageIn(level, villager);
        Container chest = homeChest(level, villager);
        if (taken < amount && chest != null) {
            StorageTheft.check(level, villager, chestPos.get());
            taken += takeFrom(chest, item, amount - taken);
            chest.setChanged();
            StorageTheft.recordContents(level, chestPos.get());
        }
        return taken;
    }

    public static void give(ServerLevel level, Villager villager, ItemStack stack) {
        ItemStack rest = villager.getInventory().addItem(stack);
        Optional<BlockPos> chestPos = HomeStorage.storageIn(level, villager);
        Container chest = homeChest(level, villager);
        if (!rest.isEmpty() && chest != null) {
            StorageTheft.check(level, villager, chestPos.get());
            rest = HopperBlockEntity.addItem(null, chest, rest, null);
            StorageTheft.recordContents(level, chestPos.get());
        }
        if (!rest.isEmpty()) villager.spawnAtLocation(level, rest);
    }

    private static int takeFrom(Container container, Item item, int amount) {
        int taken = 0;
        for (int i = 0; i < container.getContainerSize() && taken < amount; i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.is(item)) continue;
            taken += container.removeItem(i, amount - taken).getCount();
        }
        return taken;
    }

    private static Container homeChest(ServerLevel level, Villager villager) {
        return HomeStorage.storageIn(level, villager).map(pos -> HomeStorage.chestContainer(level, pos)).orElse(null);
    }
}
